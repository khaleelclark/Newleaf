"""Newleaf bounded nutrition proxy. Python 3.11+, standard library only."""
import datetime as dt
import hashlib
import hmac
import json
import math
from email.utils import parsedate_to_datetime
import os
from pathlib import Path
import re
import socket
import tempfile
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

MAX_BODY = 8192
MAX_UPSTREAM = 2 * 1024 * 1024

class GatewayError(Exception):
    def __init__(self, code, status=400, retry=None):
        self.code, self.status, self.retry = code, status, retry
        super().__init__(code)

MESSAGES = {
    'authentication': 'Device authentication failed.',
    'unconfigured': 'This nutrition source is not configured.',
    'access_entitlement': 'This nutrition source is not enabled for this account.',
    'invalid_request': 'Check the request fields and bounds.',
    'not_found': 'No matching food was found.',
    'rate_limited': 'Nutrition source limit reached. Try later.',
    'budget_exceeded': 'Configured nutrition request budget reached.',
    'unavailable': 'Nutrition source is temporarily unavailable.',
    'malformed_response': 'Nutrition source returned an unsupported response.',
}

@dataclass(repr=False)
class Config:
    device_token: str
    usda_key: str = ''
    nutritionix_id: str = ''
    nutritionix_key: str = ''
    nutritionix_rights_confirmed: bool = False
    usda_hourly_limit: int = 100
    nutritionix_daily_limit: int = 0
    device_minute_limit: int = 30
    state_file: str = '.newleaf-quota.json'
    timeout: float = 10

    def __post_init__(self):
        if len(self.device_token) < 32 or any(not 33 <= ord(c) <= 126 for c in self.device_token):
            raise ValueError('A non-whitespace device token of at least 32 characters is required')
        if not 1 <= self.timeout <= 30 or min(self.usda_hourly_limit, self.nutritionix_daily_limit) < 0 or not 1 <= self.device_minute_limit <= 100:
            raise ValueError('Invalid request bounds')

    @classmethod
    def from_env(cls):
        return cls(device_token=os.environ.get('NEWLEAF_DEVICE_TOKEN', ''),
                   usda_key=os.environ.get('USDA_API_KEY', ''),
                   nutritionix_id=os.environ.get('NUTRITIONIX_APP_ID', ''),
                   nutritionix_key=os.environ.get('NUTRITIONIX_APP_KEY', ''),
                   nutritionix_rights_confirmed=os.environ.get('NUTRITIONIX_RIGHTS_CONFIRMED') == 'true',
                   usda_hourly_limit=int(os.environ.get('USDA_HOURLY_LIMIT', '100')),
                   nutritionix_daily_limit=int(os.environ.get('NUTRITIONIX_DAILY_LIMIT', '0')),
                   state_file=os.environ.get('NEWLEAF_QUOTA_FILE', '.newleaf-quota.json'))

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def upstream(method, url, headers, body, timeout):
    """Exactly one attempt, no redirects, bounded response; never expose exception URLs."""
    try:
        req = Request(url, data=json.dumps(body).encode() if body is not None else None,
                      headers={'Accept': 'application/json', 'Content-Type': 'application/json',
                               'User-Agent': 'NewleafGateway/1.0', **headers}, method=method)
        with build_opener(NoRedirect()).open(req, timeout=timeout) as response:
            data = response.read(MAX_UPSTREAM + 1)
            if len(data) > MAX_UPSTREAM:
                raise GatewayError('malformed_response', 502)
            result = json.loads(data, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
            if not isinstance(result, dict):
                raise GatewayError('malformed_response', 502)
            return result
    except HTTPError as error:
        if error.code == 429:
            raw = error.headers.get('Retry-After', '')
            if raw.isdigit():
                retry = min(int(raw), 86400)
            else:
                try:
                    retry = min(max(1, int(parsedate_to_datetime(raw).timestamp() - time.time())), 86400)
                except (TypeError, ValueError, OverflowError):
                    retry = 60
            raise GatewayError('rate_limited', 429, max(1, retry)) from None
        if error.code in (401, 403):
            raise GatewayError('access_entitlement', 403) from None
        if error.code == 404:
            raise GatewayError('not_found', 404) from None
        raise GatewayError('unavailable', 503) from None
    except (URLError, TimeoutError, OSError):
        raise GatewayError('unavailable', 503) from None
    except (ValueError, UnicodeError):
        raise GatewayError('malformed_response', 502) from None

# Project only fields used by adapters. Drop raw diagnostics, user echoes, images and secrets.
USDA_FIELDS = {'fdcId', 'description', 'brandOwner', 'brandName', 'dataType', 'servingSize',
               'servingSizeUnit', 'householdServingFullText', 'foodCategory', 'marketCountry'}
NX_FIELDS = {'food_name', 'brand_name', 'serving_qty', 'serving_unit', 'serving_weight_grams',
             'nf_calories', 'nf_total_fat', 'nf_saturated_fat', 'nf_cholesterol', 'nf_sodium',
             'nf_total_carbohydrate', 'nf_dietary_fiber', 'nf_sugars', 'nf_protein',
             'nf_potassium', 'nf_p', 'nix_item_id', 'tag_id', 'ndb_no'}

def primitives(item, fields):
    if not isinstance(item, dict) or any(isinstance(v, float) and not math.isfinite(v) for v in item.values()):
        raise GatewayError('malformed_response', 502)
    return {k: v for k, v in item.items() if k in fields and (v is None or isinstance(v, (str, int, float, bool)))}

def usda_food(item):
    if not isinstance(item, dict) or not isinstance(item.get('fdcId'), int) or not isinstance(item.get('description'), str):
        raise GatewayError('malformed_response', 502)
    out = primitives(item, USDA_FIELDS)
    out['foodNutrients'] = []
    for nutrient in item.get('foodNutrients', []):
        n = primitives(nutrient, {'nutrientId', 'nutrientNumber', 'nutrientName', 'unitName', 'value', 'amount', 'derivationCode', 'derivationDescription'})
        if isinstance(nutrient.get('foodNutrientDerivation'), dict):
            n['foodNutrientDerivation'] = primitives(nutrient['foodNutrientDerivation'], {'code', 'description'})
        if isinstance(nutrient.get('nutrient'), dict):
            n['nutrient'] = primitives(nutrient['nutrient'], {'id', 'number', 'name', 'unitName'})
        out['foodNutrients'].append(n)
    out['foodPortions'] = []
    for portion in item.get('foodPortions', []):
        p = primitives(portion, {'amount', 'gramWeight', 'modifier', 'portionDescription'})
        if isinstance(portion.get('measureUnit'), dict):
            p['measureUnit'] = primitives(portion['measureUnit'], {'name', 'abbreviation'})
        out['foodPortions'].append(p)
    return out

def nx_food(item):
    if not isinstance(item, dict) or not isinstance(item.get('food_name'), str):
        raise GatewayError('malformed_response', 502)
    out = primitives(item, NX_FIELDS)
    if isinstance(item.get('tags'), dict):
        out['tags'] = primitives(item['tags'], {'tag_id'})
    out['full_nutrients'] = [primitives(n, {'attr_id', 'value'}) for n in item.get('full_nutrients', [])]
    out['alt_measures'] = [primitives(n, {'measure', 'qty', 'serving_weight'}) for n in item.get('alt_measures', [])]
    return out

class Service:
    def __init__(self, config, transport=upstream, clock=time.time):
        self.config, self.transport, self.clock = config, transport, clock
        self.lock = threading.Lock()
        self.inflight = set()
        self.blocked_until = {}
        self.minute, self.minute_count = -1, 0
        self.semaphore = threading.BoundedSemaphore(4)
        # Exclusive lifetime lock prevents multiple processes sharing a quota file.
        import fcntl
        state = Path(config.state_file)
        state.parent.mkdir(parents=True, exist_ok=True)
        self.file_lock = open(str(state) + '.lock', 'a')
        os.chmod(str(state) + '.lock', 0o600)
        fcntl.flock(self.file_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def status(self):
        c = self.config
        nx_configured = bool(c.nutritionix_id and c.nutritionix_key)
        nx_enabled = nx_configured and c.nutritionix_rights_confirmed and c.nutritionix_daily_limit > 0
        return {'usda': {'configured': bool(c.usda_key), 'enabled': bool(c.usda_key) and c.usda_hourly_limit > 0, 'reason': 'unconfigured' if not c.usda_key else 'budget_exceeded' if c.usda_hourly_limit == 0 else None},
                'nutritionix': {'configured': nx_configured, 'enabled': nx_enabled,
                               'reason': None if nx_enabled else 'unconfigured' if not nx_configured else 'access_entitlement'}}

    def auth(self, authorization):
        if not hmac.compare_digest(authorization, 'Bearer ' + self.config.device_token):
            raise GatewayError('authentication', 401)
        with self.lock:
            minute = int(self.clock() // 60)
            if minute != self.minute:
                self.minute, self.minute_count = minute, 0
            if self.minute_count >= self.config.device_minute_limit:
                raise GatewayError('rate_limited', 429, 60)
            self.minute_count += 1

    def reserve(self, source):
        with self.lock:
            now = self.clock()
            if self.blocked_until.get(source, 0) > now:
                raise GatewayError('rate_limited', 429, int(self.blocked_until[source] - now) + 1)
            period = 3600 if source == 'usda' else 86400
            window = int(now // period)
            limit = self.config.usda_hourly_limit if source == 'usda' else self.config.nutritionix_daily_limit
            path = Path(self.config.state_file)
            try:
                state = json.loads(path.read_text()) if path.exists() else {}
                count = state.get(source, {}).get('count', 0) if state.get(source, {}).get('window') == window else 0
                if count >= limit:
                    raise GatewayError('budget_exceeded', 429, max(1, int((window + 1) * period - now)))
                state[source] = {'window': window, 'count': count + 1}
                fd, temporary = tempfile.mkstemp(dir=path.parent, prefix='.quota-')
                try:
                    with os.fdopen(fd, 'w') as file:
                        json.dump(state, file)
                        file.flush()
                        os.fsync(file.fileno())
                    os.replace(temporary, path)
                finally:
                    if os.path.exists(temporary):
                        os.unlink(temporary)
            except (OSError, ValueError, TypeError, AttributeError):
                raise GatewayError('unavailable', 503) from None

    def call(self, source, method, url, headers=None, body=None):
        self.reserve(source)  # never refund possibly billed/unknown outcomes
        try:
            return self.transport(method, url, headers or {}, body, self.config.timeout)
        except GatewayError as error:
            if error.code == 'rate_limited':
                with self.lock:
                    self.blocked_until[source] = self.clock() + (error.retry or 60)
            raise

    def dispatch(self, method, target, authorization, body=b''):
        self.auth(authorization)
        parsed = urlsplit(target)
        try:
            query = parse_qs(parsed.query, keep_blank_values=True, strict_parsing=True, max_num_fields=4)
        except ValueError:
            raise GatewayError('invalid_request') from None
        if len(body) > MAX_BODY or parsed.fragment or parsed.scheme or parsed.netloc:
            raise GatewayError('invalid_request')
        if method == 'GET' and body:
            raise GatewayError('invalid_request')
        if parsed.path == '/v1/status' and method == 'GET' and not query:
            return self.status()
        if not self.semaphore.acquire(blocking=False):
            raise GatewayError('unavailable', 503)
        try:
            if parsed.path == '/v1/usda/search' and method == 'GET' and set(query) == {'query'} and len(query['query']) == 1:
                text = query['query'][0].strip()
                if not 2 <= len(text) <= 200 or any(ord(c) < 32 for c in text):
                    raise GatewayError('invalid_request')
                if not self.config.usda_key:
                    raise GatewayError('unconfigured', 503)
                url = 'https://api.nal.usda.gov/fdc/v1/foods/search?' + urlencode({'query': text, 'pageSize': 20, 'api_key': self.config.usda_key})
                result = self.call('usda', 'GET', url)
                if not isinstance(result.get('foods'), list):
                    raise GatewayError('malformed_response', 502)
                return {'foods': [usda_food(f) for f in result['foods'][:20]]}
            match = re.fullmatch(r'/v1/usda/foods/([1-9][0-9]{0,9})', parsed.path)
            if match and method == 'GET' and not query:
                if not self.config.usda_key:
                    raise GatewayError('unconfigured', 503)
                url = 'https://api.nal.usda.gov/fdc/v1/food/' + match[1] + '?' + urlencode({'api_key': self.config.usda_key})
                return usda_food(self.call('usda', 'GET', url))
            if parsed.path == '/v1/nutritionix/describe' and method == 'POST' and not query:
                try:
                    data = json.loads(body)
                except (ValueError, UnicodeError):
                    raise GatewayError('invalid_request') from None
                if not isinstance(data, dict) or set(data) != {'text'} or not isinstance(data['text'], str):
                    raise GatewayError('invalid_request')
                text = data['text'].strip()
                if not 2 <= len(text) <= 2000 or any(ord(c) < 32 and c not in '\n\t' for c in text):
                    raise GatewayError('invalid_request')
                status = self.status()['nutritionix']
                if not status['enabled']:
                    raise GatewayError(status['reason'], 503 if not status['configured'] else 403)
                digest = hashlib.sha256(text.encode()).digest()
                with self.lock:
                    if digest in self.inflight:
                        raise GatewayError('unavailable', 409)
                    self.inflight.add(digest)
                try:
                    result = self.call('nutritionix', 'POST', 'https://trackapi.nutritionix.com/v2/natural/nutrients',
                                       {'x-app-id': self.config.nutritionix_id, 'x-app-key': self.config.nutritionix_key},
                                       {'query': text, 'num_servings': 1})
                    if not isinstance(result.get('foods'), list) or len(result['foods']) > 100:
                        raise GatewayError('malformed_response', 502)
                    return {'foods': [nx_food(f) for f in result['foods']]}
                finally:
                    with self.lock:
                        self.inflight.discard(digest)
            raise GatewayError('invalid_request', 404)
        except (TypeError, ValueError, AttributeError, KeyError):
            raise GatewayError('malformed_response', 502) from None
        finally:
            self.semaphore.release()

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'
    def log_message(self, *args):
        pass  # do not log paths, payloads, tokens or upstream exceptions

    def handle_one_request(self):
        self.connection.settimeout(15)
        try:
            super().handle_one_request()
        except (socket.timeout, OSError):
            self.close_connection = True

    def process(self):
        try:
            if self.headers.get('Transfer-Encoding') or len(self.headers.get_all('Content-Length', [])) > 1:
                raise GatewayError('invalid_request')
            raw_length = self.headers.get('Content-Length', '0')
            if not raw_length.isdigit() or int(raw_length) > MAX_BODY:
                raise GatewayError('invalid_request', 413)
            length = int(raw_length)
            if self.command == 'POST' and self.headers.get('Content-Type', '').split(';')[0].strip().lower() != 'application/json':
                raise GatewayError('invalid_request', 415)
            authorization = self.headers.get('Authorization', '')
            if len(self.headers.get_all('Authorization', [])) != 1:
                raise GatewayError('authentication', 401)
            # Authenticate before reading body; service authenticates once for budgeting.
            if not hmac.compare_digest(authorization, 'Bearer ' + self.server.service.config.device_token):
                raise GatewayError('authentication', 401)
            body = self.rfile.read(length)
            if len(body) != length:
                raise GatewayError('invalid_request')
            output, status, retry = self.server.service.dispatch(self.command, self.path, authorization, body), 200, None
        except GatewayError as error:
            status, retry = error.status, error.retry
            output = {'error': {'code': error.code, 'message': MESSAGES[error.code], 'retry_after_seconds': retry}}
        except Exception:
            status, retry = 503, None
            output = {'error': {'code': 'unavailable', 'message': MESSAGES['unavailable'], 'retry_after_seconds': None}}
        encoded = json.dumps(output, allow_nan=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(encoded)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        if retry:
            self.send_header('Retry-After', str(retry))
        self.end_headers()
        self.wfile.write(encoded)

    do_GET = do_POST = do_PUT = do_DELETE = do_OPTIONS = process

class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 8
    def __init__(self, address, service):
        self.service = service
        self.slots = threading.BoundedSemaphore(8)
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

if __name__ == '__main__':
    try:
        service = Service(Config.from_env())
        server = Server(('127.0.0.1', int(os.environ.get('NEWLEAF_PORT', '8787'))), service)
    except Exception:
        raise SystemExit('Gateway configuration/startup failed. Check local configuration and quota file.') from None
    print('Newleaf nutrition gateway listening on loopback; HTTPS reverse proxy required for phone access.', flush=True)
    server.serve_forever()
