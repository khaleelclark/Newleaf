# Repository review — October 22, 2026

A focused pass of the current Newleaf repository using the owner's
[Ponytail and UX references](UX_PRINCIPLES.md). Safe, bounded corrections are included in
0.1.5. This is a maintenance review, not a security certification or a claim that every
physical-phone interaction has been tested.

## Findings, ordered by impact

| Priority | Finding and user impact | Outcome |
| --- | --- | --- |
| High | An unreadable encrypted credential can prevent opening the whole local journal. DeviceCredentials.read throws for malformed ciphertext or an unavailable Keystore key; JournalRepository.readJournal reads both slots while loading every journal. The loading error screen has no credential-recovery route. Existing meals are still in Room, but the user cannot reach offline logging/export through the app. | **Open.** Source-confirmed failure path; no destructive credential experiment on the owner's phone. Isolate vault failures from journal loading and add an explicit re-entry/recovery path that preserves encrypted values until the user chooses how to recover. Do not recommend uninstalling or clearing app data. |
| Medium | A valid restored `volumeUnit="mL"` used metric numeric conversion in Trends but an imperial unit label/selection in several UI locations. This could label 100 mL as 100 US fl oz and make Settings show the wrong preferred unit. | **Fixed.** Treat accepted ml/mL aliases consistently in Today, entry labels, Trends, preferences and water-goal labels. Regression restores an actual export with mL settings and checks chart/table units plus Settings selection. |
| Medium | Export manifest and outbound nutrition User-Agent hardcoded 0.1.0, making later builds identify themselves incorrectly during analysis or provider troubleshooting. | **Fixed.** Both use BuildConfig.VERSION_NAME. Backup manifest and fixture-request assertions verify the build identity; schema_version remains 1.0. Historical files are not rewritten. |
| Medium | Shared visual sections and disclosure/dropdown states lacked explicit accessibility semantics; error feedback was visible but not declared as a live region. A required scan category left Add disabled without explaining the next action. | **Fixed.** Heading/expansion semantics, a disappearing category hint and one reused polite error component across entry, scan, Settings and export/restore. Regression checks retain draft values, category enforcement and failed-save retry. Actual TalkBack speech/navigation remains a target-phone check. |
| Low | With the app explicitly light and the system in night mode, the large-text food sheet shows light status icons over a light background. Logging controls remain usable, but system status text has poor contrast in the captured configuration. | **Open.** Observed in the emulator screenshot. Review dialog-window system-bar appearance separately from the activity theme and verify on the phone. |
| Low | The lightweight export preview duplicates selection/context logic and estimates size from selected top-level photos. It can miss recipe-linked context and nested/contextual attachments, so preview counts/size can differ from the fully prepared file. | **Open.** The actual export generator resolves links, validates contents and checks archive limits. The preview is approximate. A future focused fix should share the generator's selection plan without reading all photo bytes on every UI change. Documentation now describes the actual preview and its limit. |

Credential evidence: [vault reads](../app/src/main/java/com/khaleel/newleaf/data/DeviceCredentials.kt),
[journal loading](../app/src/main/java/com/khaleel/newleaf/data/JournalRepository.kt),
and [loading UI](../app/src/main/java/com/khaleel/newleaf/ui/NewleafApp.kt).
Preview evidence: [Portability](../app/src/main/java/com/khaleel/newleaf/ui/Portability.kt)
and [canonical export preparation](../app/src/main/java/com/khaleel/newleaf/data/JournalExports.kt).
Open findings are not included in the completed-fix claim.

## Review coverage

Traced Today/direct entry and scan → candidate normalization → reviewed portion → explicit
save → Room publication; category, amount, unit and unknown-nutrient behavior; duplicate
and stale-request guards; recipe/drink contribution rules; undo and daily totals; credential
separation; JSON/CSV/Markdown/attachment backup and restore boundaries; provider timeout,
response bounds, cancellation, redirects and sanitized errors; optional gateway authentication,
request/concurrency/quota bounds; reminders, photo sharing, release signing and packaging.

The app already has reusable UI components, a single journal transaction owner, explicit
source snapshots, bounded archive validation and fixture-based tests. These structures
support the requested behavior; this pass keeps them rather than introducing replacement
layers, new dependencies or a wholesale visual rewrite. Newleaf's direct food/drink/movement
flow and visible populated nutrition are retained.

## Verification and limits

Executed commands, test totals, lint results, signed-update identity, screenshots and artifact
checks are recorded in [release validation](../releases/VALIDATION.md). Tests use fictional
local data and provider fixtures; gateway tests use a fake transport. No live USDA/OFF
request, private credential inspection, paid service, hosted deployment or phone validation
is inferred from those results.

Physical S25 Ultra barcode/network/catalog behavior, Samsung keyboard, TalkBack and
one-year startup/save performance remain unverified. The credential failure/recovery flow
above remains a material limitation; this review does not declare the app ready for broader
production distribution. Owner contact identification for broader OFF use and authenticated
TLS/proxy acceptance for a deployed gateway also remain separate work.
