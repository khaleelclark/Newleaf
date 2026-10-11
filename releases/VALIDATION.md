# Newleaf 0.1.4 — validation record

October 2, 2026. Owner follow-up: quick weight logging under activity, fractional/percentage packages after scanning, and always-visible populated nutrition. Previous evidence is preserved in [VALIDATION-0.1.3.md](VALIDATION-0.1.3.md) and earlier version records.

## Implemented behavior

Movement → Log weight opens a body-weight measurement directly, with an automatic measurement name, preferred lb/kg and Save weight. Existing multiple-measurement/date/trend behavior remains intact.

Scan requests explicit normalized package size and shows whole package separately from label serving. A reported package starts at one package, labeled for review. Amount 0.5 package and Percent of package 50 both save quantity 0.5 package; all available nutrition scales once. Switching between package/serving/measured units converts the existing amount using explicit definitions, or clears an unsupported amount. Original percentage input is retained in metadata. Multipacks mean the complete outer package, with original label text visible. No package is inferred from quantity text or arbitrary density.

Populated nutrition appears in product review, confirmed food/drink drafts and saved entry details without opening Details. Known zeroes appear; absent nutrients remain unknown. Non-nutrient NOVA classification and duplicate kJ energy are excluded from nutrition amount rows. If consumed nutrition cannot be converted, the label reference remains visible with a distinct basis and explanation; it is never represented as consumed nutrition. Editing/source controls remain secondary. Quick scan still requires category, one explicit Add, duplicate/busy locks, sanitized errors and cancellation without save.

## Build and automated checks

Final signed minified release build passed (`/tmp/newleaf-portions-release-final.log`, 1m 8s). **126 JVM tests passed**, zero failures/errors/skips; 51 are nutrition tests. Lint: zero errors, 22 existing warnings. A new Android test initially inferred a non-void return, producing a runner initialization failure; its return is now explicitly Unit. A test-only unavailable keyboard helper was removed before the final successful build. Those intermediate runs are not acceptance evidence.

Final mapped signed-release instrumentation: **38 passed**, zero failures, 39.531s (`/tmp/newleaf-portions-device-final.log`). A targeted repeat of the half-bottle save/reload test at font scale 1.6 passed (one test, 4.041s, `/tmp/newleaf-portions-large-text.log`); it is not counted again in the 38 unique tests.

New checks cover the metadata-only-first OFF set, one-source nutrient isolation, half package versus half label serving, normalized multipacks, invalid/missing/unsupported package sizes, no guessed density, package-to-measured-to-serving conversion, invalid zero amount without saving, weight shortcut without naming, visible populated nutrition, percentage live preview, one durable save/reload, original input metadata, unknown protein and soda/water separation. The package snapshot also round-trips through backup/restore with definitions, nutrition, source and units preserved; JSON/Markdown retain package identity. Existing full suites continue to exercise persistence, credential isolation, drafts, offline/manual scan paths, date navigation, library and export/restore/year data.

## Public provider evidence

Two keyless read-only OFF v3.6 requests for public barcode 5449000000996 succeeded, including the exact new fields projection. Returned product: Coca-Cola Original, quantity 330 ml, normalized product_quantity 330 and product_quantity_unit ml. Its first packaging/as_sold/100g set contains only NOVA classification; its next packaging/as_sold/100ml set supplies kcal, carbohydrate, sugar, fat, protein, saturated fat and salt/sodium data. This observed shape motivates the regression: prefer the nutrient-bearing supported set, keep its origin and do not stitch companion nutrient values. The minimal offline regression predicts half of this 330 mL package as 165 mL, 69.3 kcal and 17.49 g sugar; live inspection verifies the response contract, not physical camera decoding or arbitrary catalog coverage.

Official references: [product fields](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_base/), [nutrition schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_nutrition/), [nutrition origins/bases](https://openfoodfacts.github.io/documentation/docs/Product-Opener/dev/explain-nutrition-data/). Existing OFF attribution/license and independent nutrition-subset export policy remain intact. No USDA request, provider credential access, paid service or gateway change was needed.

## Device and visual evidence

Owned Newleaf_API36 emulator, Android 16/API 36, x86_64. Signed APK installed over 0.1.3 without uninstall; existing journal/onboarding retained. Installed version verified as 0.1.4/code 6, minSdk29/targetSdk36. Public certificate SHA-256 matches prior releases: 277efd44109c70d79c1c64c9c20c1ed1a3f1ecf1f5b082130059f762f1262d8d. Apksigner verification passed. No migration/schema change.

Actual screenshots in `/tmp/newleaf-portions-evidence/`: home, product review, half-bottle percentage with keyboard and half-bottle at font scale 1.6. Home shows the weight shortcut under Movement; both percentage screenshots show populated sugar/calories and Add/Cancel fully visible above the numeric keyboard. Content scrolls at larger text sizes. The font setting was restored to 1.0 afterward.

Physical S25 Ultra camera/network/catalog coverage, Samsung keyboard and TalkBack remain target-phone checks. This iteration visually checks the current light theme; previous theme checks are historical. Gateway's 20-test result is historical and was not rerun for this app-only revision.

## Documentation maintenance — October 11, 2026

The APK is unchanged. Current product/user/build/data/provider documents were reconciled against 0.1.4 source, local links checked, historical records labeled and source/documentation archives refreshed. Existing release JVM XML still reports 126 tests with zero failures/errors/skips; Android results above remain the recorded October2 evidence. No new application build, device run or provider request is claimed. Known export metadata limitation: manifest.app_version remains 0.1.0 and spec_version 0.1; see the [data contract](../docs/DATA_AND_EXPORTS.md). Phone/performance checks remain open.

## Delivery

All named deliverables are verified by scripts/package.py before delivery: signed APK, INSTALL/CHANGELOG/VALIDATION, signature/checksums/mapping, app/gateway source and secrets-free configuration, build/license notices, and fictional JSON/Markdown/CSV/attachment-backup examples. Source ZIP integrity and excluded private files are checked. Application/release documentation is committed and pushed to the existing private repository; binaries are delivered through Project files.
