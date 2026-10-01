# Newleaf product specification

Version 0.1 — October 1, 2026 — proposed implementation specification.

## 1. Product purpose

Help one person build a useful record of their daily lifestyle with minimal typing. Meals, drinks, movement, weight, and feelings should live together without turning each entry into a lengthy form. The journal should remain useful if the user only logs a name and time, and become richer when portions or nutrition are available.

Success means the owner can log an ordinary meal quickly, reuse regular foods and drinks, correct mistakes easily, and export the information without losing context. The app records observations; it does not diagnose symptoms or prescribe diets.

## 2. First complete release

| Area | Required behavior |
| --- | --- |
| Journal | Today timeline, historical dates, edit/delete/undo, timestamps, notes, local drafts |
| Food | Search, barcode, meal description, manual entry, flexible portions, nutrition |
| Meals | Multiple items, meal categories, custom categories, saved meals, repeat previous meal |
| Recipes | Ingredient nutrition, recipe yield, per-serving and supported gram portions, versioning |
| Drinks | US fl oz/mL, favorites, quick water logging, drink nutrition |
| Feelings | Optional meal-linked check-ins, multiple check-ins, custom tags, notes |
| Activity | Manual daily steps, activity type, distance, duration, intensity and notes |
| Weight | Multiple dated body-weight measurements, lb/kg, trend |
| Library | Favorites, recent items, custom foods, saved meals, recipes |
| Trends | Descriptive charts with coverage indicators and no causal claims |
| Data portability | JSON, CSV bundle, Markdown, optional attachments, backup and restore |
| Device | Light/dark custom design, accessibility, offline journal, signed Android delivery |
| Sources | USDA, Open Food Facts, commercial natural-language provider |

The first release includes all three source categories. Internal milestones may deliver them sequentially, but a partial milestone is not the complete requested release. Commercial access may gate one feature; it cannot be silently replaced by manual entry and described as finished.

## 3. Meals and food

### 3.1 Minimum entry and richer detail

A food needs a name and event time. Quantity, unit, brand, preparation, nutrients, notes, and photo are optional. New entries default to now and the selected journal date. Historical entries clearly show their historical date before saving.

Food can be attached to a meal or saved as a standalone snack. Meal categories start with Breakfast, Lunch, Dinner, and Snack. Add custom categories without a fixed schedule; the user can override a suggested category. A meal has a name, time, items, notes, photos, and optional feelings.

### 3.2 Logging methods

- **Search:** favorites/recent items first; online results with food name, brand, serving, and source. Select a food, adjust portion, and add it to the meal.
- **Barcode:** scan or type a barcode, review the matched product, choose consumed quantity, and save.
- **Describe:** type a meal in ordinary language, review parsed items and amounts, edit mismatches, and confirm the meal. No parsed result is saved without review.
- **Manual:** enter a custom food or just its name. Nutrition can be added later without losing the original entry.
- **Repeat:** reuse a previous meal or saved template with a new timestamp. Edits to the new meal do not change its source meal.

For drinks returned by meal parsing, create linked drink entries rather than also counting them as food. Butter, milk, dressing, cooking oil, and similar additions appear as explicit reviewable items if identified; the parser must not silently invent missing quantities.

### 3.3 Portions and nutrition

Accept grams, ounces by weight, named servings, pieces, and supported household measures such as cups or tablespoons. User-entered serving labels are valid even without a gram conversion; in that case, record the amount and preserve unknown nutrition instead of fabricating a conversion.

Show calories first, with protein, carbohydrates, fat, fiber, sugar, and sodium in expanded detail. Retain other available nutrients for export. Display source, estimated portion status, and user overrides in detail views rather than crowding the entry form.

Allow measured, estimated, and unspecified quantity statuses. User confirmation of a match does not turn an estimated quantity into a measured one. Database nutrition is a reference value, not a measurement of this particular plate.

### 3.4 Library and recipes

Favorites remember a food and usual quantity, not just its name. Saved meals are editable templates containing foods and drinks. Recipes contain ingredients, preparation notes, and a yield expressed as servings and optionally final cooked weight.

Recipe nutrition is calculated from ingredient snapshots. Logging a recipe uses the recipe version current at that time. Changing a recipe tomorrow does not change yesterday's meal. Gram portions require a usable final yield weight; recipe ingredient weight alone is not assumed to equal cooked yield.

## 4. Drinks and water

Preset drink types: water, coffee, tea, milk, juice, soda, alcohol, and other; custom names allowed. Store consumed volume in mL and display US fl oz or mL according to settings. Proposed editable quick-water sizes: 8, 16, and 24 US fl oz.

