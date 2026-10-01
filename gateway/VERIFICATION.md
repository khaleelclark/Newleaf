# Nutrition integration verification — October 1, 2026

Implementation is present in `/home/phoenix/khaleel/Newleaf`; no build, UI or data-layer files were edited by this integration work. Integration API was published before implementation and communicated to the manager, UI engineer and data/export engineer; subsequent API details were aligned with the actual AppSettings, Entry, SourceReference, NutrientEvidence and PortionDefinition models.

Required files:

- `app/src/main/java/com/khaleel/newleaf/nutrition/API.md`: public contract, errors, source statuses, gateway routes, known restrictions.
- `app/src/main/java/com/khaleel/newleaf/nutrition/NutritionClient.kt`: USDA search/details, direct Open Food Facts barcode, gateway Nutritionix describe, status, input bounds, HTTPS and duplicate-action checks.
- `app/src/main/java/com/khaleel/newleaf/nutrition/NutritionHttp.kt`: bounded HttpURLConnection transport, coroutine cancellation/disconnect, safe errors, redirect refusal, Retry-After parsing. No explicit automatic retries.
- `app/src/main/java/com/khaleel/newleaf/nutrition/ProviderMappers.kt`: independent source mappings, explicit nutrient bases, original values, unknown units, portion definitions, suggested drink kinds, provider identity and region.
- `app/src/main/java/com/khaleel/newleaf/nutrition/FoodCandidate.kt`: validated basis, consumed-entry scaling, no arbitrary water density, missingness/evidence/attribution.
- `app/src/test/java/com/khaleel/newleaf/nutrition/NutritionClientTest.kt`: 26 adapter/request/transport/export tests (one added for the licensing correction).
- `gateway/server.py`, `gateway/.env.example`, `gateway/.gitignore`: executable standard-library gateway and secret-free configuration template.
- `gateway/tests/test_gateway.py`: 13 gateway/service/real-HTTP tests.
- `gateway/README.md`, `gateway/PROVIDERS_AND_RIGHTS.md`, `gateway/VERIFICATION.md`: run/security/provider/rights notes and actual verification evidence.

Run from the Newleaf root:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.khaleel.newleaf.nutrition.*' --console=plain
python3 -m unittest discover -s gateway/tests -v
```

Results: Gradle BUILD SUCCESSFUL; adapter JUnit XML reports 25 tests, zero failures/errors/skips. Python gateway suite reports 13 tests, all OK. Android Kotlin compilation and adapter test compilation are included in the Gradle task. These fixtures use invented nutrition values and fake credentials, not real provider matching claims. The transport tests include a real loopback HTTP server: mobile POST redirect is not followed/retried, gateway body limits and no-store/no-CORS/redacted-log behavior are verified. Earlier independent JVM fixture verification also passed 24 tests with Kotlin 2.2.10/Gson 2.11.0/coroutines 1.10.2 while the rest of the app was being completed; Gradle is the final reproducible verification.

Adapter coverage includes per-100-g, per-100-mL, per-serving and already-consumed bases; ounces by mass vs US fluid ounces; explicit food-specific mass/volume relationships; unsupported cross-unit conversions; missing vs explicit zero; unknown branded search basis and selected details; USDA nutrient IDs and kJ/derivation evidence; OFF source-set selection with no aggregate stitching, exact identity/checksum, absent records, source region, unknown nutrition, bounds and original units, sodium-from-salt and available-vs-total carbohydrates; Nutritionix full nutrient dictionary units, nested tag reference, inferred amounts and full original input; editable drink classification; safe status/errors, secure gateway URL checks, no credentials in Entry/errors/request diagnostics, duplicate suppression, cancellation cleanup and no automatic parser POST retry.

Gateway coverage includes device auth and short-token rejection, unconfigured statuses, route/parameter/body allowlists, fixed upstream destinations/keys, response projection, provider key absence from response, Nutritionix account-rights/daily-budget gates, persistent budget across restart, corrupt quota fail-closed, per-device request limits, provider Retry-After cooldown, no refund/retry after unknown billed outcome, malformed/nonfinite provider data, redacted upstream error handling and bounded real HTTP requests.

Live attempt: a read-only no-key GET of the official Open Food Facts staging fixture endpoint `https://world.openfoodfacts.net/api/v3.6/product/3274080005003.json`, using custom Newleaf User-Agent plus OFF's documented staging Basic credentials, returned HTTP 502. The original plus two identical read-only retries all returned 502. No further automatic attempts, production writes or fallback provider matches were made. Live Open Food Facts response compatibility/coverage is therefore **unverified**; the current official v3.6 and nutrient-set schemas were inspected and used for fixtures. OFF staging credentials are its public test infrastructure credentials, not nutrition-provider commercial keys.

USDA and Nutritionix live authentication, coverage, provider charges and account entitlements remain **unverified** because no keys/accounts were used. No demo commercial key was used. Actual phone-to-gateway TLS, hosting/reverse proxy controls and owner device token provisioning remain **unconfigured/unverified**; nothing was deployed. Nutritionix account rights and provider-side retention remain unresolved as documented in PROVIDERS_AND_RIGHTS.md. OFF snapshot/export flags are now true under the published ODbL/DbCL policy, with attribution/database/license URIs retained; personal journal exports preserve notices, and the export contract requires an independently licensed OFF-derived subset (data/export follow-up remains separately verified). This supersedes the initial blanket OFF gate. Nutritionix remains disabled until the operator confirms required journal/export rights and a request ceiling. The default truthful Newleaf User-Agent identifies this personal app; an owner-provided contact email/URL remains to be supplied before broader distribution.

These results complete the adapter/gateway implementation and fixture verification. They do not certify a live-configured three-provider release, commercial license, unrestricted OFF export, production hosting or device acceptance.

## Follow-up licensing correction

The manager requested removing the blanket OFF retention/export gate based on its published license grants. Nutrition source notices/URIs and true snapshot/export flags are now implemented; the existing barcode test checks both licenses and permitted flags, and a new test checks actual JSON/Markdown/backup/restore with preserved notices and nutrients. Source and documentation files were checked after the edits. The earlier 25-test Gradle success above predates this correction. The expanded 26-test suite passed independent JVM compilation and JUnit execution on October 1 using the actual nutrition and pure data/export sources, Kotlin 2.2.10, Gson 2.11.0 and coroutines 1.10.2 (`python3 /tmp/newleaf-run-nutrition-license-tests.py`: `OK (26 tests)`). These are OFF-shaped fixtures with invented values, not a successful live provider fetch. Aggregate Gradle validation still belongs to the manager; no additional Gradle run was started because the manager owns shared build/test reports. Gateway code was unchanged. Live staging 502 and unconfigured keyed-provider limitations remain unchanged. Separate OFF-derived archive subset implementation/verification belongs to data/export; this report does not claim that other owner's follow-up is complete.
