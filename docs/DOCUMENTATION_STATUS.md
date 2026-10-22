# Documentation status and maintenance

Updated October 22, 2026 for Newleaf 0.1.5/code7. The current README is the documentation
entry point. This revision adopts the owner's Ponytail and UX reference, updates affected
application/release guidance and packages the new signed APK with its source.

## Current scope

[Repository guidance](../AGENTS.md) applies Ponytail to future coding work.
[UX principles](UX_PRINCIPLES.md) identify the exact upstream snapshot, licensed compact
rules, reviewed book sections, Newleaf-specific decisions and verification boundaries.
The source package includes the rules and license; the copyrighted PDF is not redistributed.

The shared UI now exposes section headings and expanded/collapsed states, uses polite
accessibility announcements for entry/barcode errors, and explains the required scan
category. Direct logging, weight, nutrition/portion calculations and persistence retain their contracts. Export/request producer
identity now follows the build version; metric unit aliases display consistently. Product/user/build/data/provider documents identify the current
release; named historical behavior/evidence sections retain their original versions.

## Audit and evidence

Executed build/test, documentation-link, archive/member/checksum and signature results
belong in [release validation](../releases/VALIDATION.md). Historical 0.1.4 evidence,
including its October 11 documentation and October 12 APK-inclusion follow-ups, is
preserved in [VALIDATION-0.1.4.md](../releases/VALIDATION-0.1.4.md). Previous immutable
Project attachments remain historical; current-release artifacts supersede them.

## Known application/acceptance limits

The [repository review](REPOSITORY_REVIEW.md) records open findings: unreadable vault
credentials can block local journal loading; the export preview can omit some linked
context/attachment size; and light-sheet status icons need contrast review with system night mode. Fixed findings and evidence are recorded separately.

- Export manifest.app_version now identifies the installed build. Historical 0.1.4 and earlier
  files may still say 0.1.0. spec_version=0.1 is a legacy product-spec marker; schema_version=1.0
  remains the portable-format identifier.
- Physical S25 Ultra camera/network/Samsung keyboard/TalkBack and one-year startup/save
  performance remain unverified. Semantics assertions do not certify spoken TalkBack output.
- Open Food Facts coverage and supplied package/serving/nutrition fields vary. Owner contact
  identification remains a broader-distribution task.
- The optional gateway has no production deployment. Its historical provider/service
  evidence is separate from current app fixture tests.

## Maintenance procedure

1. Read versionName/versionCode and actual source behavior. Use INSTALL for user instructions,
   developer APIs for implementation detail and VALIDATION for executed evidence.
2. Apply AGENTS.md and UX_PRINCIPLES.md. Update affected documentation together when flow,
   schema, permissions, credentials or export behavior changes.
3. Preserve historical release results. Record fresh commands/results separately; never
   infer physical-device or live-provider validation from fixture replays.
4. Check local links/fragments, named handoff files and archive members. Keep fictional
   examples separate from personal data.
5. Run scripts/package.py after final documentation is ready. Verify ZIP integrity,
   required members and CHECKSUMS. Build/test for code changes; documentation-only
   maintenance must not imply a new APK test run.
6. Include the exact signed current APK in releases/ and Project files. Update .gitignore
   and README/INSTALL download links. Commit/push the reviewed source/docs/current APK
   to the existing private repository. Never package secrets, signing material or journal data.
