# Newleaf journal report

Generated: 2026-10-01T18:27:29.713503611Z. Schema 1.0.
Range: all earlier dates through all later dates, inclusive, preserved local dates.
Selected records: 13; context-only records: 0.
Notes: true; photo bytes: false; library: true; recipes: true.
Values describe logged observations. Unknown values are not zero; complete nutrition does not imply complete daily logging.

## 2026-09-30

Known logged calories: 350.0 kcal; partial nutrition (2 known, 1 unknown, 1 estimated contributors).
Water: unknown mL; total drinks: 354.88235475 mL; steps: unknown.
Activity: unknown m, unknown s; daily weight: unknown kg (0 measurements). Logging completeness: unmarked.
- protein_g: 15.0 g; partial, 2 unknown contributors.
- carbohydrate_g: unknown g; partial, 3 unknown contributors.
- fat_g: unknown g; partial, 3 unknown contributors.
- saturated_fat_g: unknown g; partial, 3 unknown contributors.
- fiber_g: unknown g; partial, 3 unknown contributors.
- sugar_g: unknown g; partial, 3 unknown contributors.
- sodium_mg: unknown mg; partial, 3 unknown contributors.

### 2026-09-30T12:00-04:00 — Fictional coffee (drink)
ID: sample-drink; revision 1; zone America/New_York; source manual.
Portion: 12.0 US fl oz; estimated.
Known calories: 50.0 kcal; nutrition complete.
Linked meal: sample-meal.

### 2026-09-30T12:00-04:00 — Fictional reference food (food)
ID: sample-food; revision 1; zone America/New_York; source manual.
Portion: 150.0 g; measured.
Known calories: 300.0 kcal; nutrition complete.
Linked meal: sample-meal.
Notes:
=SUM(1,2)
CSV protects this text; JSON keeps it exact. Unicode: 🥗
Attachments: attachments/9c1d6530-d304-38e8-a335-6333ebfa1948.png.

### 2026-09-30T12:00-04:00 — Fictional lunch (meal)
ID: sample-meal; revision 1; zone America/New_York; source manual.
Notes:
An example meal, not a dietary recommendation.

### 2026-09-30T12:00-04:00 — Name-only fictional food (food)
ID: sample-unknown; revision 1; zone America/New_York; source manual.
Known calories: unknown kcal; nutrition partial.
Linked meal: sample-meal.


## 2026-10-01

Known logged calories: 150.0 kcal; complete nutrition (2 known, 0 unknown, 0 estimated contributors).
Water: 473.176473 mL; total drinks: 473.176473 mL; steps: 0.
Activity: 1609.344 m, 2400 s; daily weight: 68.01942775 kg (2 measurements). Logging completeness: unmarked.
- protein_g: 7.5 g; partial, 1 unknown contributors.
- carbohydrate_g: unknown g; partial, 2 unknown contributors.
- fat_g: unknown g; partial, 2 unknown contributors.
- saturated_fat_g: unknown g; partial, 2 unknown contributors.
- fiber_g: unknown g; partial, 2 unknown contributors.
- sugar_g: unknown g; partial, 2 unknown contributors.
- sodium_mg: unknown mg; partial, 2 unknown contributors.

### 2026-10-01T07:00-04:00 — Fictional weight (weight)
ID: sample-weight-1; revision 1; zone America/New_York; source manual.
Portion: 150.0 lb; unspecified.
Weight: 68.03885550000001 kg.

### 2026-10-01T08:00-04:00 — Morning check-in (checkin)
ID: sample-checkin; revision 1; zone America/New_York; source manual.
Linked meal: sample-meal.
Observed 72000 seconds after meal; entered 2026-10-01T18:27:29.458554960Z.
Tags: satisfied, custom-example.
Ratings (1–5): {energy=4}.
Notes:
A linked observation on the following day.

### 2026-10-01T10:00-04:00 — Water (drink)
ID: sample-water; revision 1; zone America/New_York; source manual.
Portion: 16.0 US fl oz; unspecified.
Known calories: 0.0 kcal; nutrition complete.

### Date-only — Daily steps (steps)
ID: sample-steps; revision 1; zone Etc/UTC; source manual.
Daily steps: 0.

### 2026-10-01T18:00-04:00 — Fictional recipe (food)
ID: sample-recipe-serving; revision 1; zone America/New_York; source recipe.
Portion: 1.0 serving; unspecified.
Known calories: 150.0 kcal; nutrition complete.
Recipe snapshot: sample-recipe / version 1.

### 2026-10-01T19:00-04:00 — Fictional weight (weight)
ID: sample-weight-2; revision 1; zone America/New_York; source manual.
Weight: 68.0 kg.

### 2026-10-01T23:40-04:00 — Fictional walk (activity)
ID: sample-activity; revision 1; zone America/New_York; source manual.
Distance: 1609.344 m.
Duration: 2400 s.


## Library

- Fictional reference food (FOOD, version 1), ID sample-reference.
- Fictional recipe (RECIPE, version 1), ID sample-recipe.

## Sources, units, and exclusions

- Newleaf fictional acceptance fixture: Invented values for export verification; not measured nutrition facts (CC0 fictional example)
Canonical units: grams, mL, kg, meters, seconds, kcal, sodium mg. US fl oz = 29.5735295625 mL; weight oz = 28.349523125 g.
Gateway credentials and transient drafts/caches are excluded. Other exclusions: ["gateway credentials","transient drafts/caches","photo bytes"].