Water progress counts entries classified as water by default. Other beverages contribute to total drink volume and nutritional totals but do not get a made-up hydration multiplier. Show labels that make this distinction clear. Plain water can explicitly carry zero calories; an unknown drink's calories remain unknown.

Coffee with milk/sugar may have a base drink plus additions in its composition; the drink total is counted once. Reusing a favorite carries those additions forward. A single water tap saves immediately with visible Undo; holding or opening the preset permits quantity/time adjustment.

Daily water goals are optional. The owner can enter a goal; the app does not prescribe one.

## 5. Feelings and daily context

After a meal, attach one or more timestamped check-ins. Preset tags include satisfied, still hungry, energized, sleepy, bloated, uncomfortable, and neutral, plus custom tags. Optional hunger and fullness ratings use clearly labeled 1–5 scales; optional energy uses 1–5. These are personal observations, not clinical scores.

Store when the feeling was observed separately from when it was entered. Show elapsed time since the linked meal. Allow an immediate check-in, a later check-in, and a correction. The app does not automatically assign a feeling to the closest meal.

A daily note can record context such as sleep, stress, or travel in free text. Dedicated sleep tracking is outside this release. An optional reminder to check in later is configurable and off by default; dismissing it creates no fabricated observation.

## 6. Activity and steps

One manual step total per local date; a replacement edits that day's total instead of adding another total. Zero is a valid explicit value; no entry means unknown. Steps are independent of activity records.

Activity types include walk, hike, run, cycling, workout, and custom. Minimum is type/name and date/time. Optional distance uses mi/km; duration uses minutes or start/end time; optional perceived intensity and notes. Cross-midnight activity remains one record and belongs to its start date for daily summaries; exports include both times.

No activity calories are estimated by default. Activity distance is shown as recorded activity distance; steps are not converted into distance or added to recorded distance. Future sensor/Samsung Health/Health Connect imports would require source precedence and deduplication, and are explicitly deferred.

## 7. Body weight

Log lb or kg with event time, optional notes, and an optional user-defined context such as morning. Keep multiple measurements; never silently overwrite them. Today can display the latest measurement for that date. The daily weight series uses the mean of that day's measurements and reports measurement count. A seven-day moving average uses available daily means, with its count visible, and requires at least three measured days in the window.

Optional starting and target weights are presentation preferences. The app supports maintenance or observation without a weight-loss target.

## 8. History and trends

History offers a date picker, timeline, and filters for meals, drinks, activities, weight, and feelings. Search food/meal names and notes. Empty dates say “Nothing logged”; they do not imply no food, no activity, or zero calories.

Trends use 7-day, 30-day, and custom ranges: known calories and macros, water and total drink volume, steps, activity duration/distance, body weight, and feeling frequencies. Each chart identifies observed days, missing entries, partial nutrition, and the aggregation used. Optional daily logging completeness is marked by the user as unmarked, partial, or complete; it is never inferred from entry count.

Feelings can be explored by meal and elapsed time, with observation counts. Avoid statements that a food caused a symptom. Trends and exports describe patterns in recorded information, with gaps apparent.

## 9. Export, recovery, and privacy

Export one day, an inclusive date range, or everything. Provide CSV tables for spreadsheets, versioned JSON for structured analysis, and a readable Markdown report. Include meals, drinks, activity, steps, weight, check-ins, notes, units, sources, and data-quality fields. Export library items, recipes, and attachments as selectable additions; a full backup includes them all.

The export review shows record counts, options, and estimated size. Use the Android save/share flow. User controls whether notes and photos are included; omitting them is explicit in the manifest. Nothing is sent to an AI automatically. A full backup and restore flow prevents portability from depending on an online service.

Journal data resides on the phone by default. Online food search and parsing send only what is necessary to the selected nutrition service. The first use of online parsing briefly explains that the meal description goes to a provider. This does not require a diary account. Details are in the integration document.

## 10. Defaults and deferred work

Defaults: local single-user journal, US display units, automatic light/dark theme, system time zone at entry, all optional goals and reminders off, nutrition summaries expandable. The unit and region settings remain editable.

Deferred: automatic step collection, wearable imports, GPS recording, photo-based nutrition estimates, social features, cloud diary sync, built-in AI advice, fasting timers, meal planning, and a public app-store launch. Photo attachment is in scope; photo recognition is separate.

Design choices and provider choices remain proposals until review. The existing user instruction authorizes this specification and folder creation only.
