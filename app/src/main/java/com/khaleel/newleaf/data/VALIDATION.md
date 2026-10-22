# Data-layer validation

Current data implementation: Newleaf 0.1.5. Documentation reviewed October 22, 2026. [Current release validation](../../../../../../../../releases/VALIDATION.md) is the authoritative integrated evidence; historical engineer checkpoints in Git history are superseded by that record.

## Recorded evidence

The current integrated app results are in the linked release validation; the 0.1.4 historical record reports 126 JVM tests and 38 emulator tests. Data-related coverage includes portion scaling/unknowns, recipe/composition snapshots, null-versus-zero coverage, time zones/DST, atomic edits and Undo, shared Room ownership, drafts, credential isolation, export/restore conflicts/recovery, Unicode/CSV escaping, attachment integrity and the independent OFF subset. Package definitions/source snapshots round-trip through backup/restore. The 0.1.5 revision leaves this data contract unchanged; its own executed checks are recorded separately.

## Reproduction

From the repository root, use the Gradle commands in [Build](../../../../../../../../BUILD.md). Standard JVM checks: `./gradlew :app:testDebugUnitTest`; runtime checks: `./gradlew :app:connectedDebugAndroidTest` on an explicitly selected development emulator. Release acceptance uses the mapped signed APK/test-runner procedure in Build. Do not install a debug identity over the personal release.

The optional isolated runner at app/src/test/java/com/khaleel/newleaf/data/run-isolated-tests.py uses cached Gradle dependencies and supports `--samples app/src/test/resources/data/samples`. It covers pure data/export code and cannot replace Room/Keystore Android runtime checks.

## Samples and limits

[Sample fixtures](../../../../../../test/resources/data/samples/README.md) contain invented nutrition and placeholder PNG bytes, not real journal data. Release samples are verified by scripts/package.py. JSON is canonical; CSV views alone are not a restore format.

Archives are generated in memory, bounded to 256 MiB expanded data and 64 MiB per member. Room schema is 1; future changes need explicit migrations and migration tests, with no destructive fallback. Physical S25 Ultra persistence/update/keyboard/accessibility and one-year startup/save performance remain phone acceptance work. Producer identity and historical export metadata are documented in the [data contract](../../../../../../../../docs/DATA_AND_EXPORTS.md).
