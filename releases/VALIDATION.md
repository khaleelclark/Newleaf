# Newleaf 0.1.0 — validation record

October 1, 2026. This record distinguishes local implementation, repeatable fixtures, Android runtime evidence, and remaining external/device acceptance. No physical Galaxy S25 Ultra was connected.

## Environment and commands

- Development host: Linux, JDK 17, Gradle 8.13, AGP 8.12.0, Kotlin/Compose compiler 2.2.10; compile/target API 36, minimum API 29.
- Owned test emulator: `Newleaf_API36`, Google APIs Android 16/API 36, x86_64. Display override 1344×2992 at density 480; this approximates screen dimensions and does not emulate Samsung One UI or physical camera hardware.
- Baseline before the free-only adaptation: `scripts/release.sh` — signed, minified release build passed; 89 release JVM tests, zero failures/errors; release lint: zero errors and 21 informational/dependency/API warnings. Pinned API 36 and dependency versions are intentional for this build.
- Baseline gateway: `python3 -m unittest discover -s gateway/tests -v` — passed: 13 tests.
- Initial debug instrumentation — passed: 11 device tests, zero failures, including Compose logging, Room persistence, multiple repository handles, Keystore credentials, merge/replace recovery, corruption rejection, and a 2,190-record year fixture.
- Baseline mapped release instrumentation — all 13 tests passed, including Compose logging. Runtime ABI keep rules preserve shared dependencies used by the separate test APK. Final free-only changes require a new build and test run.

Final free-only build: `scripts/release.sh` passed in 2m 10s. All **108 release JVM tests** passed with zero failures/errors/skips: domain 25, nutrition 42, exports 24, editor 11, UI contracts 5 and exporter samples 1. Release lint reported **zero errors and 20 warnings**. The signed APK is 11,563,763 bytes. The optional gateway suite passed **20 tests**, including real public USDA response projection and disabled commercial routes. First final mapped release run: 16/17 passed; the existing logging test ambiguously matched both a timeline Food label and the log-picker button. Its selector is now scoped to button role and the fixture name is unique, preserving populated-journal rerun behavior. No production app change was required; The second run again passed 16/17: the same test’s generic unknown-calorie assertion matched two existing timeline records. The assertion is now tied to the newly logged entry and its actual detail nutrition label, rather than underlying timeline content. Final mapped release instrumentation passed **all 17 tests**, zero failures, in 19.765 seconds (`/tmp/newleaf-free-device-tests-14.log`). Test coverage includes separate encrypted USDA/gateway aliases, Room plaintext absence, durable reopen/update/clear, credential exclusion and preservation across merge/replace/recovery, onboarding merge policy, both free-source Compose flows and populated-journal logging.

The first device run exposed SQLITE_BUSY. The fix uses a process-owned Room database with serialized database work and a shared mutation mutex; repository handles close independently. The successful rerun exercised simultaneous handles and durable reopen behavior. Unchanged records now short-circuit structural comparison during saves. Editor regressions cover source-basis preservation, independently exported drink-base evidence and removing all drink additions.

## Acceptance evidence

| Specification scenarios | Evidence and boundary |
| --- | --- |
| A01–A02, A09 | Compose device test: welcome without account, quick water and Undo, manual name-only food logging. Unknown nutrition retained. |
| A03, A06–A08, A10, A12–A13 | Domain/adapter fixtures: portion scaling, independent bases, consumed parser values, inferred amounts, failures/quotas, drink composition, recipe snapshots and missing yield. Actual host USDA authentication/search/details verification is recorded below; all 42 final nutrition fixtures passed, including real USDA search/detail and description replay. |
| A04–A05 | Barcode identity/checksum/no-match and offline/manual fallback are implemented and fixture-tested. Physical camera scanning/permission denial remain phone acceptance. |
| A11 | Repeat uses new IDs and historical nutrient snapshots; domain copying is tested. Complete touch workflow remains phone review. |
| A14–A18, A21 | Domain/editor/device fixtures cover elapsed times, daily step replacement including zero, distance/weight conversions, multiple weight means, preference changes, DST gaps/overlaps and cross-midnight activities. |
| A19–A20 | Local journal works independently of gateway configuration; device tests cover durable entries and drafts after repository reopen. Extended background/process-death touch acceptance remains. |
| A22 | Device test: delete/unlink and Undo recover consistent dependent links. Meal editor transaction checks are included in the final run below. |
| A23–A26 | Export fixtures cover contextual records, unknowns/zero, Unicode/newlines/CSV formula protection, notes exclusions, schemas, photo digests and complete archive restoration. Device tests cover private photo restoration and recovery before replace. Android file-picker export/share and final restore touch checks are recorded below. |
| A27 | Sparse summaries, observation counts and missing-day handling fixture-tested; accessible numeric table accompanies charts. |
| A28 | Release reinstall smoke result below; future higher-version migrations and physical phone update remain pending. |
| A29 | Light/dark themes and scalable layout implemented. Emulator layout checks below; actual TalkBack/One UI/large text on S25 Ultra remain unverified. |
| A30 | Device token encryption/exclusion and sanitized diagnostics fixture/device-tested. Personal USDA key exclusion/hostile import and safe diagnostics pass JVM tests; separate Keystore persistence, independent slots, reopen and credential-safe restoration pass release device tests. Source packaging excludes signing keys/private configuration. No real user journal was used. |
| A31 | Two free USDA host calls passed; Open Food Facts production verification is recorded below. Physical phone networking and optional gateway deployment remain unverified. |

