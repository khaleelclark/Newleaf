# Nutrition integration specification

Current integration for Newleaf 0.1.4; reviewed October 11, 2026. USDA and Open Food Facts supply nutrition. Food list (Describe) provides bounded USDA-assisted review. Historical authorized host reads and current recorded mapper/portion evidence are in [release validation](../releases/VALIDATION.md). No hosted gateway or paid fallback is part of the release.

## Sources and access

**USDA FoodData Central** supplies general/branded food search and detail. Register for a free data.gov key. USDA data are public domain/CC0; the documented default allowance is 1,000 requests/hour/IP. Coverage, portion definitions and nutrient completeness vary. Preserve fdcId, preparation, declared nutrient basis, retrieval time and missingness. [Official API guide](https://fdc.nal.usda.gov/api-guide/)

**Open Food Facts** supplies public packaged-food/drink barcode lookup without a user account or key. Requests identify Newleaf with a custom User-Agent. Data are community contributed and may be missing or incomplete. Database, content and image licensing differ; attribution and an independently extractable licensed nutrition subset accompany exports. No diary information is publicly contributed. [Official API documentation](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/)

**Nutritionix is disabled.** Its developer portal announces the withdrawal of public free access; limited trials do not meet the owner's free-only requirement. Legacy credentials must not activate a commercial endpoint. No commercial parser is required. [Official developer announcement](https://developer.nutritionix.com/)

## User actions and review

| Action | Source and behavior | Fallback |
| --- | --- | --- |
| Food search | Local saved items plus explicit USDA query/details | Manual entry with unknown nutrition |
| Barcode | One Open Food Facts lookup per captured code | Enter/search manually, retaining barcode |
| Describe meal | Bounded comma/newline item list; optional leading quantity, fraction and unit; provisional USDA matches | Keep original description and continue manually |
| Repeat or recipe | Local immutable reviewed snapshots | Works offline |

Describe accepts at most eight food/drink fragments. It is deterministic assisted search, not unrestricted language understanding or AI analysis. Original text, supplied quantities and inferred amounts remain distinguishable. All matches require review; editing a suggestion never silently saves it. USDA keyword similarity is not proof of identity. A missing serving conversion leaves nutrition unknown rather than inventing a gram or fluid-ounce density.

Home Scan requires category and one Add to journal; Find a food → Barcode inside an editor uses Confirm item, then Save. Supplied nutrition stays visible in review/drafts/details. Explicit normalized package g/mL defines a whole package; half-package and 50-percent inputs scale once. Label-serving size stays separate, and missing package size/density stays unknown.

Food detail retains description-derived portion hints while obtaining the selected source's nutrient basis. Measured quantities scale only against compatible declared units/weights. A consumed serving must not be multiplied again as though its values were per 100 g. Unsupported or partial fields stay explicitly unknown. Local overrides preserve attribution and are never uploaded; historical journal entries do not refresh themselves from source data.

## Personal credentials and optional gateway

The primary setup is a personal free USDA key entered on the phone. Store it in a dedicated Keystore-protected credential slot, separately from an optional gateway token. Never put it in plaintext journal metadata, settings exports, JSON/CSV/Markdown, backups, diagnostics, APK assets or Git. Backup restoration preserves the installation's current credentials and cannot import credentials from a file.

Send the key only as the `X-Api-Key` header to the official HTTPS FDC origin, never in a URL. Disable redirects and automatic retries. Search sends the explicit food query; details send a selected fdcId; descriptions send food fragments as bounded searches. Open Food Facts receives only a barcode. Neither path sends body weight, feelings, activities, photos or the whole journal. Third-party retention is governed by that service; do not claim it retains nothing.

An authenticated HTTPS gateway remains optional for server-managed free USDA access. Shared deployment keys stay on the server; the phone protects its device token. The gateway contains no diary database, bounds input/response size, sanitizes errors, enforces persistent request ceilings and does not log food-query payloads by default. Public deployment is outside this handoff; personal-key mode requires no server or hosting fee.

## Reliability and quotas

Use bounded timeouts/response sizes and explicit actions, cancel abandoned work, and retain drafts on authentication, quota, network or malformed-response errors. Do not automatically repeat calls or follow redirects with credentials. Quota errors offer a user-initiated retry later; manual logging, favorites and exports continue offline. No billing credentials, commercial endpoint activation or paid fallback exists.

Preserve separate source identities and nutrient evidence rather than filling gaps from similarly named foods. Source-derived records carry reference basis, quantity, license/attribution and confidence only when actually supplied; never invent a confidence score. Export source nutrition alongside independently licensed Open Food Facts reuse data without exposing the private diary in that subset. Private user photos are distinct from provider product images.

## Verification boundaries

Fixtures must cover direct-header authentication, allowed HTTPS origin, blocked redirects, credentials excluded from URL/errors/exports, disabled commercial access, quota/error handling, bounded description fragments, quantities/fractions, retained detail hints, compatible scaling and unknown units. Replay real public USDA/OFF responses through the same mappers, without fixtures containing credentials. Device tests verify protected credential persistence and merge/replace preservation.

A successful host API call proves the provided key and that response, not Android physical-phone networking, product coverage or camera behavior. Record actual release/device evidence and remaining physical S25 Ultra checks in `releases/VALIDATION.md`.
