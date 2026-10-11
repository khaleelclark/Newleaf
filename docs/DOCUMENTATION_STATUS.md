# Documentation status and maintenance

Reviewed October 11, 2026 against the Newleaf 0.1.4/code6 source and existing release files. This is a documentation-only revision; the signed APK, database schema, behavior and release test results are unchanged.

## Scope

The README index is the current documentation entry point. Installation/daily use, product behavior, experience, data/export schema, nutrition contracts, optional gateway, architecture/acceptance, build/signing, third-party notices, samples and release records are covered. Historical VALIDATION-0.1.x files retain their version-specific evidence and are explicitly labeled historical. Old specification/handoff packages in Project files describe their original versions; the newly published documentation/source bundles supersede them for current use.

## Corrections

Removed proposal-only status and authorization language, reconciled direct Food/Drinks/Movement/Weight actions and More, made scan/category/visible autofill/package percentages consistent, documented actual unified Entry/JSON/CSV structures, corrected build/signing prerequisites and setup/recovery paths, and replaced stale engineer handoff notes with concise current API/validation references. Historical evidence is separate from current phone acceptance.

## Audit checks

28 Markdown files were read/checked; all 70 local Markdown links/fragments resolve and code fences are balanced. The 21 distinct external Markdown references in current documents returned HTTP200 in a read-only reference check; historical release URLs remain evidence of their original date. Existing release JVM XML confirms 126 tests, zero failures/errors/skips. This audit does not rerun the APK or certify every external API. Archive/member/checksum verification accompanies publication.

## Known application/acceptance limits

- The current APK emits legacy manifest.app_version=0.1.0 and spec_version=0.1; schema_version=1.0 remains the portable-format identifier. This documentation update records the producer limitation and does not repair application code.
- Physical S25 Ultra camera/network/Samsung keyboard/TalkBack and one-year startup/save performance remain unverified. Emulator/font/theme checks have the exact scope stated in release validation.
- Open Food Facts coverage and supplied package/serving/nutrition fields vary. Owner contact identification is still a broader-distribution task.
- The optional gateway has no production deployment; authenticated TLS/proxy operation remains deployment acceptance work.

## Maintenance procedure

1. Read versionName/versionCode and final source behavior before changing docs. Use INSTALL for user instructions, developer APIs for implementation detail and VALIDATION for executed evidence.
2. Update all affected documents together when navigation, portion behavior, schemas, permissions, credentials or export contracts change. Do not describe desired features as implemented.
3. Preserve historical release results; record fresh commands/results separately. Never claim physical-device or live-provider validation from fixture replays.
4. Check every local Markdown link and fragment, named handoff file and archive member. Keep fictional examples distinct from personal data. Regenerate a sample only when its format/content needs change.
5. After documentation-only changes, rerun scripts/package.py, verify source ZIP integrity and CHECKSUMS, and publish the refreshed documentation/source files. Keep the APK and its exact mapping/signature. Rebuild/retest only for an application change.
6. Commit/push the reviewed documents to the private repository. Publish each final downloadable file through Project files; previous immutable artifacts remain historical.
