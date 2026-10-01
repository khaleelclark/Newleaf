# Implementation proposal, acceptance, and eventual delivery

Version 0.1 — planning only. Nothing in this document authorizes development or deployment now.

## 1. Proposed architecture

| Component | Proposal and purpose |
| --- | --- |
| Android UI | Kotlin with Jetpack Compose and custom Newleaf components |
| Journal | Room/SQLite local relational storage with transactional writes and versioned migrations |
| Preferences | Local settings store for units, appearance, reminders, and goals |
| Nutrition | Separate adapters for USDA, Open Food Facts, and the selected commercial parser |
| Gateway | Small authenticated service protecting provider keys; no diary sync/storage |
| Photos | Private app storage; explicit attachment export; Android picker/camera integration |
| Export | Local generators for structured JSON, CSV ZIP, Markdown, full backup |
| Reminders | Optional local scheduling, permission requested when enabled |

Compose supports custom design systems, so using it does not require adopting Material visuals. [Android design-system documentation](https://developer.android.com/develop/ui/compose/designsystems)

Android documents local databases/private app storage and the system file-picker approach for user-selected documents. Room is the proposed persistence tool; it does not itself guarantee application-level encryption. [Android storage documentation](https://developer.android.com/training/data-storage/)

Final dependency versions and Android SDK levels are selected and verified during implementation. Proposed minimum is Android 10/API 29; the target SDK must fit the current supported toolchain and the owner's actual OS. Delivery testing must cover the actual S25 Ultra installation, not just an emulator. Do not assume its factory Android version remains current.

The journal is the authoritative local state. Online results enrich a draft; only an explicit save creates journal records. Save operations use transactions and stable draft IDs. User data survives app restart and an in-place signed update. No prototype JSON store substitutes for the eventual transactional journal.

## 2. Privacy, backup, and permissions

No advertisements, analytics SDK, automatic AI upload, or cloud diary sync in the first release. Sensitive content is excluded from application and gateway logs. Food-query transmission is explained at first online use. Provider credentials remain server-side; device credentials use platform-protected storage.

Private local storage is the baseline, with Android device protection. Optional biometric app lock can be evaluated later; do not promise encryption at the database layer without an explicit implementation and recovery plan. Backups/exports leave the app only through the user's selected destination. Explain that a normal export is readable wherever it is saved.

Use least-required permissions: camera when scanning/capturing; notifications when reminders are enabled. User-selected photo/document access uses system pickers. No location, activity sensor, broad storage, or Samsung Health access for manual logging.

Configure platform backup policy explicitly before release; proposed default excludes the journal/credentials from automatic platform cloud backup because this release supplies an explicit backup flow. This is an implementation requirement, not a claim that Android would automatically do so. Document uninstall/data-clear behavior and suggest exporting a backup first.

## 3. Development sequence after authorization

1. **Visual review and access decisions:** review Today and logging layouts; resolve commercial access/export rights, hosting costs, package/signing setup, and actual phone OS.
2. **Local journal:** navigation, meals/food/drinks, feelings, activity/steps, weight, edits, drafts, totals, units.
3. **Convenience and sources:** favorites, saved meals, recipes, three adapters, gateway, barcode, parsing review, offline behavior.
4. **Portability and trends:** export formats, complete backup/restore, attachments, history filters, charts with coverage.
5. **Device validation and handoff:** S25 Ultra usability, accessibility, update/restore tests, signed release APK and instructions.

Milestones describe implementation order, not reduced release scope. No hard completion dates or cost estimate are set by this specification. If provider access is delayed, report which capability remains incomplete rather than declaring the whole project done.

## 4. Acceptance scenarios

Each row defines observable behavior to verify during development. These are planned checks, not tests already run.

| ID | Scenario | Required result |
| --- | --- | --- |
| A01 | First launch without account or goals | User reaches Today and can log a manual meal |
| A02 | Save a food with only a name/time | Entry persists; nutrition is unknown; totals show partial coverage |
| A03 | Log measured 150 g against fixture 200 kcal/100 g | Exactly 300 kcal before display rounding; basis exported |
| A04 | Scan a supported product | Review correct product/source/portion before saving; one lookup per captured code |
| A05 | Deny camera or scan missing barcode | Manual barcode/search/custom entry stays available; draft preserved |
| A06 | Describe a mixed meal with a coffee | Review parsed foods and drink; edit amounts; no double counting |
| A07 | Parser guesses an amount | Estimated remains visible after user confirms the food identity |
| A08 | Parser times out, quota expires, or account lacks access | Clear scoped error; local logging continues; no silent billed retries |
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
| A31 | Exercise every selected provider with approved access | Actual search/barcode/parser calls verified, mappings and costs documented |

Proposed performance targets: local entry save feedback within 300 ms and interactive cold start within 2 seconds on the target phone, validated using a documented dataset of at least one year of representative entries. Network work never blocks local journal access. Optimize only against measured behavior.

## 5. Eventual delivery checklist

The development phase must supply and verify these named deliverables; none are required to exist during this specification-only phase:

- `releases/Newleaf-<version>.apk`: signed release APK installable on the owner's S25 Ultra.
- `releases/INSTALL.md`: install, permissions, gateway setup, update, backup/restore, and uninstall/data-clear instructions.
- `releases/CHANGELOG.md`: version, included features, known limitations, and resolved provider choices.
- `releases/CHECKSUMS.txt`: artifact integrity values.
- Application and gateway source, build instructions, dependency/license notices, and a secrets-free configuration example.
- `releases/VALIDATION.md`: acceptance results, actual device/OS, provider access results, and export/restore evidence.
- Sample fictional-data exports in the three formats plus an attachment backup example; never ship the owner's real journal as samples.

The owner must be able to install updates with the same signing identity. Agree signing-key custody and secure recovery before the first release. Never include private signing keys in ordinary Project files or chat attachments.

An APK is the proposed delivery format; a Play Store release is outside the current scope. Development authorization, paid-service choices, and gateway deployment remain separate future actions.

## 6. Specification completeness checklist

At this phase the required outputs are this folder, README, and five linked specification documents. Check each file exists, is readable, and has valid internal links. Confirm source statements have official references, unresolved assumptions are labeled, and no application/build files were created. This phase is complete when those documents are present and reviewable; it is not evidence that the app exists.
