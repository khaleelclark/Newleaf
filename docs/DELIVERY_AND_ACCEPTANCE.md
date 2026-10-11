# Architecture, acceptance and delivery

Current implementation: Newleaf 0.1.4. Documentation reviewed October 11, 2026. APK distribution is personal; public app-store delivery, paid services and a hosted gateway deployment are outside the current release.

## Architecture

| Component | Implemented behavior |
| --- | --- |
| UI | Kotlin/Compose, custom Newleaf palette/components, Today/History/Trends and More chooser |
| Journal | Room/SQLite schema 1, unified Entry payloads with indexed fields, process-wide storage ownership and transactional edits |
| Settings/drafts | Local metadata and durable drafts; USDA key/device token use independent Keystore-backed encrypted slots |
| Nutrition | Explicit USDA search/details and bounded Food list matching; direct keyless Open Food Facts barcode lookup |
| Gateway | Optional Python standard-library USDA service; authenticated HTTPS proxy needed for phone access |
| Photos | Private app files, system picker/camera, explicit original-byte export |
| Portability | Schema 1.0 JSON, 18-table CSV ZIP, Markdown, full attachment backup, validated merge/replace restore |
| Reminders | Optional notification permission and Android inexact scheduling |

MinSdk29, compileSdk36 and targetSdk36 are pinned in app/build.gradle.kts. [Build](../BUILD.md) records toolchain versions and commands. Room is not a separately encrypted database. Private app storage and device protection are the journal baseline; credentials have additional Keystore protection.

Only explicit Save/Add commits a draft. Home Scan returns to today and requires a category before Add; nested Confirm item enriches the current draft before Save. Transactions commit before state publication. Cancel and stale lookup responses do not create records. Recipe/portion/source snapshots are immutable unless the user explicitly edits the entry.

## Privacy and recovery

No analytics, advertisements, automatic AI upload, diary cloud sync or health-account integration. CAMERA is requested for scanning/capture; POST_NOTIFICATIONS is optional for reminders; INTERNET supports explicit lookup. Pickers avoid broad storage access. Platform backup is disabled in the manifest/extraction rules; full user-directed backups exclude credentials and transient drafts. Uninstall/data-clear deletes local data. Replace restore writes a local recovery backup before its transaction; merge conflicts require an explicit resolution. See [installation/recovery](../releases/INSTALL.md) and [data contract](DATA_AND_EXPORTS.md).

## Acceptance scenarios

These IDs retain the original contract for maintenance. They describe expected results, not 31 independently executed device tests. Automated coverage is recorded in [validation](../releases/VALIDATION.md); physical phone accessibility/network/camera and performance are separate open checks.

