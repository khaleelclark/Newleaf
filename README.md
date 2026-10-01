# Newleaf

**A fresh start, one day at a time.**

Newleaf is a personal Android journal for meals, drinks, activity, body weight, and how you feel. It pairs fast logging with detailed, portable data for later analysis.

## Project status

Implementation started October 1, 2026 following the owner's development instruction. A specialist team is building the native Android experience, journal/export layer, and nutrition integrations. The specification below remains the product contract; release validation will distinguish implemented behavior from unresolved provider access and actual-device checks.

Project location: `/home/phoenix/khaleel/Newleaf`.

Primary device: Samsung Galaxy S25 Ultra. Eventual delivery: an installable signed Android APK, source, and installation/update instructions.

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

## Dependencies to resolve before implementation

These do not prevent specification work. They must be resolved before claiming the full app is operational:

1. Third-provider account access, pricing, and permission to retain nutrition in journal records, backups, and AI exports.
2. Applicable Open Food Facts attribution and reuse requirements for the exact integration and export design.
3. Hosting choice and spending limit for a small nutrition gateway that protects provider credentials.
4. Actual Android version on the owner's phone, package identifier, signing-key custody, and distribution choice.
5. Visual review before implementation; proposed palette and layouts are not a final approved design.

Development is authorized. Paid plans, external account registration, and public gateway deployment require the owner's concrete choices. No schedule or budget estimate is committed. See [build instructions](BUILD.md) and the eventual `releases/` handoff for installation and validation.
