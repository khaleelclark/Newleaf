# Newleaf 0.1.2 — validation record

Historical release record. For current behavior/evidence, see [VALIDATION.md](VALIDATION.md) and [INSTALL.md](INSTALL.md).

October 2, 2026. The owner tested 0.1.1 and asked for a much simpler, human-readable interface and a main-screen barcode icon that creates a reviewed entry with a chosen category. Earlier results are retained in [VALIDATION-0.1.1.md](VALIDATION-0.1.1.md) and [VALIDATION-0.1.0.md](VALIDATION-0.1.0.md).

## Implemented behavior

Today exposes Log and Scan. Scan opens Android capture directly, starts keyless Open Food Facts lookup and shows the matched product. The user selects Breakfast/Lunch/Dinner/Snack/Other, reviews the consumed amount and presses Add to journal once. This action persists a confirmed journal entry; cancellation and lookup results alone do not save. Unknown provider amounts and nutrition remain unknown. Label amounts are references that the user reviews, not measurements of consumption.

The daily summary is compact. One water shortcut replaces the three simultaneous sizes; Log → Drink accepts other amounts. Library moves to Settings → Saved foods & recipes. History owns date browsing. Short entry questions, readable choice fields and secondary details replace first-view mode menus. Detailed nutrition/provenance, exports/backups, recipes, photos, protected keys and safe restoration remain available.

## Build and automated checks

- Signed, minified release build and mapped test APK: scripts/release.sh passed in 2m 15s (`/tmp/newleaf-simple-release-2.log`). Source/test/resource hashes were checked before and after compilation; no interleaved source changes occurred. Version code 4 and the public signing identity match the upgrade contract; apksigner verified the APK and its certificate matches 0.1.1.
- JVM: **120 passed**, zero failures/errors/skips. Nutrition's 46 regressions, including actual public USDA responses, remain included.
- Lint: **zero errors, 22 warnings**, including the existing portrait-scanner warnings. Fixed orientation is not certified on large-screen Android 16 devices.
- Optional gateway: **20 passed** (`/tmp/newleaf-simple-gateway-1.log`). No production gateway behavior changed.
- First mapped device run passed 26/31; stale navigation/selectors and a duplicate scripted click blocked five tests. The next runs passed 30/31 while a label shared by the underlying editor and review confused the test selector. The selector was scoped to the populated amount; the targeted confirmation/draft/save regression passed. Test-only rebuild and lint passed in 34s (`/tmp/newleaf-simple-test-build-4.log`). These issues were test selection, not failed product population or journal persistence.
- Final mapped signed-release instrumentation: **31 passed**, zero failures, in 29.506 seconds (`/tmp/newleaf-simple-device-4.log`). Coverage includes actual home scan navigation and one durable save with category, scanner cancellation/no-match/manual fallback, late response rejection, optional food/drink/activity logging, free-source setup/library access, protected credentials, persistence and export/restore safety.

## Device and visual checks

Owned Newleaf_API36 emulator, Android 16/API 36, x86_64, 1344×2992 at density 480. Signed 0.1.2 installed over 0.1.1 without uninstall; journal data and onboarding state stayed present. Home exposes Log and an accessible Scan barcode icon with Scan label. Scan always returns to Today before capture; History → Log remains available for earlier dates.

Actual UI checks confirm the main Scan action opens the portrait camera directly; Back returns to Today. Camera denial leaves barcode typing, Scan again and Enter it yourself. No physical barcode was decoded. Offline device tests exercise capture/lookup with fictional catalog responses; one Add persists the selected meal category and confirmed nutrition once. The captured starting amount is a label reference, not detected consumption.

Dark-theme screenshots show the compact home summary and product/category/amount review. Manual food entry keeps Save above the keyboard; at font scale 1.6 the form scrolls and Save/Close stay accessible. Screenshots are under `/tmp/newleaf-simple-evidence/`, including home.png, product-review.png, manual-food-keyboard.png, food-large-text-keyboard.png, camera.png and camera-denied.png. The specialist's final read-only screenshot review remains pending. Packaging/required-file checks must pass before attachment.

## Boundaries

Tests use fictional products and offline camera/lookup injection. These exercise actual UI navigation, category selection, one explicit save, cancellation, unknown data and recovery; they do not certify physical S25 Ultra camera decoding, its network or catalog coverage for the owner's unidentified product. No new provider calls, paid services or private nutrition-key access are needed for this revision. TalkBack and physical-phone behavior remain target-device checks.
