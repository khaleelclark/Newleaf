# Newleaf

**A fresh start, one day at a time.**

Newleaf is a personal Android journal for food, drinks, movement, body weight and feelings, with local storage and complete exports for later analysis.

## Current release

**0.1.5 · version code 7 · package `com.khaleel.newleaf`**. Android 10/API 29 or newer; compiled and targeted against API 36. The primary device is the Samsung Galaxy S25 Ultra. Distribution is a signed APK through Project files and this private repository; there is no Play Store listing.

[Download Newleaf 0.1.5 APK](https://github.com/khaleelclark/Newleaf/raw/refs/heads/main/releases/Newleaf-0.1.5.apk) (GitHub sign-in required). Install over your existing app without uninstalling. The checksum and public signing identity are recorded in `releases/CHECKSUMS.txt` and `releases/SIGNATURE.txt`.

Today opens food, drink, activity, steps and weight forms directly. Scan opens packaged-product review with a required category, visible populated nutrition and one Add to journal action. Reported whole-package measurements support fractions and percentages; missing nutrition stays unknown. Manual logging and exports work offline. The 0.1.5 update explains the required scan category and exposes section headings, expansion states and form-error announcements to accessibility services.

## Documentation

| Start here | Contents |
| --- | --- |
| [Install and use](releases/INSTALL.md) | Phone installation, daily use, half-bottle logging, source setup, export and recovery |
| [Changelog](releases/CHANGELOG.md) | Features by release |
| [Release validation](releases/VALIDATION.md) | Recorded build/test evidence and remaining phone checks |
| [Product behavior](docs/PRODUCT_SPEC.md) | Current scope, defaults and deferred capabilities |
| [Repository review](docs/REPOSITORY_REVIEW.md) | Fixed issues, remaining findings and verification limits |
| [Coding and UX principles](docs/UX_PRINCIPLES.md) | Ponytail guidance and the owner-supplied UX reference applied to Newleaf |
| [Experience](docs/EXPERIENCE.md) | Screens, interaction, visual system and accessibility targets |
| [Data and exports](docs/DATA_AND_EXPORTS.md) | Implemented schema, units, calculations, file formats and restore |
| [Nutrition sources](docs/NUTRITION_INTEGRATIONS.md) | Free USDA/OFF access, review, privacy and source policy |
| [Architecture and acceptance](docs/DELIVERY_AND_ACCEPTANCE.md) | Components, acceptance scenarios and release checklist |
| [Build](BUILD.md) | Toolchain, debug/release builds, signing and packaging |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | Component and nutrition notices |
| [Documentation maintenance](docs/DOCUMENTATION_STATUS.md) | Audit scope, known limits and update procedure |

Developer contracts: [data API](app/src/main/java/com/khaleel/newleaf/data/API.md), [nutrition API](app/src/main/java/com/khaleel/newleaf/nutrition/API.md), and [optional gateway](gateway/README.md). [Fictional exports](releases/samples/README.md) are separate samples and never seed the journal.

## Nutrition and privacy

Open Food Facts barcode lookup needs no personal account or key. USDA food search and bounded Food list suggestions need a free data.gov key entered in Settings → Nutrition sources → Save preferences. The key is protected with Android Keystore and excluded from exports/backups. An authenticated HTTPS gateway is optional. Nutritionix and paid fallbacks are disabled.

The journal stays on the phone. Explicit online lookup sends food queries or a barcode to the relevant provider. No AI upload, analytics, diary account, cloud journal sync, automatic steps/health-account import or fasting features are included. Export destinations are chosen by the user; full backups include original local photos but exclude credentials and transient drafts. Uninstalling or clearing app data deletes local records, so save a full backup elsewhere first.

## Evidence and limits

Current build/test evidence and its scope are recorded in the [0.1.5 validation record](releases/VALIDATION.md). Physical S25 Ultra camera/network/keyboard/TalkBack acceptance and target-phone performance remain open. The optional gateway has separate local test evidence and no production deployment. See the validation record for exact boundaries.

## Repository and maintenance

The owner's private repository is `khaleelclark/Newleaf`. Keep source, documentation and the current signed release APK together. The APK is tracked in `releases/` and also published in Project files. Older release APKs and development builds remain excluded from version control. Never commit real journal data, credentials, signing keys/passwords, local SDK configuration or build caches. Signing-key custody and recovery are described in [Build](BUILD.md).
