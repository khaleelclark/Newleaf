# Newleaf 0.1.1 — validation record

October 2, 2026 (work began October 1). This release responds to the owner's installed-app feedback about crowded controls, poor logging flow and barcode results that did not populate visibly. The user's particular barcode and provider response were not supplied; no claim attributes that exact failure to one cause. The previous 0.1.0 record is retained in [VALIDATION-0.1.0.md](VALIDATION-0.1.0.md).

## Changes and source review

A managed UI/UX specialist implemented three primary Log choices, progressive disclosure of optional fields, automatic barcode lookup and populated portion review, retained-code retry/manual continuation, cancellation protection and simpler secondary actions. Parent review also moved recovered drafts into a collapsed Saved drafts section. Parent scanner review also replaced the library camera-denial/restart error with an in-editor fallback and kept scanning in portrait. The nutrition engineer fixed recognized Open Food Facts products being discarded when no supported nutrition input set was available; identity is retained and unknown values remain unknown. Confirmation updates the draft; saving remains explicit.

Parent inspected the named UI, mapper, contract, regression and experience files in place. Version code 3 and the existing signing identity support updating over 0.1.0. No journal database schema or credential-storage change is introduced.

## Build and repeatable checks

- `scripts/release.sh`: signed, minified Android release and mapped test APK, release JVM tests and lint. Final build including the More details refinement passed in 2m 40s (`/tmp/newleaf-ux-release-21.log`). Signing identity was compared with 0.1.0 and matched.
- Release JVM results: **120 passed**, zero failures/errors/skips: domain 25, nutrition 46, exports 24, editor 12, UI contracts 5, barcode flow 7, exporter samples 1.
- Release lint: **zero errors, 22 warnings**; two additional warnings concern the portrait scanner orientation. This phone-focused scanner choice does not certify fixed orientation on large-screen Android 16 devices.
- `python3 -m unittest discover -s gateway/tests -v`: **20 passed** (`/tmp/newleaf-ux-gateway-15.log`). No gateway production behavior changed.
- First combined build caught two new error-message assertions compiled while specialist follow-up source was changing. The stable-source rerun passed all 119 tests; later builds also passed.
- Mapped release instrumentation passed **all 24 tests**, zero failures, in 20.936 seconds (`/tmp/newleaf-ux-device-tests-21.log`). The first run passed the existing 17 tests, while all seven new flow tests were blocked at initialization by an optimized Compose model stability field. Preserving FoodCandidate advanced them to an optimized EditorDraft companion-method boundary. A subsequent run passed 23/24; the one remaining failure involved a constructor optimized out of the shared exception type. Targeted ABI keep rules retain the shared models, companion, exception/enum and directly invoked editor/theme composables, including offline test hooks. These failures do not demonstrate a product lookup or camera failure.

## Device and visual checks

Owned `Newleaf_API36` emulator, Android 16/API 36, x86_64, 1344×2992 at density 480. Existing 0.1.0 was updated in place without uninstall; existing journal records and recovered drafts remained visible without reopening onboarding.

Actual screenshots show the initial logging picker with Food, Drink, Activity and collapsed secondary/draft groups, compared with the older twelve-plus-control populated picker. Dark-theme food entry keeps Save visible above an open keyboard. At font scale 1.6, labels wrap and Save remains pinned while the form scrolls. Screenshots are recorded under `/tmp/newleaf-ux-evidence/`, including the final `food-grouped-0.1.1.png`, `food-grouped-keyboard-0.1.1.png` and `food-grouped-large-text-0.1.1.png`. Real emulator touch checks confirmed camera permission is requested in portrait before opening capture; declining it returns to the editor with a plain manual-entry explanation. With permission granted, the actual capture screen remains 1344×2992 portrait and Back returns to barcode entry without creating a journal item. These checks do not include decoding a physical product. The specialist reviewed the Log hierarchy and keyboard/save screenshots and approved their visible hierarchy and save placement. They recommended combining competing food-editor disclosures; parent grouped favorites/portion evidence, notes/photos and timing under More details while keeping portion/calories directly reachable. Final screenshot checks and the updated logging regression verify this grouping. Final device coverage includes all seven actual-editor flow tests, free-source settings, name-only logging, durable persistence, encrypted credentials, merge/replace/recovery and export safety.

## Boundaries and preserved functionality

The UI barcode tests use fake capture and offline lookup responses to exercise the actual editor, populated product review, confirmation, save separation, retry/manual fallback, unknown portions/nutrients and abandoned-request cancellation. They do not certify physical camera decoding, phone networking, coverage of an unidentified real barcode or universal catalog completeness.

No new provider call, registration, paid service, deployment, private nutrition-key access or real journal data was used for this revision. Prior two official USDA search/detail HTTP 200 reads and one Open Food Facts product verification are recorded in the earlier validation record; the two USDA response replay cases remain in the passing nutrition suite. Physical S25 Ultra camera/network checks and TalkBack remain target-device work. The owner has installed 0.1.0 and supplied usability feedback; no physical phone was connected to this development host.

Free USDA/OFF behavior, encrypted device credentials, key exclusion from exports/backups, retained-local-key merge/replace, photos, recipes and data portability remain part of the regression coverage. Missing nutrition is never replaced by zero or guessed conversion. The new APK signature verifies and matches 0.1.0. Required-file and source-archive integrity checks are performed by scripts/package.py before attachment; CHECKSUMS.txt records the final artifacts.
