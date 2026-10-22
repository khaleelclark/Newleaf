# Newleaf 0.1.5 — validation record

October 22, 2026. Repository pass using the owner's Ponytail and UX references.
Previous release evidence is preserved in [VALIDATION-0.1.4.md](VALIDATION-0.1.4.md).
See the [repository review](../docs/REPOSITORY_REVIEW.md) for fixed and open findings,
and [principles](../docs/UX_PRINCIPLES.md) for exact reference scope and provenance.

## Implemented changes

Repository AGENTS.md points to a pinned Ponytail compact ruleset with its complete MIT
license, and an original Newleaf application of reviewed sections from the owner-supplied
UX book. These references travel in the source package. No new Android dependency,
agent hook, analytics, or copy of the copyrighted PDF is included.

Shared sections expose heading semantics; details/selectors expose expansion state.
Entry, barcode, Settings and export/restore errors use one polite live-region component.
Scan explains that a category is needed before Add; selecting one removes the hint.
Existing required-category, unknown nutrition, fraction/percentage conversion, explicit
commit, busy lock, cancellation and save-error retry contracts remain in place.

Accepted ml/mL preferences now receive consistent metric labels in Today, entry details,
Trends, preferences and goal fields. A restored mL preference is exercised through real
app navigation and an actual persisted water entry while retaining existing records.
Export manifest.app_version and outbound nutrition User-Agent now use the build version.
Schema_version remains 1.0; spec_version remains the independent legacy marker 0.1.
Regenerated fictional export samples identify producer 0.1.5 and pass round-trip checks.

## Build and automated evidence

- Final application source: scripts/release.sh passed in 3m28s, building the minified
  release and mapped instrumentation APK and running release unit/lint checks
  (`/tmp/newleaf-015-release-final.log`). Subsequent changes were test-only. The final
  test-package build passed in 45s (`/tmp/newleaf-015-release-final-scope.log`), with the
  release app and JVM tasks up to date. The signed application bytes are unchanged
  across those test refinements.
- **126 JVM tests passed**, zero failures/errors/skips, across seven XML suites in
  app/build/test-results/testReleaseUnitTest. Backup producer-version and fixture
  User-Agent assertions cover the repaired metadata.
- Lint: **zero errors, 22 warnings** in app/build/reports/lint-results-release.xml.
  Existing compiler warnings concern a deprecated walk icon and a nullable test-only
  Java platform type; these are not silently presented as a warning-free build.
- Final mapped, signed-release instrumentation: **41 passed**, zero failures, 48.707s
  (`/tmp/newleaf-015-device-final-scope.log`). New regressions exercise heading and
  expansion semantics, disclosure input retention, disabled selection, restored metric
  labels/preferences, required-category help and announced failed-save retry.
- A separate font-scale1.6 repeat of name-only food and half-bottle percentage save
  passed **two tests** in 17.699s (`/tmp/newleaf-015-large-dark.log`). These are repeat
  cases, not additional unique tests in the 41 total.
- Optional gateway: **20 local tests passed** in 0.617s
  (`python3 -m unittest discover -s gateway/tests -v`, `/tmp/newleaf-015-gateway.log`).
  Source is unchanged; fake transports and local HTTP tests do not validate deployment.

Intermediate runner attempts exposed ambiguous text selectors, release-optimized interfaces
called directly by new tests, and an interaction between a temporary replacement journal
and normal old-photo cleanup. Tests now target actual dialogs/popups/editable fields,
reuse shared Panel/app navigation, and preserve existing records during unit-preference
checks. The emulator's original 25-record/one-photo fictional baseline was restored through
Newleaf's file-picker/replace flow; a read-only check confirmed the records and photo file.
The missing 68-byte fixture was recovered from the local recovery archive with its SHA-256
verified before restoration. Intermediate failed runs are not acceptance evidence.

## Signing and install

Owned Newleaf_API36 emulator, Android16/API36 x86_64, serial emulator-5582. The unrelated
Household emulator was not installed to or tested. Newleaf installed over 0.1.4 with
adb install -r; no uninstall/data-clear was used. Installed version is 0.1.5/code7,
package com.khaleel.newleaf, minSdk29/targetSdk36. No database/schema migration.

Apksigner verification passed. Application and mapped test package match the established
release certificate SHA-256:
277efd44109c70d79c1c64c9c20c1ed1a3f1ecf1f5b082130059f762f1262d8d.
Application SHA-256:
41ca9f0e56a4649c32503485e6f948aed9b42bc55579d9623e8abb35c9e992ea.
Public signing details and final deliverable digests are in SIGNATURE.txt and CHECKSUMS.txt.

## Visual evidence and boundaries

Actual captures are in /tmp/newleaf-015-evidence/: home-light, food-keyboard-light,
half-bottle-light, home-large-light-system-night, food-keyboard-large-light-system-night
and half-bottle-large-light-system-night PNGs. Home, large food and large half-bottle
captures were inspected. The half-bottle review shows 50 percent, 120 kcal, 30g sugar,
scrollable content, and Add/Cancel visible above the numeric keyboard. Long entry text
and identifiers are fictional test fixtures. The large food capture shows Save visible;
it does not show a food text keyboard, so no broader keyboard claim follows from it.

System night mode was requested for the font-scale1.6 repeat, but the journal's explicit
light app preference remained in force. The numeric keyboard is dark; these images do
not establish full dark-app visual acceptance. Light icons over the light food-sheet
status area have low contrast in this configuration and are an open review finding.
Original emulator font/night preferences were restored after the repeat. The shared
selector test separately composes NewleafTheme(dark), testing semantics rather than
full-app visual appearance.

Physical S25 Ultra camera/network/catalog coverage, Samsung keyboard, spoken TalkBack
and one-year startup/save performance remain unverified. No live USDA/OFF request,
provider credential inspection, paid service or gateway deployment was performed.
Unreadable encrypted credentials can still block journal loading; approximate export
preview counts/sizes and the status-area contrast issue remain open in the review.
This is a verified personal APK update and a review report, not broad production certification.

## Delivery verification

scripts/package.py verifies the signed APK, install/changelog/validation, signing/mapping,
source/build material, repository guidance, review/principles, pinned Ponytail snapshot and
license, optional gateway and fictional samples before producing the source ZIP/checksums.
The source archive excludes private configuration, signing material, build caches and the PDF.
The current signed APK is tracked in the private repository and published through Project
files alongside the source and review documents. Historical Project artifacts remain immutable.
