# Newleaf

**A fresh start, one day at a time.**

Newleaf is a personal Android journal for meals, drinks, activity, body weight, and how you feel. It pairs fast logging with detailed, portable data for later analysis.

## Project status

Newleaf 0.1.3 is a signed Android APK with a source archive, installation instructions and fictional export examples. Daily Food, Drinks and Movement trackers open forms directly, with a visible barcode Scan action. Food starts with a short description; numeric details are optional. [Release validation](releases/VALIDATION.md) records current checks and physical-phone limits.

Project location: `/home/phoenix/khaleel/Newleaf`.

Primary device: Samsung Galaxy S25 Ultra. Handoff files are in `releases/`; see [installation](releases/INSTALL.md) and [build instructions](BUILD.md).

## Read the specification

| Document | Contents |
| --- | --- |
| [Product specification](docs/PRODUCT_SPEC.md) | Scope, features, daily behavior, and defaults |
| [Experience and design](docs/EXPERIENCE.md) | Navigation, screen layouts, logging flows, accessibility, visual direction |
| [Data and exports](docs/DATA_AND_EXPORTS.md) | Records, units, nutrition calculations, export formats, backup and restore |
| [Nutrition integrations](docs/NUTRITION_INTEGRATIONS.md) | Free USDA/OFF access, assisted descriptions, privacy, quotas, licensing |
| [Delivery and acceptance](docs/DELIVERY_AND_ACCEPTANCE.md) | Proposed architecture, implementation sequence, acceptance scenarios, eventual handoff |

The product document sets scope; the other documents define the behavior needed to deliver it. If a later revision changes scope, update the linked documents together.

## Settled requirements

- Name: Newleaf, expressing a fresh start and lifestyle change.
- Fast meal, food, drink, and activity logging; feelings after meals; body weight and flexible portions.
- Food search, barcode scanning, meal descriptions, saved meals, and reusable recipes.
- Free-only nutrition: USDA FoodData Central search/details and assisted descriptions, plus Open Food Facts barcode lookup.
- A clean custom visual identity rather than the standard Material appearance.
- Complete exports for spreadsheets and AI analysis, with original context and data quality information.
- Android delivery for the owner's S25 Ultra.
- Application development authorized after completion of the specification.

## Proposed defaults, open for revision

- Native Android in Kotlin with a custom Compose design system and local storage.
- Single user, no mandatory diary account, local journal by default.
- US customary display units initially: lb, US fl oz, mi; g also available for food weighing.
- Today, History, and Trends as primary navigation; settings and library as secondary destinations.
- No default calorie target, weight target, reminders, fasting features, or automatic AI analysis.
- Local meal photos are included; automatic photo-based nutrition recognition is a later option.

## Remaining external setup

Open Food Facts is free and needs no user account or key for product lookup. USDA is free with a data.gov API key; enter your personal key in Settings → Nutrition sources. Newleaf protects it locally and excludes it from exports/backups. A hosted gateway is optional, so online nutrition does not require paid hosting.

The owner's USDA key passed two read-only official API calls, for search and details. The key is not embedded in the APK. Final free-only emulator validation is complete; actual S25 Ultra installation/usability remains to be checked. Package identifier is `com.khaleel.newleaf`; signing-key custody is documented in the installation instructions.

Nutritionix's public free tier was withdrawn, so that provider is disabled. Describe meal uses bounded, reviewed USDA matches rather than a paid natural-language service. No subscription, paid account, or public gateway deployment is part of this release.

## Version control

Commit focused checkpoints and push regularly to the owner's `khaleelclark` GitHub account. Use the verified GitHub identity and SSH access. Never commit provider/device credentials, local SDK configuration, signing keys/passwords, generated build caches, or real journal data. Release binaries are delivered separately from source history. Remote creation/push status is recorded in release validation.
