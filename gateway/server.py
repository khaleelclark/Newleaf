"""Newleaf bounded nutrition proxy. Python 3.11+, standard library only."""
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
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
from fractions import Fraction
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
    'free_only': 'Newleaf uses free sources only. Nutritionix is disabled.',
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
    usda_hourly_limit: int = 100
    device_minute_limit: int = 30
    state_file: str = '.newleaf-quota.json'
    timeout: float = 10

    def __post_init__(self):
        if len(self.device_token) < 32 or any(not 33 <= ord(c) <= 126 for c in self.device_token):
            raise ValueError('A non-whitespace device token of at least 32 characters is required')
        if not 1 <= self.timeout <= 30 or self.usda_hourly_limit < 0 or not 1 <= self.device_minute_limit <= 100:
            raise ValueError('Invalid request bounds')

    @classmethod
    def from_env(cls):
        return cls(device_token=os.environ.get('NEWLEAF_DEVICE_TOKEN', ''),
                   usda_key=os.environ.get('USDA_API_KEY', ''),
                   usda_hourly_limit=int(os.environ.get('USDA_HOURLY_LIMIT', '100')),
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

# Small syntax parser, not a language model. Never invent a serving mass/density.
UNIT_ALIASES = {
    'g': 'g', 'gram': 'g', 'grams': 'g', 'kg': 'kg', 'kilogram': 'kg', 'kilograms': 'kg',
    'mg': 'mg', 'oz': 'oz', 'ounce': 'oz', 'ounces': 'oz', 'lb': 'lb', 'pound': 'lb', 'pounds': 'lb',
    'ml': 'mL', 'milliliter': 'mL', 'milliliters': 'mL', 'l': 'L', 'liter': 'L', 'liters': 'L',
    'fl oz': 'US fl oz', 'fluid ounce': 'US fl oz', 'fluid ounces': 'US fl oz',
    'cup': 'cup', 'cups': 'cup', 'tbsp': 'tablespoon', 'tablespoon': 'tablespoon', 'tablespoons': 'tablespoon',
    'tsp': 'teaspoon', 'teaspoon': 'teaspoon', 'teaspoons': 'teaspoon',
    'serving': 'serving', 'servings': 'serving', 'slice': 'slice', 'slices': 'slice',
    'piece': 'piece', 'pieces': 'piece', 'item': 'item', 'items': 'item',
}
QUANTITY = re.compile(r'^(?P<q>(?:[0-9]+ +[0-9]+/[0-9]+|[0-9]+/[0-9]+|[0-9]+(?:\.[0-9]+)?|\.[0-9]+))\s*(?P<rest>.*)$')
WORDS = {'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5, 'six': 6, 'seven': 7, 'eight': 8, 'half': .5}

def description_items(text):
    if not isinstance(text, str) or not 2 <= len(text.strip()) <= 2000 or len(text) > 2000 or any(ord(c) < 32 and c not in '\n\t' for c in text):
        raise GatewayError('invalid_request')
    parts = [p.strip() for p in re.split(r'[,\n]', text) if p.strip()]
    if not 1 <= len(parts) <= 8:
        raise GatewayError('invalid_request')
    result = []
    for part in parts:
        normalized = re.sub(r'\s+', ' ', part)
        for symbol, fraction in {'½': '1/2', '¼': '1/4', '¾': '3/4', '⅓': '1/3', '⅔': '2/3'}.items():
            normalized = normalized.replace(symbol, ' ' + fraction).strip()
        quantity, rest = None, normalized
        match = QUANTITY.fullmatch(normalized)
        if match:
            try:
                quantity = float(sum((Fraction(n) for n in match['q'].split()), Fraction(0)))
            except (ValueError, ZeroDivisionError):
                raise GatewayError('invalid_request') from None
            rest = match['rest'].strip()
        elif normalized.split(' ', 1)[0].lower() in WORDS:
            word, _, rest = normalized.partition(' ')
            quantity = float(WORDS[word.lower()])
        elif normalized.startswith(('-', '+')):
            raise GatewayError('invalid_request')
        if quantity is not None and not 0 < quantity <= 1000000:
            raise GatewayError('invalid_request')
        unit, recognized, query = 'serving' if quantity is None else 'item', quantity is None, rest
        if quantity is not None:
            for alias in sorted(UNIT_ALIASES, key=len, reverse=True):
                if rest.lower().startswith(alias + ' '):
                    unit, recognized, query = UNIT_ALIASES[alias], True, rest[len(alias):].strip()
                    break
            else:
                # An unsupported unit is retained. No gram/volume conversion is guessed.
                tokens = rest.split(' ', 1)
                if len(tokens) == 2:
                    unit = tokens[0]
                    query = rest  # ambiguous noun/adjective: preserve every search word
        if not 2 <= len(query) <= 200 or any(ord(c) < 32 for c in query):
            raise GatewayError('invalid_request')
        result.append({'input_text': part, 'query': query, 'quantity_hint': quantity, 'unit_hint': unit,
                       'quantity_estimated': True, 'unit_recognized': recognized, 'match_estimated': True})
    return result

class Service:
    def __init__(self, config, transport=upstream, clock=time.time):
        self.config, self.transport, self.clock = config, transport, clock
        self.lock = threading.Lock()
        self.inflight = set()
        self.blocked_until = {}
        self.minute, self.minute_count = -1, 0
        self.semaphore = threading.BoundedSemaphore(4)
        self.upstream_slots = threading.BoundedSemaphore(4)
        # Exclusive lifetime lock prevents multiple processes sharing a quota file.
        import fcntl
        state = Path(config.state_file)
        state.parent.mkdir(parents=True, exist_ok=True)
        self.file_lock = open(str(state) + '.lock', 'a')
        os.chmod(str(state) + '.lock', 0o600)
        fcntl.flock(self.file_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def status(self):
        c = self.config
        usda = {'configured': bool(c.usda_key), 'enabled': bool(c.usda_key) and c.usda_hourly_limit > 0,
                'reason': 'unconfigured' if not c.usda_key else 'budget_exceeded' if c.usda_hourly_limit == 0 else None}
        return {'usda': usda, 'describe': {**usda, 'mode': 'usda_assisted', 'free_only': True}}

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
            if source != 'usda':
                raise GatewayError('free_only', 403)
            period = 3600
            window = int(now // period)
            limit = self.config.usda_hourly_limit
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
        # Independent items run concurrently, but all routes share four upstream slots.
        if source != 'usda' or method != 'GET' or not url.startswith('https://api.nal.usda.gov/fdc/v1/'):
            raise GatewayError('free_only', 403)
        if not self.upstream_slots.acquire(timeout=self.config.timeout):
            raise GatewayError('unavailable', 503)
        try:
            self.reserve(source)  # never refund unknown outcomes
            try:
                return self.transport(method, url, headers or {}, body, self.config.timeout)
            except GatewayError as error:
                if error.code == 'rate_limited':
                    with self.lock:
                        self.blocked_until[source] = self.clock() + (error.retry or 60)
                raise
        finally:
            self.upstream_slots.release()

    def describe_item(self, item):
        url = 'https://api.nal.usda.gov/fdc/v1/foods/search?' + urlencode({
            'query': item['query'], 'pageSize': 5,
            'dataType': 'Survey (FNDDS)'})
        result = self.call('usda', 'GET', url, {'X-Api-Key': self.config.usda_key})
        foods = result.get('foods')
        if not isinstance(foods, list):
            raise GatewayError('malformed_response', 502)
        if not foods:
            return {**item, 'food': None, 'match_status': 'no_match', 'needs_details': False}
        food = usda_food(foods[0])
        if not re.fullmatch(r'[1-9][0-9]{0,9}', str(food['fdcId'])):
            raise GatewayError('malformed_response', 502)
        url = 'https://api.nal.usda.gov/fdc/v1/food/' + str(food['fdcId'])
        try:
            detail = usda_food(self.call('usda', 'GET', url, {'X-Api-Key': self.config.usda_key}))
        except GatewayError as error:
            if error.code != 'not_found':
                raise
            return {**item, 'food': food, 'match_status': 'provisional', 'needs_details': True}
        if detail['fdcId'] != food['fdcId']:
            raise GatewayError('malformed_response', 502)
        return {**item, 'food': detail, 'match_status': 'provisional', 'needs_details': False}

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
                url = 'https://api.nal.usda.gov/fdc/v1/foods/search?' + urlencode({'query': text, 'pageSize': 20})
                result = self.call('usda', 'GET', url, {'X-Api-Key': self.config.usda_key})
                if not isinstance(result.get('foods'), list):
                    raise GatewayError('malformed_response', 502)
                return {'foods': [usda_food(f) for f in result['foods'][:20]]}
            match = re.fullmatch(r'/v1/usda/foods/([1-9][0-9]{0,9})', parsed.path)
            if match and method == 'GET' and not query:
                if not self.config.usda_key:
                    raise GatewayError('unconfigured', 503)
                url = 'https://api.nal.usda.gov/fdc/v1/food/' + match[1]
                return usda_food(self.call('usda', 'GET', url, {'X-Api-Key': self.config.usda_key}))
            if parsed.path in ('/v1/parse', '/v1/nutritionix/describe', '/v1/nutritionix/parse'):
                raise GatewayError('free_only', 403)
            if parsed.path == '/v1/describe' and method == 'POST' and not query:
                try:
                    data = json.loads(body)
                except (ValueError, UnicodeError):
                    raise GatewayError('invalid_request') from None
                if not isinstance(data, dict) or set(data) != {'text'}:
                    raise GatewayError('invalid_request')
                items = description_items(data['text'])  # validate every item before any call
                if not self.config.usda_key:
                    raise GatewayError('unconfigured', 503)
                digest = hashlib.sha256(data['text'].strip().encode()).digest()
                with self.lock:
                    if digest in self.inflight:
                        raise GatewayError('unavailable', 409)
                    self.inflight.add(digest)
                try:
                    with ThreadPoolExecutor(max_workers=min(4, len(items))) as pool:
                        foods = list(pool.map(self.describe_item, items))
                    return {'mode': 'usda_assisted', 'original_text': data['text'], 'review_required': True, 'foods': foods}
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
