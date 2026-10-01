# Data, calculation, and export contract

Version 0.1 — conceptual schema. No database or executable schema has been created.

## 1. Record conventions

Every durable record has a stable ID, created/updated timestamps, and a revision. Logged observations carry event time separately from creation time. IDs survive export and restore; local draft IDs survive retries to prevent duplicate saves.

Store event instants in UTC plus the IANA time zone, local timestamp with offset, and local event date at logging. Changing the phone time zone does not regroup old entries automatically. Editing a date/time explicitly recomputes its instant and local date. Ambiguous daylight-saving times require an offset choice; nonexistent times require correction. Date-only steps and daily notes have a local date and zone without inventing a time of day.

Canonical numeric units: food mass g, drink volume mL, body mass kg, distance m, duration seconds, energy kcal. Retain original quantity/unit and conversion metadata. Numeric precision is retained internally; round only for display. All times use ISO 8601 in exports. A missing numeric value is null, never zero or an empty numeric string.

## 2. Conceptual entities

| Entity | Core fields and relationships |
| --- | --- |
| Settings | preferred units, locale/region, zone policy, appearance, optional goals/reminders |
| Meal | ID, name/category, event time, original meal-description text if used, notes, food-entry IDs, drink-entry IDs, photo IDs |
| FoodEntry | optional meal ID; name/brand/preparation; original portion; normalized mass if known; nutrient snapshot; source; notes/photos; optional recipe version |
| DrinkEntry | optional meal ID; name/type; original volume; normalized volume; water classification; composition; nutrient snapshot; source; notes |
| FeelingCheckIn | optional meal ID; observed time; entered time; tags; optional hunger/fullness/energy; notes |
| ActivityEntry | name/type; start and optional end; duration/distance; optional intensity; notes; manual source |
| DailySteps | unique local date; step count; zone; source; optional note |
| WeightEntry | event time; original weight/unit; canonical kg; context; notes |
| DailyContext | unique local date; notes; logging completeness unmarked/partial/complete |
| CustomFood | name/brand; explicit nutrition basis; nutrient values; portion definitions; barcode; revisions |
| Favorite | referenced food/drink/template; usual quantity; ordering; snapshot/version reference |
| SavedMeal | name; reusable food/drink components and their usual portions; versions |
| RecipeVersion | recipe ID/version; ingredient snapshots; serving yield; optional final cooked mass; computed nutrient totals; notes |
| Attachment | local ID; MIME type; filename; content digest; size; capture time; linked records |
| SourceReference | provider; food ID/barcode; retrieval time; attribution; basis; version; override details |

References may be absent when information is unknown. Commercial provider IDs are namespaced by provider; identical IDs from two services are not the same food. Raw provider payloads belong in a separate bounded cache only when retention is permitted, not in automatic AI exports.

## 3. Nutrients and evidence

Each nutrient value contains nutrient code, value, unit, basis, origin, and status. Status is known, estimated, or unknown. Origin distinguishes database reference, user-entered label, recipe calculation, and user override. A provider's zero is kept only when it represents an explicit supplied value rather than an omitted field/default placeholder.

Core codes: energy_kcal, protein_g, carbohydrate_g, fat_g, saturated_fat_g, fiber_g, sugar_g, sodium_mg. Additional nutrients use a canonical code/unit dictionary and retain their provider identifiers. Do not confuse salt with sodium or kcal with kJ; convert only using documented mappings and preserve the original values. New unmapped nutrients remain exportable with provider code/unit rather than disappearing.

Portion evidence is separate: measured, estimated, unspecified. Match review status is separate again: confirmed, unreviewed, unmatched. Confirmed food identity does not imply measured portion or complete nutrition.

Historical entries contain the nutrition actually used when saved, its source/basis, and overrides. Refreshing a catalog item, recipe, or favorite changes future suggestions, not existing journal entries. An explicit edit can replace an entry's snapshot and revision, visibly recalculating affected totals.

## 4. Portion rules

