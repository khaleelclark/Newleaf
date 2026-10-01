import contextlib
import http.client
import io
import json
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
            ('POST', '/v1/nutritionix/describe', b'{"text":"beans","weight":80}'),
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
        self.assertIn('api_key=' + SECRET, self.calls[0][1])
        self.assertEqual('GET', self.calls[0][0])
        self.assertNotIn(TOKEN, str(self.calls))
        self.assertEqual(20, int(self.calls[0][1].split('pageSize=')[1].split('&')[0]))

    def test_usda_detail_projection(self):
        s = self.service(usda_key=SECRET)
        s.transport = lambda *args: FOOD
        result = s.dispatch('GET', '/v1/usda/foods/123', 'Bearer ' + TOKEN)
        self.assertEqual(123, result['fdcId'])
        self.assertEqual(100, result['foodNutrients'][0]['amount'])

    def test_nutritionix_rights_and_budget_gate(self):
        s = self.service(nutritionix_id='id', nutritionix_key=SECRET, nutritionix_daily_limit=3)
        self.error(s, 'POST', '/v1/nutritionix/describe', b'{"text":"beans"}', expected='access_entitlement')
        self.assertEqual([], self.calls)
        s.config.nutritionix_rights_confirmed = True
        s.config.nutritionix_daily_limit = 1
        s.transport = lambda *args: self.calls.append(args) or {'foods': [{'food_name': 'milk', 'serving_qty': 2, 'serving_unit': 'cup', 'nf_calories': 240, 'secret': SECRET}]}
        out = s.dispatch('POST', '/v1/nutritionix/describe', 'Bearer ' + TOKEN, b'{"text":"two cups milk"}')
        self.assertEqual(240, out['foods'][0]['nf_calories'])
        self.assertNotIn(SECRET, json.dumps(out))
        self.assertEqual('POST', self.calls[0][0])
        self.assertEqual({'query': 'two cups milk', 'num_servings': 1}, self.calls[0][3])
        self.error(s, 'POST', '/v1/nutritionix/describe', b'{"text":"beans"}', expected='budget_exceeded')
        self.assertEqual(1, len(self.calls))
        self.assertNotIn('milk', Path(s.config.state_file).read_text())

    def test_timeout_not_retried_or_budget_refunded(self):
        s = self.service(nutritionix_id='id', nutritionix_key=SECRET, nutritionix_daily_limit=1, nutritionix_rights_confirmed=True)
        def failure(*args):
            self.calls.append(args)
            raise GatewayError('unavailable', 503)
        s.transport = failure
        self.error(s, 'POST', '/v1/nutritionix/describe', b'{"text":"beans"}', expected='unavailable')
        self.error(s, 'POST', '/v1/nutritionix/describe', b'{"text":"beans"}', expected='budget_exceeded')
        self.assertEqual(1, len(self.calls))

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
