# Newleaf product specification

Current behavior for Newleaf 0.1.5. Documentation reviewed October 22, 2026. Release evidence and remaining acceptance work are in [validation](../releases/VALIDATION.md).

## 1. Product purpose

Help one person build a useful record of their daily lifestyle with minimal typing. Meals, drinks, movement, weight, and feelings should live together without turning each entry into a lengthy form. The journal should remain useful if the user only logs a name and time, and become richer when portions or nutrition are available.

Success means the owner can log an ordinary meal quickly, reuse regular foods and drinks, correct mistakes easily, and export the information without losing context. The app records observations; it does not diagnose symptoms or prescribe diets.

## 2. Release scope

| Area | Required behavior |
| --- | --- |
| Journal | Today timeline, historical dates, edit/delete/undo, timestamps, notes, local drafts |
| Food | Search, barcode, bounded Food list suggestions, manual entry, flexible portions, nutrition |
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
| Sources | Free USDA search/details and assisted description lookup; Open Food Facts barcode lookup |

The owner's October 1 free-only requirement governs this release. Food search and multi-item description review use USDA; packaged-product barcode lookup uses Open Food Facts. Nutritionix's public free tier has been withdrawn, so no commercial parser is enabled. Describe provides provisional USDA matches and explicit quantity/unit hints for confirmation; it does not promise unrestricted natural-language understanding. No subscription or paid hosting is required: the owner's free USDA key can be entered on the phone, with an authenticated gateway remaining optional.

## 3. Meals and food

### 3.1 Minimum entry and richer detail

A food needs a name and event time. Quantity, unit, brand, preparation, nutrients, notes, and photo are optional. New entries default to now and the selected journal date. Historical entries clearly show their historical date before saving.

Food can be attached to a meal or saved as a standalone entry. Manual Meal category is optional, with Breakfast, Lunch, Dinner, Snack and Other plus retained custom categories. Home Scan requires an explicit category before Add to journal. A meal has a name, time, items, notes, photos and optional linked feelings.

### 3.2 Logging methods

- **Everyday food:** Today → Log food → type what you ate → Save food. No amount or nutrition is required. More details contains optional numeric/editing fields; supplied nutrition appears automatically.
- **Search:** Find a food opens Name/Barcode lookup; saved items are available separately. Choose a source match, review its portion, Confirm item into the draft, then Save.
- **Barcode:** home Scan returns to today, opens capture and looks up the product automatically. Choose category, Food/Drink and consumed amount, then Add to journal once. In an existing editor, Confirm item fills the draft before its separate Save.
- **Food list:** More → More ways to log → Meal → Find a food → Food list accepts up to eight comma/newline-separated items with optional quantities. USDA suggestions and amounts need review; free text alone does not infer nutrition.
- **Manual:** enter a custom food or just its name. Nutrition can be added later without losing the original entry.
- **Repeat:** reuse a previous meal or saved template with a new timestamp. Edits to the new meal do not change its source meal.

For drinks returned by meal parsing, create linked drink entries rather than also counting them as food. Butter, milk, dressing, cooking oil, and similar additions appear as explicit reviewable items if identified; the parser must not silently invent missing quantities.

### 3.3 Portions and nutrition

Accept grams, ounces by weight, named servings, pieces, and supported household measures such as cups or tablespoons. User-entered serving labels are valid even without a gram conversion; in that case, record the amount and preserve unknown nutrition instead of fabricating a conversion.

Show calories and all populated nutrients automatically in scanned review, confirmed drafts and saved entry detail, including explicit zeroes. Missing nutrients remain unknown. Technical source and editing controls remain secondary. Retain other supplied nutrients and their units for export.

When the provider reports a positive normalized whole-package mass/volume, review starts at one package and shows label serving separately. Enter 0.5 package or Percent of package 50 for half; compatible unit changes preserve the amount. Multipack size means the whole outer package. Missing package size offers measured units or an explicitly convertible label serving; no text-derived size or arbitrary density is invented.

Allow measured, estimated, and unspecified quantity statuses. User confirmation of a match does not turn an estimated quantity into a measured one. Database nutrition is a reference value, not a measurement of this particular plate.

### 3.4 Library and recipes

Favorites remember a food and usual quantity, not just its name. Saved meals are editable templates containing foods and drinks. Recipes contain ingredients, preparation notes, and a yield expressed as servings and optionally final cooked weight.

Recipe nutrition is calculated from ingredient snapshots. Logging a recipe uses the recipe version current at that time. Changing a recipe tomorrow does not change yesterday's meal. Gram portions require a usable final yield weight; recipe ingredient weight alone is not assumed to equal cooked yield.

## 4. Drinks and water

The everyday drink selector offers Water, Coffee, Tea, Juice, Milk and Other; Other permits soda, alcohol or any custom name. Store known consumed volume in mL and display US fl oz or mL according to settings. Today has one quick-water shortcut: 16 US fl oz (473.2 mL displayed). Custom volumes use Log drink.