- Positive amounts for food/drinks/weight; nonnegative steps and activity distance/duration. Blank means unspecified, not zero. A name-only food can be saved without amount.
- Weight ounce is avoirdupois: 1 oz = 28.349523125 g. US fluid ounce: 1 US fl oz = 29.5735295625 mL. Pound: 1 lb = 0.45359237 kg. Mile: 1 mi = 1609.344 m.
- Label US fluid ounces explicitly in data and unit settings; an ambiguous imported “oz” is not assumed to be fluid volume.
- Cups, tablespoons, pieces, and serving names require food-specific definitions for mass or a usable nutrient-per-unit basis. A cup of oil and a cup of rice do not have the same mass. Never substitute a water-density conversion for an arbitrary drink.
- Per-100-g nutrition multiplied by consumed g / 100 gives consumed nutrition. Per-100-mL nutrition multiplied by consumed mL / 100 gives consumed nutrition. Per-serving values require the consumed serving count or a documented serving size.
- Do not apply portion scaling twice to a parser response that already describes the consumed amount. Every provider adapter must declare its basis before normalization.
- Recipes sum ingredient nutrition, then divide by serving yield; gram portions also require final recipe mass. A missing ingredient nutrient makes that nutrient's recipe total partial, even when other nutrients are complete.
- Calculated totals retain the scaling/basis used. Plausibility issues prompt review; they do not silently clamp, discard, or replace the user's input.

## 5. Counting and missing information

Food and drink entries are the authoritative nutrient contributors. Meal totals are derived views, never additional contributors. A drink linked to a meal still contributes once to the day's calories and once to drink volume. Its milk/sugar composition contributes within the drink total, not again as independent food entries.

For each aggregate nutrient, return known sum, total contributor count, known contributor count, unknown contributor count, estimated contributor count, and completeness. A missing sodium value does not make known calories disappear. Name-only foods remain contributors with unknown nutrients. Plain water may have explicitly defined zero nutrients.

Daily completeness is empty, partial, or complete for each nutrient among logged entries; this is different from the user's daily logging completeness flag. A “complete” calorie dataset can still omit an unlogged dinner. UI wording is “Known logged calories,” with a partial indicator where needed.

No-entry days have null intake/steps/weight in summaries. Actual measured zero or explicitly entered zero is zero. Day-level averages show their denominator; averaging intake excludes days with no entries and reports observed days and partial days. It does not describe that average as the person's actual average intake.

Daily weight uses the mean of that date's measurements. The seven-day trend averages available daily means, requiring at least three measured days, with count shown. Steps are a daily total; activity distance is separate. Activities crossing midnight are grouped by their start date and are not duplicated into both days.

## 6. Export scope and selection

User selects a single local date, inclusive date range, or all records. Selection uses preserved event local date, not export-time zone. Meals/food/drinks/weight/activity are selected by event date; steps/context by their stored local date; feelings by observed date.

Include linked context needed to interpret selected records: for example, a meal from the previous evening referenced by a selected morning feeling, or a later check-in for a selected meal. Mark these as context-only/outside the selected range. They do not inflate date-range totals. Export counts distinguish selected and contextual records.

Library and recipe options are independent. Recipe versions referenced by exported entries are included as context even if the full library is not selected. Original meal-description text is included with notes unless the user excludes it. Exclude deleted entries unless the user explicitly asks for a recoverable backup containing a temporary undo state; routine backups contain active data only.

## 7. Formats

### Structured JSON

Single UTF-8 file, schema_version `1.0`, with manifest, settings, meals, food_entries, drink_entries, feeling_checkins, activities, daily_steps, weight_entries, daily_context, custom_foods, favorites, saved_meals, recipe_versions, attachment_manifest, and daily_summaries. Arrays remain present when empty. Nutrients are code/value/unit/status objects; amounts preserve original and canonical units. Normal exports include current record revisions, not every edit-history version.

Manifest fields: export ID, generation time, app/spec schema versions, selected range and date policy, display-unit preferences, included/excluded categories, context counts, per-table counts, source attribution, attachment inclusion, and any restrictions. Unknown fields from a future schema must not be silently discarded during restore.

### CSV bundle

A ZIP contains UTF-8 tables with stable IDs and documented headers. Include the same canonical `journal.json` as the structured export so nested serving definitions, drink compositions, settings, source details, and library metadata remain fully portable alongside spreadsheet views:

