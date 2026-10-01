# Nutrition integration specification

Documentation checked October 1, 2026. No provider credentials have been requested or used, and no subscriptions or live integration tests have been performed.

## 1. Three-source requirement

Newleaf must include all three source categories requested by the owner:

1. USDA FoodData Central for general food search and nutrient detail.
2. Open Food Facts for packaged food/drink barcode lookup.
3. A commercial meal-description parser, proposed as Nutritionix.

Nutritionix and fatsecret were presented together as the third category during planning. This specification selects Nutritionix as a proposed default and preserves fatsecret as an alternative if access, pricing, coverage, or export rights fit better. Integrating both commercial services is not currently required. Do not substitute providers silently; document any final choice before implementation.

## 2. Provider roles and verified capabilities

### USDA FoodData Central

Provides food search and food detail, covering several general and branded food datasets. Requests require a data.gov API key; the documentation requires keeping it private. Data is published under CC0/public domain. The documented default limit is 1,000 requests per hour per IP, with a temporary block after exceeding it. [Official API guide](https://fdc.nal.usda.gov/api-guide/)

Planned use: search ordinary foods, retrieve selected details, and normalize preparation/portion information. Nutrition and portion mappings need fixture verification; do not assume every food has every nutrient or usable serving weight. Reference ID is fdcId. Newleaf includes source attribution in details and exports.

### Open Food Facts

Provides product nutrition/ingredients and barcode-based product lookup. Its data is community contributed and may be incomplete. Documentation identifies separate database/content/image licenses, requires a custom User-Agent, and sets endpoint-specific limits. It recommends current v3 for new product integrations; product lookup and full-text search are distinct capabilities. [Official API documentation](https://openfoodfacts.github.io/openfoodfacts-server/api/)

Planned use: barcode-first packaged food and drink matching; preserve the scanned code and product identity. Use a pinned supported lookup version after implementation-time verification. Do not fire a barcode request on every camera frame. Missing records or nutrients lead to editable custom entry. No automatic public contribution of the owner's meals/photos.

### Nutritionix, proposed third provider

Its documented natural-language nutrients endpoint is `POST /v2/natural/nutrients` on `trackapi.nutritionix.com`. Authentication uses app ID/key. Responses include foods, serving quantities/units/weights, and nutrients; the API has options for time context and branded foods. [Official natural-language documentation](https://docx.syndigo.com/developers/docs/natural-language-for-nutrients)

Planned use: explicit meal-description analysis. Adapter output separates original phrases, matches, quantities, and inference status. The consumed nutrient basis must be verified before rescaling. App-selected event time wins over interpreted relative language unless the user explicitly accepts a suggested time. Access entitlement, current prices, and storage/export permission remain unverified account-specific dependencies.

### fatsecret alternative

fatsecret documents meal-description processing and image recognition. Voice input would require transcription before parsing. Its language and photo capabilities are optional usage-billed add-ons with a trial, and pricing details are supplied through its account dashboard. [Official feature and pricing explanation](https://platform.fatsecret.com/features/image-recognition-and-nlp)

If selected, it fills the third category rather than adding a parallel fourth mandatory service. Photo recognition is still deferred regardless of provider availability. Retention/export terms and actual costs must be confirmed for the chosen account.

## 3. Routing and result behavior

| User action | Local behavior | Remote behavior | Failure fallback |
| --- | --- | --- | --- |
| Food search | favorites, recent foods, custom foods | USDA search/details | use local results or manual entry |
| Barcode | cached permitted product snapshot | Open Food Facts product lookup | type/search/create item with barcode preserved |
| Describe meal | retain local draft/text | Nutritionix parser, proposed | continue manually without discarding text |
| Repeat favorite | copy local saved snapshot | none required | works offline within resolved retention policy |
| Recipe logging | use local recipe version | none unless adding a new ingredient | save incomplete nutrition explicitly |

Sources complement each other rather than making every interaction call all three. Online search must not wait for all sources to return. Do not assert a USDA keyword match is an exact barcode product match. A later secondary barcode provider can be considered only with a documented capability/access check.

Ranking favors exact identifiers, brand/preparation, appropriate region, usable serving data, and known nutrient coverage. Local favorites receive priority. Results may be grouped when identities clearly match, but source records remain separate; do not stitch one food's calories to another food's sodium merely because their names resemble each other.

User edits to a provider result create a local override with attribution intact. The override is not uploaded to the provider. Providers must not update historical diary entries in the background.

## 4. Normalized adapter contract

Each adapter returns:

- Namespaced provider/item identifiers, barcode if present, name/brand/preparation, region.
- Serving definitions, nutrient basis, nutrient codes/units/values, missingness, and retrieval time.
- Original input fragment and whether each portion was supplied or inferred, when available.
- Attribution, license/contract identifier, permitted persistence/export flags and cache expiry.
- Match status and any supported confidence metadata; do not invent numeric confidence when the provider does not supply it.
- Structured failure category: no match, incomplete data, unavailable, rate limited, authentication, access entitlement, malformed response.

These fields are proposed Newleaf requirements; provider documentation does not guarantee every field exists. Missing adapter fields remain explicit. The gateway and mobile adapter must reject an unknown nutrient basis instead of guessing.

## 5. Credentials and a small nutrition gateway

Proposed deployment: the phone calls a small authenticated gateway for keyed USDA/commercial requests. Provider credentials stay on the server, out of the APK, source control, exported files, and diagnostic logs. Open Food Facts can be accessed directly with an identifying User-Agent if the final architecture permits it; rate limits and licenses still apply.

The gateway has no diary database and does not receive body weight, feelings, activity, personal notes, photos, or the full journal. It receives food queries, identifiers, and explicit meal-description text. Provision a device credential for the owner's installation; protect it on the phone and provide a rotation path. A personal app does not require public signup, but gateway requests still need authentication, request bounds, and quota control.

Use TLS, redacted logs, no food-query payload logging by default, and short-lived processing. Record the selected providers' actual retention policies before making any “nothing retained” claim; gateway behavior cannot control third-party retention. The online feature explanation identifies the recipients.

Gateway hosting is a proposal, not an authorized deployment. Select location/provider, recurring-cost ceiling, and operational ownership during implementation planning. If gateway hosting is declined, revisit the credential design; embedding reusable secret keys in the APK is not an acceptable default.

## 6. Reliability and cost control

Provider calls have bounded timeouts and cancel when a draft/action is abandoned. Search can retry transient read failures with a bounded backoff; rate-limited calls honor Retry-After and quota budgets. A meal-description POST may be billed; do not automatically repeat it unless provider idempotency/cost semantics are confirmed. Retry is an explicit user action otherwise, with the existing draft retained.

Cache only fields/durations permitted by the provider. Distinguish catalog cache, per-entry nutrition snapshots, and full-diary backup rights. A cache expiry must not silently erase journal nutrition; if the provider does not permit durable snapshots, it is unsuitable for Newleaf's required full-history exports without another arrangement.

Use local favorites first, explicit Analyze actions, duplicate in-flight request suppression, endpoint limits, and a configurable commercial-use ceiling. Reaching a ceiling disables that online action gracefully, while local logging and exports remain usable. Exact limits/prices are configuration established from the selected account, not values guessed in this spec.

## 7. Export and reuse decision record

Before selecting a commercial plan or final export implementation, record evidence for:

1. Displaying matched nutrition and showing attribution.
2. Retaining nutrition in a user's historical journal and favorites/recipes.
3. Retaining referenced provider identifiers and derivative calculations.
4. Exporting nutrition in JSON, CSV, Markdown, backups, and user-directed AI analysis.
5. Storing/displaying/exporting product images separately from user photos.
6. Gateway caching, offline use, retention durations, and deletion requirements.

Review the applicable Open Food Facts reuse terms as well. This document identifies requirements, not a completed legal determination. Keeping attribution and separating provider catalogs from personal journal data does not by itself establish compliance.

**Release gate:** the full-data export requirement must be compatible with the selected services. If a restriction prevents it, resolve access or provider choice with the owner; do not silently omit data and label the export complete. The user's journal and manual logging remain independent of provider availability.

## 8. Verification needed later

Use permitted fixtures/live calls to test a generic cooked food, packaged beverage, missing barcode, incomplete nutrition, recipe ingredient, and mixed food/drink description. Confirm exact scaling, food identity, region, error handling, attribution, cache behavior, and actual account charges. Document coverage misses instead of claiming universal food recognition.
