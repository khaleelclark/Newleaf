import contextlib
import http.client
import io
import json
import os
from urllib.parse import parse_qs, urlsplit
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from gateway.server import Config, GatewayError, Service, Server, MAX_BODY, upstream

TOKEN = 'fixture-device-token-000000000000000000'
SECRET = 'fixture-server-only-secret'
FOOD = {'fdcId': 123, 'description': 'Beans', 'dataType': 'Foundation',
        'foodNutrients': [{'nutrient': {'id': 1008, 'unitName': 'kcal'}, 'amount': 100}]}

class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.calls = []
        def transport(*args):
            self.calls.append(args)
            return {'foods': [FOOD]}
        self.transport = transport

    def service(self, **options):
        c = Config(device_token=TOKEN, state_file=str(Path(self.temp.name) / f'quota-{len(self.calls)}-{len(options)}.json'), **options)
        s = Service(c, self.transport, clock=lambda: 3600)
        self.addCleanup(s.file_lock.close)
        return s

    def error(self, service, method, target, body=b'', expected='invalid_request', auth=None):
        with self.assertRaises(GatewayError) as caught:
            service.dispatch(method, target, auth or 'Bearer ' + TOKEN, body)
        self.assertEqual(expected, caught.exception.code)

    def test_auth_configuration_and_status(self):
        with self.assertRaises(ValueError): Config('short')
        s = self.service()
        self.error(s, 'GET', '/v1/status', expected='authentication', auth='Bearer wrong')
        result = s.dispatch('GET', '/v1/status', 'Bearer ' + TOKEN)
        self.assertFalse(result['usda']['configured'])
        self.error(s, 'GET', '/v1/usda/search?query=beans', expected='unconfigured')
        self.assertEqual([], self.calls)

    def test_endpoint_and_input_allowlist(self):
        s = self.service(usda_key=SECRET)
        for method, target, body in [
            ('GET', '/v1/usda/search?query=x', b''),
            ('GET', '/v1/usda/search?query=beans&query=milk', b''),
            ('GET', '/v1/usda/search?query=beans&url=https://evil.invalid', b''),
            ('GET', '/v1/usda/search?query=beans%00', b''),
            ('POST', '/v1/usda/search?query=beans', b'{}'),
            ('GET', '/v1/usda/foods/../1', b''),
            ('GET', '/v1/usda/foods/123?api_key=anything', b''),
            ('GET', 'https://evil.invalid/v1/status', b''),
            ('POST', '/v1/describe', b'{"text":"beans","weight":80}'),
            ('POST', '/v1/nutritionix/describe', b'x' * (MAX_BODY + 1)),
            ('GET', '/v1/status', b'private notes')]:
            self.error(s, method, target, body)
        self.assertEqual([], self.calls)

    def test_keys_only_upstream_and_projection(self):
        s = self.service(usda_key=SECRET)
        self.transport = None
        def transport(*args):
            self.calls.append(args)
            return {'foods': [dict(FOOD, debug=SECRET)], 'api_key': SECRET}
        s.transport = transport
        result = s.dispatch('GET', '/v1/usda/search?query=black%20beans', 'Bearer ' + TOKEN)
        self.assertNotIn(SECRET, json.dumps(result))
        self.assertNotIn('api_key=', self.calls[0][1])
        self.assertNotIn(SECRET, self.calls[0][1])
        self.assertEqual(SECRET, self.calls[0][2]['X-Api-Key'])
        self.assertEqual('GET', self.calls[0][0])
        self.assertNotIn(TOKEN, str(self.calls))
        self.assertEqual(20, int(self.calls[0][1].split('pageSize=')[1].split('&')[0]))

    def test_usda_detail_projection(self):
        s = self.service(usda_key=SECRET)
        s.transport = lambda *args: FOOD
        result = s.dispatch('GET', '/v1/usda/foods/123', 'Bearer ' + TOKEN)
        self.assertEqual(123, result['fdcId'])
        self.assertEqual(100, result['foodNutrients'][0]['amount'])

    def describe(self, service, text):
        return service.dispatch('POST', '/v1/describe', 'Bearer ' + TOKEN, json.dumps({'text': text}).encode())

    def description_transport(self, *args):
        self.calls.append(args)
        if '/foods/search?' in args[1]:
            return {'foods': [FOOD]}
        return dict(FOOD, foodPortions=[{'amount': 1, 'gramWeight': 150, 'portionDescription': '1 cup',
                                       'measureUnit': {'name': 'undetermined'}}])

    def test_legacy_paid_environment_cannot_activate_nutritionix(self):
        with patch.dict(os.environ, {'NEWLEAF_DEVICE_TOKEN': TOKEN, 'NUTRITIONIX_APP_ID': 'id',
                                    'NUTRITIONIX_APP_KEY': SECRET, 'NUTRITIONIX_RIGHTS_CONFIRMED': 'true',
                                    'NUTRITIONIX_DAILY_LIMIT': '1000', 'USDA_API_KEY': ''}):
            config = Config.from_env()
        self.assertFalse(hasattr(config, 'nutritionix_key'))
        s = self.service(usda_key=SECRET)
        self.assertNotIn('nutritionix', s.status())
        self.assertTrue(s.status()['describe']['free_only'])
        for path in ('/v1/nutritionix/describe', '/v1/nutritionix/parse', '/v1/parse'):
            self.error(s, 'POST', path, b'{"text":"beans"}', expected='free_only')
        self.assertEqual([], self.calls)

    def test_description_comma_newline_items_details_order_and_privacy(self):
        s = self.service(usda_key=SECRET)
        s.transport = self.description_transport
        text = '  1/2 cup beans, 100g rice\n2 fl oz milk  '
        out = self.describe(s, text)
        self.assertEqual(text, out['original_text'])
        self.assertEqual('usda_assisted', out['mode'])
        self.assertTrue(out['review_required'])
        self.assertEqual(['beans', 'rice', 'milk'], [f['query'] for f in out['foods']])
        self.assertEqual([.5, 100, 2], [f['quantity_hint'] for f in out['foods']])
        self.assertEqual(['cup', 'g', 'US fl oz'], [f['unit_hint'] for f in out['foods']])
        self.assertTrue(all(f['match_estimated'] and f['quantity_estimated'] for f in out['foods']))
        self.assertEqual(150, out['foods'][0]['food']['foodPortions'][0]['gramWeight'])
        self.assertEqual(6, len(self.calls))
        self.assertTrue(all(c[0] == 'GET' and c[1].startswith('https://api.nal.usda.gov/fdc/v1/') for c in self.calls))
        self.assertNotIn(SECRET, json.dumps(out))
        self.assertNotIn(TOKEN, str(self.calls))
        self.assertNotIn('beans', Path(s.config.state_file).read_text())

    def test_description_mixed_unicode_word_fractions_and_unknown_unit(self):
        s = self.service(usda_key=SECRET)
        s.transport = self.description_transport
        out = self.describe(s, '1 1/2 cups beans, ½ cup milk, two tablespoons oil, 2 glugs juice, rice')
        self.assertEqual([1.5, .5, 2, 2, None], [f['quantity_hint'] for f in out['foods']])
        self.assertEqual('glugs', out['foods'][3]['unit_hint'])
        self.assertFalse(out['foods'][3]['unit_recognized'])
        self.assertEqual('glugs juice', out['foods'][3]['query'])
        self.assertIsNone(out['foods'][4]['quantity_hint'])

    def test_description_max_items_and_invalid_quantities_before_network(self):
        s = self.service(usda_key=SECRET)
        s.transport = self.description_transport
        for text in (','.join(['rice'] * 9), '1/0 cup rice', '0 g rice', '-1 g rice',
                     '1000001 g rice', '1 g ' + 'x' * 201, 'rice\x00', 'x' * 2001):
            self.error(s, 'POST', '/v1/describe', json.dumps({'text': text}).encode())
        self.assertEqual([], self.calls)
        out = self.describe(s, ','.join(['rice'] * 8))
        self.assertEqual(8, len(out['foods']))
        self.assertEqual(16, len(self.calls))

    def test_description_partial_no_match_never_drops_item(self):
        s = self.service(usda_key=SECRET)
        def transport(*args):
            query = parse_qs(urlsplit(args[1]).query).get('query', [None])[0]
            if query == 'unmatched':
                self.calls.append(args)
                return {'foods': []}
            return self.description_transport(*args)
        s.transport = transport
        out = self.describe(s, '1 cup beans, unmatched')
        self.assertEqual(2, len(out['foods']))
        self.assertEqual('provisional', out['foods'][0]['match_status'])
        self.assertEqual('no_match', out['foods'][1]['match_status'])
        self.assertIsNone(out['foods'][1]['food'])
        self.assertEqual('unmatched', out['foods'][1]['input_text'])
        self.assertEqual(3, len(self.calls))

    def test_description_detail_identity_and_not_found_fallback(self):
        s = self.service(usda_key=SECRET)
        def transport(*args):
            if '/foods/search?' in args[1]: return {'foods': [FOOD]}
            raise GatewayError('not_found', 404)
        s.transport = transport
        self.assertTrue(self.describe(s, 'beans')['foods'][0]['needs_details'])
        s.transport = lambda *args: {'foods': [FOOD]} if '/foods/search?' in args[1] else dict(FOOD, fdcId=999)
        self.error(s, 'POST', '/v1/describe', b'{"text":"beans"}', expected='malformed_response')

    def test_description_unconfigured_budget_timeout_and_cooldown(self):
        s = self.service()
        self.error(s, 'POST', '/v1/describe', b'{"text":"beans"}', expected='unconfigured')
        s = self.service(usda_key=SECRET, usda_hourly_limit=1)
        def failure(*args):
            self.calls.append(args)
            raise GatewayError('unavailable', 503)
        s.transport = failure
        self.error(s, 'POST', '/v1/describe', b'{"text":"beans"}', expected='unavailable')
        self.error(s, 'POST', '/v1/describe', b'{"text":"beans"}', expected='budget_exceeded')
        self.assertEqual(1, len(self.calls))
        self.assertEqual(set(), s.inflight)

    def test_description_duplicate_and_global_upstream_bounds(self):
        s = self.service(usda_key=SECRET)
        started, release = threading.Event(), threading.Event()
        active, maximum = 0, 0
        guard = threading.Lock()
        def transport(*args):
            nonlocal active, maximum
            with guard:
                active += 1
                maximum = max(active, maximum)
            started.set()
            try:
                release.wait(2)
                return self.description_transport(*args)
            finally:
                with guard: active -= 1
        s.transport = transport
        outputs, errors = [], []
        text = ','.join(['rice'] * 8)
        def run():
            try: outputs.append(self.describe(s, text))
            except Exception as e: errors.append(e)
        thread = threading.Thread(target=run)
        thread.start()
        try:
            self.assertTrue(started.wait(1))
            self.error(s, 'POST', '/v1/describe', json.dumps({'text': text}).encode(), expected='unavailable')
        finally:
            release.set()
            thread.join(3)
        self.assertFalse(errors)
        self.assertEqual(1, len(outputs))
        self.assertLessEqual(maximum, 4)
        self.assertEqual(set(), s.inflight)

    def test_persisted_budget_survives_restart(self):
        s = self.service(usda_key=SECRET, usda_hourly_limit=1)
        s.dispatch('GET', '/v1/usda/search?query=beans', 'Bearer ' + TOKEN)
        s.file_lock.close()
        restarted = Service(s.config, self.transport, clock=lambda: 3600)
        self.addCleanup(restarted.file_lock.close)
        self.error(restarted, 'GET', '/v1/usda/search?query=milk', expected='budget_exceeded')
        self.assertEqual(1, len(self.calls))

    def test_corrupt_quota_fails_closed(self):
        s = self.service(usda_key=SECRET)
        Path(s.config.state_file).write_text('corrupt')
        self.error(s, 'GET', '/v1/usda/search?query=beans', expected='unavailable')
        self.assertEqual([], self.calls)

    def test_provider_retry_after_cools_requests(self):
        s = self.service(usda_key=SECRET)
        def limited(*args): self.calls.append(args); raise GatewayError('rate_limited', 429, 120)
        s.transport = limited
        self.error(s, 'GET', '/v1/usda/search?query=beans', expected='rate_limited')
        self.error(s, 'GET', '/v1/usda/search?query=milk', expected='rate_limited')
        self.assertEqual(1, len(self.calls))

    def test_malformed_provider_and_nonfinite_rejected(self):
        s = self.service(usda_key=SECRET)
        for response in [{}, {'foods': [dict(FOOD, servingSize=float('nan'))]}, {'foods': ['bad']}, {'foods': [dict(FOOD, foodNutrients=None)]}]:
            s.transport = lambda *args: response
            self.error(s, 'GET', '/v1/usda/search?query=beans', expected='malformed_response')

    def test_actual_usda_public_response_projection_preserves_portion_and_nulls(self):
        fixtures = Path(__file__).resolve().parents[2] / 'app/src/test/resources/nutrition'
        search = json.loads((fixtures / 'usda-live-search-2026-10-01.json').read_text())
        detail = json.loads((fixtures / 'usda-live-detail-2026-10-01.json').read_text())
        s = self.service(usda_key=SECRET)
        s.transport = lambda *args: search if '/foods/search?' in args[1] else detail
        candidates = s.dispatch('GET', '/v1/usda/search?query=apples', 'Bearer ' + TOKEN)
        self.assertEqual(1750340, candidates['foods'][0]['fdcId'])
        result = s.dispatch('GET', '/v1/usda/foods/1750340', 'Bearer ' + TOKEN)
        self.assertEqual('Foundation', result['dataType'])
        portion = result['foodPortions'][0]
        self.assertEqual('RACC', portion['measureUnit']['name'])
        self.assertEqual(1, portion['amount'])
        self.assertEqual(140, portion['gramWeight'])
        nutrients = {n['nutrient']['id']: n for n in result['foodNutrients']}
        self.assertEqual(58.20306, nutrients[2048]['amount'])
        self.assertNotIn('amount', nutrients[2045])
        self.assertEqual(0, nutrients[1013]['amount'])
        self.assertNotIn(1258, nutrients)
        self.assertNotIn(SECRET, json.dumps(result))

    def test_device_rate_limit(self):
        s = self.service(device_minute_limit=1)
        s.dispatch('GET', '/v1/status', 'Bearer ' + TOKEN)
        self.error(s, 'GET', '/v1/status', expected='rate_limited')

    def test_transport_errors_redacted_and_not_retried(self):
        opener = unittest.mock.Mock()
        opener.open.side_effect = HTTPError('https://provider.invalid/?api_key=' + SECRET, 401, SECRET, {}, None)
        with patch('gateway.server.build_opener', return_value=opener):
            with self.assertRaises(GatewayError) as caught:
                upstream('POST', 'https://provider.invalid', {}, {'query': 'private meal'}, 1)
        self.assertEqual('access_entitlement', caught.exception.code)
        self.assertNotIn(SECRET, str(caught.exception))
        self.assertEqual(1, opener.open.call_count)

    def test_real_http_no_logging_no_cors_bounds(self):
        s = self.service(usda_key=SECRET)
        server = Server(('127.0.0.1', 0), s)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            captured = io.StringIO()
            with contextlib.redirect_stderr(captured):
                c = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
                c.request('GET', '/v1/usda/search?query=private%20food', headers={'Authorization': 'Bearer ' + TOKEN})
                response = c.getresponse()
                self.assertEqual(200, response.status)
                self.assertEqual('no-store', response.getheader('Cache-Control'))
                self.assertIsNone(response.getheader('Access-Control-Allow-Origin'))
                response.read(); c.close()
                s.transport = self.description_transport
                c = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
                c.request('POST', '/v1/describe', body=json.dumps({'text': '1/2 cup beans, rice'}),
                          headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'})
                response = c.getresponse()
                self.assertEqual(200, response.status)
                meal = json.loads(response.read())
                self.assertTrue(meal['review_required'])
                self.assertEqual(2, len(meal['foods']))
                c.close()
                c = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
                c.request('POST', '/v1/parse', body=b'{"text":"beans"}',
                          headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'})
                response = c.getresponse()
                self.assertEqual(403, response.status)
                self.assertEqual('free_only', json.loads(response.read())['error']['code'])
                c.close()
                c = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
                c.request('POST', '/v1/nutritionix/describe', body=b'x' * (MAX_BODY + 1), headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'})
                response = c.getresponse()
                self.assertEqual(413, response.status)
                self.assertNotIn(TOKEN, response.read().decode())
                c.close()
            self.assertEqual('', captured.getvalue())
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=2)

if __name__ == '__main__': unittest.main()
