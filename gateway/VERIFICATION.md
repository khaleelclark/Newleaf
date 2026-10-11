# Optional gateway verification

Documentation reviewed October 11, 2026. Gateway source is included with Newleaf 0.1.4 but is optional and has no deployed production endpoint. [Release validation](../releases/VALIDATION.md) separates recorded Android results from historical gateway evidence.

## Recorded test evidence

On October 1, 2026, `python3 -m unittest discover -s gateway/tests -v` passed 20 Python tests. The 0.1.4 app-only release did not rerun that suite. Documentation maintenance does not count that historical result as a fresh execution.

The fixtures cover authenticated status/search/details/description routes, disabled commercial routes with zero upstream calls, header-only USDA key transport, public-response projection, bounded item/count/order matching, fractions and unknown units, detail/no-match handling, input prevalidation, quotas/cooldown/concurrency/duplicate suppression, corrupt persisted counters and safe errors. They use fake credentials; public USDA response resources contain catalog data only.

## Live evidence boundaries

Historical authorized host USDA search/detail reads returned HTTP200; OFF public-product reads/replays exercised source mapping, including the 0.1.4 soda/package shape. See the app release record and [USDA fixture provenance](../app/src/test/resources/nutrition/USDA_FIXTURES.md). Offline replays are not additional live calls, and a host read does not validate Android phone networking or unrestricted food coverage.

## Deployment acceptance

Before relying on a hosted gateway, configure the private key/token and persistent quota file in [setup](README.md), then verify authenticated HTTPS reachability, certificate trust, proxy total deadlines, input/header/body bounds, safe logging, unauthorized denial, quota persistence across restart and device cancellation. Socket timeouts alone do not impose a total request deadline. No production TLS/proxy/device provisioning or cloud deployment has been performed.

Open Food Facts is called directly by the app, not proxied here. The default personal User-Agent identifies Newleaf; an owner-chosen contact override remains a broader-distribution task. Nutritionix stays disabled even with legacy credentials. Source mapping/export obligations are documented in [provider notes](PROVIDERS_AND_RIGHTS.md) and the [nutrition API](../app/src/main/java/com/khaleel/newleaf/nutrition/API.md).