| ID | Scenario | Required result |
| --- | --- | --- |
| A01 | First launch without account or goals | User reaches Today and can log a manual meal |
| A02 | Save a food with only a name/time | Entry persists; nutrition is unknown; totals show partial coverage |
| A03 | Log measured 150 g against fixture 200 kcal/100 g | Exactly 300 kcal before display rounding; basis exported |
| A04 | Scan a supported product | Review correct product/source/portion before saving; one lookup per captured code |
| A05 | Deny camera or scan missing barcode | Manual barcode/search/custom entry stays available; draft preserved |
| A06 | Describe a mixed meal with a coffee | Review parsed foods and drink; edit amounts; no double counting |
| A07 | Description supplies a provisional amount | Estimated remains visible after user confirms the food identity |
| A08 | Parser times out, quota expires, or authentication fails | Clear scoped error; local logging continues; no automatic retries or paid fallback |
| A09 | Log quick water then Undo | Exactly one entry removed; volume/nutrition return to prior values |
| A10 | Log coffee with milk/sugar | Composition contributes to drink total once; water total remains separate |
| A11 | Repeat yesterday's meal | New IDs/time, same reviewed snapshot; editing copy leaves original unchanged |
| A12 | Edit recipe after logging it | Old entry retains old version; new entry uses new version |
| A13 | Log recipe grams without cooked yield | No fabricated gram nutrition; user can supply yield or log servings |
| A14 | Add a feeling two hours after meal | Linked meal and observed/entered times retained; elapsed time correct |
| A15 | Replace today's step total | One daily total; activity distances unchanged; zero differs from missing |
| A16 | Add walk/hike with distance and duration | Both recorded in preferred units, canonical values exported |
| A17 | Add two body weights on one date | Both retained; daily mean and measurement count match |
| A18 | Change preferred units | Same canonical data; original entered units retained; no drift on round-trip |
| A19 | Use app offline | History, saved items, manual logging, recipes, weight, local export work |
| A20 | Background app mid-entry | Draft survives; no ghost save or duplicate when resumed |
| A21 | Edit date across midnight/time-zone boundary | Original/revised date is explicit; affected totals update correctly |
| A22 | Delete meal with dependent entries and undo | Chosen dependent behavior is explicit; undo restores consistent links |
| A23 | Export date range with outside-range related feeling/meal | Context marked; range summaries exclude context-only entries |
| A24 | Export all formats with Unicode/multiline/formula-like notes | Text preserved/documented; CSV safe; JSON original values intact |
| A25 | Backup/restore full journal including photos | IDs, recipes, notes, sources, settings, and attachment digests preserved |
| A26 | Cancel or fail restore/export | Existing journal intact; no success claim for incomplete file |
| A27 | View sparse trends | Missing days remain missing; observation/coverage counts shown |
| A28 | Install signed update over prior version | Data preserved; migrations succeed; signing identity unchanged |
| A29 | S25 Ultra with large text/TalkBack/dark mode | Primary logging usable; no clipped controls or inaccessible charts |
| A30 | Inspect release artifact/logs/export | No provider secret, device token, or sensitive diagnostic payload leaked |
| A31 | Exercise every selected provider with approved access | Actual free search/barcode/assisted matching verified, mappings and quotas documented |


Additional 0.1.4 regressions cover direct weight logging, visible populated nutrition, half-package/percentage equivalence, package-versus-label-serving size, unit conversion without guessed density, and save/reload/export/restore of portion definitions.

## Release evidence and open checks

The recorded 0.1.4 release passed 126 JVM tests and 38 mapped signed-release emulator tests on Android16/API36 x86_64. A font-scale1.6 half-bottle repeat is separate evidence, not an extra unique test. Lint recorded zero errors and 22 warnings. Historical theme/provider/gateway checks are not silently counted as new runs. Exact results and limits are in release validation.

Physical S25 Ultra installation prompts, camera/catalog/network behavior, Samsung keyboard, TalkBack and target-phone performance remain to be checked. The original targets of entry-save feedback within 300ms and cold start within 2s with at least one year of representative data have not been certified on the phone. OFF's owner contact User-Agent override and optional gateway HTTPS/proxy operation remain distribution/deployment tasks.

## Handoff checklist

Before publishing a release, verify each actual file, then run scripts/package.py:

- releases/Newleaf-<version>.apk, signed with the established key and increased version code for an app update.
- releases/INSTALL.md, CHANGELOG.md and VALIDATION.md matching that APK.
- releases/SIGNATURE.txt, MAPPING.txt and CHECKSUMS.txt; retain the exact minification map for that APK.
- Newleaf-<version>-source.zip containing application/gateway source, build instructions, docs, schemas/tests and secrets-free gateway/.env.example.
- releases/samples/example-journal.json, example-report.md, example-spreadsheets.zip and example-backup.zip plus sample README; fictional data only.
- Named documents in the README index, valid local links, and explicit boundaries between release evidence and acceptance targets.

Never publish signing keys/passwords, device/provider credentials, local SDK paths or real journal data. Back up the established signing identity privately before relying on future updates. Run release/unit/lint checks and mapped instrumentation after code changes; documentation-only maintenance validates links, file contents, packaging and checksums without implying a new APK test run. See [documentation procedure](DOCUMENTATION_STATUS.md).
