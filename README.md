# Newleaf

**A fresh start, one day at a time.**

Newleaf is a personal Android journal for meals, drinks, activity, body weight, and how you feel. It pairs fast logging with detailed, portable data for later analysis.

## Project status

The Android implementation and signed release candidate are present. A specialist team built the Android experience, journal/export layer, and three nutrition adapters. [Release validation](releases/VALIDATION.md) records actual tests and remaining provider/device checks; a compiled adapter does not mean a hosted gateway or keyed account is provisioned.

Project location: `/home/phoenix/khaleel/Newleaf`.

Primary device: Samsung Galaxy S25 Ultra. Handoff files are in `releases/`; see [installation](releases/INSTALL.md) and [build instructions](BUILD.md).

## Read the specification

| Document | Contents |
| --- | --- |
| [Product specification](docs/PRODUCT_SPEC.md) | Scope, features, daily behavior, and defaults |
| [Experience and design](docs/EXPERIENCE.md) | Navigation, screen layouts, logging flows, accessibility, visual direction |
| [Data and exports](docs/DATA_AND_EXPORTS.md) | Records, units, nutrition calculations, export formats, backup and restore |
| [Nutrition integrations](docs/NUTRITION_INTEGRATIONS.md) | Three source categories, provider routing, costs, privacy, dependencies |
| [Delivery and acceptance](docs/DELIVERY_AND_ACCEPTANCE.md) | Proposed architecture, implementation sequence, acceptance scenarios, eventual handoff |

The product document sets scope; the other documents define the behavior needed to deliver it. If a later revision changes scope, update the linked documents together.

## Settled requirements

- Name: Newleaf, expressing a fresh start and lifestyle change.
- Fast meal, food, drink, and activity logging; feelings after meals; body weight and flexible portions.
- Food search, barcode scanning, meal descriptions, saved meals, and reusable recipes.
- All three nutrition source categories from the discussion: USDA FoodData Central, Open Food Facts, and a commercial natural-language service.
- A clean custom visual identity rather than the standard Material appearance.
- Complete exports for spreadsheets and AI analysis, with original context and data quality information.
- Android delivery for the owner's S25 Ultra.
- Application development authorized after completion of the specification.

## Proposed defaults, open for revision

- Third provider: Nutritionix; fatsecret remains an evaluated alternative, not a fourth mandatory integration. The earlier discussion grouped these two under one source category.
- Native Android in Kotlin with a custom Compose design system and local storage.
- Single user, no mandatory diary account, local journal by default.
- US customary display units initially: lb, US fl oz, mi; g also available for food weighing.
- Today, History, and Trends as primary navigation; settings and library as secondary destinations.
- No default calorie target, weight target, reminders, fasting features, or automatic AI analysis.
- Local meal photos are included; automatic photo-based nutrition recognition is a later option.

## Remaining external setup

These must be resolved before claiming all three live sources and physical-device acceptance:

1. Third-provider account access, pricing, and permission to retain nutrition in journal records, backups, and AI exports.
2. USDA API access and a configured gateway. Open Food Facts uses its public service with attribution and an independently licensed nutrition subset in exports.
3. Hosting choice and spending limit for a small nutrition gateway that protects provider credentials.
4. Actual S25 Ultra installation and usability. Package identifier is `com.khaleel.newleaf`; signing-key custody is documented in the installation instructions.

Development is authorized. Paid plans, external account registration, and public gateway deployment require the owner's concrete choices. No schedule or budget estimate is committed. See [build instructions](BUILD.md) and the eventual `releases/` handoff for installation and validation.

## Version control

Commit focused checkpoints and push regularly to the owner's `khaleelclark` GitHub account. Use the verified GitHub identity and SSH access. Never commit provider/device credentials, local SDK configuration, signing keys/passwords, generated build caches, or real journal data. Release binaries are delivered separately from source history. Remote creation/push status is recorded in release validation.
