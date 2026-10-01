# Data-layer validation — October 1, 2026

Implemented files in this package: Models.kt, EventTimes.kt, Units.kt, Nutrition.kt, Summaries.kt, Recipes.kt, JournalValidation.kt, JournalCodec.kt, JournalDatabase.kt, JournalStorage.kt, PhotoReferences.kt, OpenFoodFactsExports.kt, DeviceCredentials.kt, JournalRepository.kt, ExportModels.kt and JournalExports.kt. API.md documents the integration contract. Room schema v1 is generated at app/schemas/com.khaleel.newleaf.data.JournalDatabase/1.json.

## Executed checks

- `python3 app/src/test/java/com/khaleel/newleaf/data/run-isolated-tests.py --samples app/src/test/resources/data/samples`: **36 JUnit tests passed**. Pure Kotlin data tests run independently of UI and nutrition tests.
- `./gradlew :app:compileDebugKotlin :app:assembleDebugAndroidTest`: **passed**; real Room KSP generation, app compilation and instrumentation APK assembly succeeded.
- Full `./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest`: did not complete because NutritionClientTest referenced unresolved com.sun.net.httpserver.HttpServer in that test environment. No data-source compilation error was reported. The build owner/nutrition owner has been informed.
- `adb devices`: no connected test device at validation time; Android tests compiled but were not executed by this engineer.

DomainTest and ExportsTest cover exact portion scaling, unknown household conversions, mass/volume separation, null versus zero, coverage, food/drink counting, recipe versions and deep copies, partial recipe coverage, weight means/trends, observation denominators, DST gaps/overlaps, cross-midnight activity, CSV escaping/Unicode/multiline text, context-only selection, notes exclusion, archive/photo digests, unsupported schemas, invalid references, summary recalculation, unknown fields, credential exclusion, and source restrictions.

PersistenceTest adds device checks for atomic invalid-batch rollback, delete/unlink/Undo, merge conflict decisions, recovery-before-replace, private photo restoration and corrupt-photo rejection. The app owner's JournalDeviceTest separately checks Room restart/drafts/step replacement and Keystore persistence. Run these using `./gradlew :app:connectedDebugAndroidTest` once a development device/emulator is available.

## Verified fictional deliverables

All six files are present under app/src/test/resources/data/samples: README.md, journal.json, journal.md, journal-csv.zip, journal-backup.zip and one-day-context.json. The generator validates the full backup after writing it. Every ZIP includes journal.json, all 18 CSV tables, report.md, DATA_DICTIONARY.md and integrity.json. The backup includes original placeholder PNG bytes and SHA-256 attachment metadata. No owner journal, gateway token, or transient draft is used.

## Remaining validation and integration obligations

Android runtime execution and actual S25 Ultra persistence/update testing remain pending. The one-year dataset/device save/startup performance targets are not measured. Future database changes need explicit migration tests; no destructive fallback is enabled. UI must use deleteWithDependents' entire returned snapshot set for Undo and call purgeUnreferencedPhotos after its Undo window. Source access/retention/export approvals are owned by nutrition/product integration; restricted sources fail export rather than silently drop nutrition. Export archive generation is bounded and in memory (256 MiB expanded archive, 64 MiB per member).

Review follow-up: added partial drink composition and exported coverage round-trip regressions, estimated-but-complete recipe regression, and recursive recipe/composition source-rights regression. Known partial subtotals retain known status; only actual estimated contributors propagate estimated status. Pure data suite re-run: 36 passed; fictional samples regenerated and full backup verified. Parent owns the next integrated Gradle/device checks.

Follow-up QA: parent reported 6/7 API36 connected tests failing with SQLiteDatabaseLockedException at Room trigger synchronization. Those failures are retained as evidence; they are not treated as passed. Added process-wide Room storage/executor/mutex and safe handle closure, plus concurrent repository regression. Parent owns the connected rerun and all Gradle commands; no claim of a runtime fix is made until that rerun passes. Added draft/snapshot/active-photo cleanup protection and OFF subset/privacy/reference-scaling regressions. New pure data suite passes 39 tests; fictional samples regenerated and validated. OFF archives have an independently licensed subset/notice when applicable, with no private journal content in that subset.


Atomic editor follow-up: `JournalEdits.kt` plans upsert/delete/unlink/daily replacement, and repository `applyEdits(upserts, deleteIds): List<Entry>` commits it through one existing mutate/Room transaction. API.md documents Undo snapshots and deletion of newly created IDs. `JournalCodec.same` now skips JSON comparison when the same immutable entry object is retained, avoiding serialization of unchanged records during persistence. HEIF exports preserve `.heif`, original bytes and `image/heif`.

Executed for this change: `python3 app/src/test/java/com/khaleel/newleaf/data/run-isolated-tests.py` **passed all 45 JUnit tests**, including five atomic-edit regressions and HEIF archive integrity/MIME coverage. Added two device regressions for atomic meal save/Undo across handles and invalid-edit rollback/reopen; these new tests have not been run by this engineer. No Gradle commands were started. Manager earlier reported all 11 prior API36 device tests passing after shared-storage fix, with a 2190-record single-save measurement of 2118 ms before this identity optimization. Parent owns integrated Gradle/device rerun and performance remeasurement; this change makes no new device timing claim. All named source/API/test deliverables for this follow-up were checked in place.