## Release and performance evidence

- Version 0.1.0, version code 2; package `com.khaleel.newleaf`, minimum API 29, target/compile API 36. The release is non-debuggable, minified and resource-shrunk. The APK uses a private persistent RSA-3072 signing key; `SIGNATURE.txt` contains public certificate verification only.
- Signed in-place update from candidate version code 1 to 2 succeeded on API 36 and preserved completed onboarding plus an existing 16 US fl oz water record. No uninstall or data clearing occurred. Future releases still require the same key and a higher version code.
- Final year fixture: 2,190 records across 365 days; mapped-release emulator warm repository reopen 271 ms, one correction 603 ms. These are repository operations, not whole-app cold start, and emulator timings vary with host load. The final signed-release `am start -W` cold activity launch reported 799 ms to first frame with a small journal. Neither result certifies physical S25 Ultra targets.
- One Open Food Facts production HTTP 200 response for product `3017620422003` was replayed through `NutritionClient.barcode` and `FoodCandidate.toEntry`. Verified 539 kcal/100 g; a 15 g serving maps to 80.85 kcal and two servings to 161.7 kcal. All 27 nutrition adapter fixtures passed. This verifies one real response and its mapping, not product completeness or Android phone networking.
- The owner’s private USDA key authenticated two read-only official HTTPS calls (search and food details), both HTTP 200; fdcId 1750340. Key was transmitted only in an authentication header and was not printed, committed or embedded in any artifact. Both public responses passed direct-client replay: source 100 g basis, named 140 g RACC portion, estimated energy, null versus zero, unsupported servings/volume and preserved description hints. Android protected-credential persistence and restore tests passed in the final 17-test release suite.
- JSON, Markdown, CSV ZIP and photo-backup samples are generated by the actual exporters from seven fictional records. The sample backup is restored and photo digest checked by the release unit suite; all 18 CSV tables, dictionary and archive integrity manifest are present.

Baseline signed release export touch smoke passed: Settings → Export → Save export opened Android’s document picker, saved a 12,429-byte JSON file to Downloads, and the saved bytes parsed successfully. Share using Android opened the platform share sheet with the correct JSON filename; no external recipient was selected.

Baseline restore touch smoke opened the Android document picker, selected the fictional attachment backup and displayed the validated preview: schema 1.0, seven records, one photo and storage estimate. Merge completed but applied the sample’s unfinished-onboarding setting and reopened welcome. The shared merge path now preserves this installation’s onboarding setting; its expanded device regression passed. Final signed-release picker touch rerun also passed: selected the seven-record/one-photo backup, reviewed a real conflict, chose Keep phone and merged; Today remained available and showed Journal restored, with no welcome screen.

Final release export touch smoke passed: saved via Android’s document picker to `newleaf-journal-2026-10-01 (1).json`, 47,068 bytes, 11 records; the saved JSON parses and settings omit both credentials. The app showed Export saved. Backup merge touch smoke and all 17 mapped release tests passed. Final dark-mode/1.5× font-scale editor review passed: numeric keyboard open, weight field and lb/kg choices usable, Save weight fully visible above the keyboard; screenshot inspected. Modal status-bar icons can retain the system appearance when an app theme overrides system mode, so target-phone contrast remains a minor visual follow-up. Packaging verified every named handoff file, source ZIP CRC integrity, both ZIP/APK member scans for signing files and the actual private USDA key, and SHA-256 checksums against all delivered binaries/samples. Source archive is approximately 3.3 MB; APK is approximately 11 MiB. No secret value was emitted by these checks.

## Source history and external access

The private repository is [khaleelclark/Newleaf](https://github.com/khaleelclark/Newleaf), with `main` published over SSH. Source checkpoints are authored as `khaleelclark` with the account's verified numeric noreply address. Repository creation used the owner's signed-in GitHub browser; SSH also verified that account. The initial remote `main` SHA matched the local HEAD exactly. Release follow-up commits are pushed separately; the final remote comparison is performed after the handoff documentation commit and before artifact delivery. Signing keys/passwords, provider credentials, generated APK/source ZIP/mapping and SDK/build caches are excluded from Git; fictional export samples are included.

## Remaining physical and provider checks

On the physical phone, verify installation, one-handed logging, camera/scanner, picker grants, TalkBack, large text, dark mode, notifications and actual cold-start/save performance. No one-year target-phone 300 ms save / 2 s cold-start claim is made from emulator timings. Notification alarms are inexact and reschedule when the app is reopened after reboot.

USDA uses the owner’s free data.gov key entered on the phone; the key is not embedded in the APK. Direct personal-key mode requires no gateway. Nutritionix is disabled. No account, subscription, hosting service or commercial call was created. Optional gateway fixtures do not verify production deployment/TLS controls.