| File | Required columns / behavior |
| --- | --- |
| meals.csv | ID, event UTC/local/zone/date, name/category, notes, context_only |
| food_entries.csv | ID, meal_id, event fields, name/brand/preparation, original quantity/unit, mass_g, portion evidence, source reference |
| drink_entries.csv | ID, meal_id, event fields, drink name/type, original volume/unit, volume_ml, counts_as_water, portion evidence, source reference |
| nutrients.csv | contributor type/ID, nutrient code, value, unit, status, origin, basis, source reference |
| feeling_checkins.csv | ID, meal_id, observed/entered times, ratings, notes, context_only |
| feeling_tags.csv | check-in ID, tag code, user label |
| activities.csv | ID, name/type, start/end, event date/zone, distance_m, duration_s, intensity, notes |
| daily_steps.csv | ID, local date/zone, steps, source, notes |
| weight_entries.csv | ID, event fields, original weight/unit, weight_kg, context, notes |
| daily_context.csv | ID, local date, notes, logging completeness |
| source_references.csv | ID, provider/food ID/barcode, retrieved time, basis, attribution, override metadata |
| library.csv | object type/ID/version/name, favorite/default portion metadata |
| recipe_ingredients.csv | recipe ID/version, ingredient ID, portion, source and snapshot reference |
| recipe_nutrients.csv | recipe ID/version, nutrient code/value/unit/coverage |
| recipe_yields.csv | recipe ID/version, serving count, final mass_g |
| template_components.csv | saved meal ID/version, component type/ID, default quantity/unit |
| daily_summaries.csv | date, entry counts, water/drink volume, known energy/macros, coverage, steps, activity totals, daily weight, measurement count |
| attachments.csv | attachment ID, filename/type/digest/size, linked record ID, included flag |

All applicable rows include context_only and record revision. Full column types and null conventions accompany the bundle in DATA_DICTIONARY.md. Missing numeric fields are blank cells; explicit zero is `0`. Booleans are true/false. Proper CSV quoting preserves multiline notes and Unicode. Protect spreadsheet-facing text from formula execution while documenting the reversible escaping; JSON preserves original text exactly.

### Markdown report

Human-readable manifest, per-day summaries, and chronological entries. Show portions, available nutrition, drink volumes, activities, steps, weight, check-ins with elapsed time, and notes when selected. Append source attribution, units, unknown/estimated counts, logging completeness, and exclusions. Large ranges can split into dated report files with an index.

The report carries descriptive data, not AI-generated conclusions or automatic dietary advice. It should be useful to a reader without access to Newleaf.

### Attachments and full backup

When selected, a ZIP contains JSON/report/CSV outputs plus attachment files and a manifest linking them to records. Export original local photos, not just thumbnails, with their association and optional metadata. No attachment is uploaded to a nutrition provider merely because it is exported.

Full backup is a versioned JSON archive with every active journal/library/settings record, all referenced recipe snapshots, and attachments. Exclude the personal USDA key, gateway authentication token, and transient drafts/cache. Credentials use separate local protected storage; restoring a backup preserves this installation’s current credentials and cannot import keys or tokens from a file. USDA data are public domain and Open Food Facts source nutrition includes attribution and a separately reusable licensed subset; neither requires omitting journal nutrition. A backup cannot silently omit required data and still be labeled complete.

## 8. Restore and integrity

Restore previews schema version, counts, file integrity, and storage requirements before committing. Offer merge or replace with clear consequences. Merge preserves IDs, skips byte-equivalent duplicates, and requires a decision for conflicting revisions. Replace creates a recovery backup first, then atomically installs the selected data. Cancellation or invalid archives leave current data unchanged.

Validate record references, attachment digests, quantities/units, timestamps, and supported schema before commit. Referenced meals/recipes must exist or be deliberately represented as missing context; never crash or silently discard related records. Derived summaries are recalculated and compared with exported values. Unsupported future schema is rejected with a clear message.

Database migrations and application updates preserve historical snapshots. Journal deletion clears local records and linked photos after the undo period; exported files outside the app are separately owned by the user. Clearly distinguish clearing lookup cache from deleting the journal.

## 9. Export verification examples

These are acceptance fixtures for later development, not measured nutrition facts:

- A reference food containing 200 kcal per 100 g logged at 150 g exports 300 kcal and its scale basis.
- A known 300 kcal food plus a name-only food exports known total 300 kcal, one unknown contributor, and partial coverage.
- A meal with 300 kcal food and a linked 50 kcal drink exports 350 kcal; the drink is not counted twice.
- A 16 US fl oz drink normalizes to 473.176473 mL and round-trips to the original display quantity.
- A morning check-in linked to yesterday's meal exports that meal as context when exporting only today.
- A CSV with a note containing commas/newlines and a formula-like prefix remains safe and interpretable; JSON restores the exact note.
- Backup, restore, and re-export preserve IDs, notes, tags, source snapshots, recipe versions, units, and attachment bytes.