Water progress counts entries classified as water by default. Other beverages contribute to total drink volume and nutritional totals but do not get a made-up hydration multiplier. Show labels that make this distinction clear. Plain water can explicitly carry zero calories; an unknown drink's calories remain unknown.

Coffee with milk/sugar may have a base drink plus additions in its composition; the drink total is counted once. Reusing a favorite carries those additions forward. A single quick-water tap saves with visible Undo. Use Log drink or edit the saved entry to adjust volume/time.

Water is the only implemented daily goal; calorie goals and starting/target weight preferences are not implemented. Daily water goals are optional. The owner can enter a goal; the app does not prescribe one.

## 5. Feelings and daily context

After a meal, attach one or more timestamped check-ins. Preset tags include satisfied, still hungry, energized, sleepy, bloated, uncomfortable, and neutral, plus custom tags. Optional hunger and fullness ratings use clearly labeled 1–5 scales; optional energy uses 1–5. These are personal observations, not clinical scores.

Store when the feeling was observed separately from when it was entered. Show elapsed time since the linked meal. Allow an immediate check-in, a later check-in, and a correction. The app does not automatically assign a feeling to the closest meal.

A daily note can record context such as sleep, stress, or travel in free text. Dedicated sleep tracking is outside this release. An optional reminder to check in later is configurable and off by default; dismissing it creates no fabricated observation.

## 6. Activity and steps

One manual step total per local date; a replacement edits that day's total instead of adding another total. Zero is a valid explicit value; no entry means unknown. Steps are independent of activity records.

Activity types include walk, hike, run, cycling, workout, and custom. Minimum is type/name and date/time. Optional distance uses mi/km; duration is entered in minutes; stored/imported records can retain an optional end timestamp; optional perceived intensity and notes. Cross-midnight activity remains one record and belongs to its start date for daily summaries; exports include an end time when one is stored.

No activity calories are estimated by default. Activity distance is shown as recorded activity distance; steps are not converted into distance or added to recorded distance. Future sensor/Samsung Health/Health Connect imports would require source precedence and deduplication, and are explicitly deferred.

## 7. Body weight

Log lb or kg with event time and optional notes. Stored/imported records can retain a measurement-context category. Keep multiple measurements; never silently overwrite them. Today lists each measurement in the timeline. The daily weight series uses the mean of that day's measurements and reports measurement count. A seven-day moving average uses available daily means, with its count visible, and requires at least three measured days in the window.

Movement → Log weight supplies the measurement name and opens weight/unit directly. The app supports observation without a weight-loss target; starting/target weight preferences are not implemented.

## 8. History and trends

History offers a date picker, timeline, and filters for meals, drinks, activities, weight, and feelings. Search food/meal names and notes. Empty dates say “Your entries will appear here”; they do not imply no food, no activity or zero calories.

Trends use 7-day, 30-day, and custom ranges: known calories and macros, water and total drink volume, steps, activity duration/distance, body weight, and feeling frequencies. Each chart identifies observed days, missing entries, partial nutrition, and the aggregation used. Optional daily logging completeness is marked by the user as unmarked, partial, or complete; it is never inferred from entry count.

Feelings can be explored by meal and elapsed time, with observation counts. Avoid statements that a food caused a symptom. Trends and exports describe patterns in recorded information, with gaps apparent.

## 9. Export, recovery, and privacy

Export one day, an inclusive date range, or everything. Provide CSV tables for spreadsheets, versioned JSON for structured analysis, and a readable Markdown report. Include meals, drinks, activity, steps, weight, check-ins, notes, units, sources, and data-quality fields. Export library items, recipes, and attachments as selectable additions; a full backup includes them all.

The export form shows range/format/options and the total journal record count before preparing the file. It does not show a preflight size estimate or selected-range count preview. Restore has a separate validated file/count/storage review. Use the Android save/share flow. User controls whether notes and photos are included; omitting them is explicit in the manifest. Nothing is sent to an AI automatically. A full backup and restore flow prevents portability from depending on an online service.

Journal data resides on the phone by default. Online food search and parsing send only what is necessary to the selected nutrition service. Lookup and source settings explain online access. Direct Food list mode splits the text locally and sends food queries to USDA; gateway mode sends the entered list to the configured gateway. There is no separate first-use consent dialog. This does not require a diary account. Details are in the integration document.

## 10. Defaults and deferred work

Defaults: local single-user journal, US display units, automatic light/dark theme, system time zone at entry, water goal unset and reminders off. Populated nutrition remains visible; technical controls expand on request. Unit and appearance settings remain editable; locale/region are retained in the data model.

Deferred: automatic step collection, wearable imports, GPS recording, photo-based nutrition estimates, social features, cloud diary sync, built-in AI advice, fasting timers, meal planning, and a public app-store launch. Photo attachment is in scope; photo recognition is separate.

The current implementation is native Kotlin/Compose with Room/SQLite. Documented behavior describes 0.1.4; acceptance targets are not certification of every physical-device behavior. Public distribution, hosted gateway deployment and performance targets require their own validation.
