# Newleaf 0.1.3 — validation record

October 2, 2026. The owner requested a better flow inspired by Simple after testing 0.1.2. Prior release evidence is preserved in [VALIDATION-0.1.2.md](VALIDATION-0.1.2.md), [VALIDATION-0.1.1.md](VALIDATION-0.1.1.md) and [VALIDATION-0.1.0.md](VALIDATION-0.1.0.md).

## Reference and implemented behavior

Read Simple's official [food logging](https://help.simple.life/en/articles/9887898-how-to-log-and-edit-food), [drink logging](https://help.simple.life/en/articles/9887906-how-to-log-and-edit-drinks) and [tracker overview](https://help.simple.life/en/articles/9887852-simple-life-overview-of-main-features). Applied direct tracker-local actions, a description-first food form, familiar beverage choices and week/date navigation. Newleaf retains its own artwork/design system and local journal/export contracts.

Today has Food → Log food, Drinks → Log drink/quick water, and Movement → Log activity/Add steps. Header Scan opens capture immediately and retains the confirmed product/category/one-Add contract. Food accepts a short name/description and optional meal category; More details holds amount/calories. Free text alone does not infer nutrition. Drink changes retain volume/event but clear prior product nutrition/source and update water classification. More retains the complete chooser and saved drafts; History search/export is secondary. Week selection and Calendar apply the explicit date to direct logging; Scan returns to today.

## Build and automated checks

Initial signed minified build passed (`/tmp/newleaf-inspired-release.log`, 2m 42s). Initial JVM results: 121 passed, zero failures/errors/skips. Initial mapped signed-release instrumentation: 35 passed, zero failures, 33.94s (`/tmp/newleaf-inspired-device.log`). Lint: zero errors, 22 warnings. A subsequent visual adjustment prevents weekday labels wrapping at large text sizes and removes a duplicate unknown-calorie warning; final rebuild passed (`/tmp/newleaf-inspired-release-final.log`, 2m 4s). Final JVM: **121 passed**, zero failures/errors/skips. Final signed-release instrumentation: **35 passed**, zero failures, 35.296s (`/tmp/newleaf-inspired-device-final.log`). Final lint: zero errors, 22 existing warnings.

HomeFlowUiTest exercises actual repository saves from direct food and drink actions, name-only/mixed-description food without invented nutrition, coffee volume/water isolation, selected-date logging, and movement/steps cancellation. Existing tests cover one-save scan/category, cancellation/no-match/late lookup, draft-versus-save review, UI source setup/library, Room persistence, credentials, export/backup/restore and year-sized data. EditorStateTest verifies beverage changes retain entry ID, creation time, version progression, date/notes/category/volume while clearing stale nutrition/provenance.

## Device and visual evidence

Owned Newleaf_API36 emulator, Android 16/API 36, x86_64. Signed release installed over 0.1.2 (version code 4 → 5) without uninstall; existing journal and onboarding data remain. Public certificate SHA-256 matched 0.1.2 and apksigner verified the APK. No migration/schema change was introduced.

Actual screenshots in `/tmp/newleaf-inspired-evidence/` show home trackers in light/dark themes, food keyboard and drink volume/selector. Save remains visible above the keyboard. Font-scale 1.6 testing caught wrapped week labels, corrected to single-line labels with horizontal scrolling. The food editor at font scale 1.6 keeps Save and Close visible above the keyboard. Large-text weekday labels stay on one line; the week can scroll horizontally.

## Boundaries

Catalog/capture tests use fictional offline fixtures; no production provider calls or private nutrition key access were needed. Physical S25 Ultra barcode decoding, network/catalog coverage, One UI keyboard/installation and TalkBack remain target-phone checks. No nutrition/backend/export implementation change, paid service or public deployment was made. Gateway's 20-test result is historical from 0.1.2; this UI revision does not rerun or claim new gateway verification.

## Delivery and version control

All named handoff files are checked by `scripts/package.py`; it verifies a nonempty signed APK, INSTALL/CHANGELOG/VALIDATION, public signature, build/dependency notices, mapping, application/gateway source and secrets-free example, and fictional JSON/Markdown/CSV/attachment-backup samples. Source ZIP integrity and excluded private configuration are checked before import. APK version 0.1.3/code 5 and unchanged public signing identity are verified. Source/release documentation is committed and pushed to the existing private Newleaf repository; binaries are delivered through Project files.
