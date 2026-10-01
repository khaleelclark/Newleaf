package com.khaleel.newleaf.data

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/** Fictional fixtures only; this generator never opens the app's database. */
object SampleExports {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Pass a destination directory" }
        val directory = File(args.single()).apply { check(isDirectory || mkdirs()) }
        fun at(entry: Entry, time: String) = EventTimes.at(entry, Instant.parse(time), ZoneId.of("America/New_York"))
        val source = SourceReference(provider = "Newleaf fictional acceptance fixture", itemId = "fixture:food", attribution = "Invented values for export verification; not measured nutrition facts", license = "CC0 fictional example")
        val meal = at(Entry(id = "sample-meal", kind = EntryKind.MEAL, name = "Fictional lunch", notes = "An example meal, not a dietary recommendation.", componentIds = listOf("sample-food", "sample-drink", "sample-unknown")), "2026-09-30T16:00:00Z")
        val reference = Entry(id = "sample-reference", kind = EntryKind.FOOD, name = "Fictional reference food", isLibrary = true, basisQuantity = 100.0, basisUnit = "g", nutrients = mapOf("energy_kcal" to 200.0, "protein_g" to 10.0, "sodium_mg" to null), sourceReference = source)
        val food = at(Nutrition.scalePortion(reference, 150.0, "g").copy(id = "sample-food", isLibrary = false, mealId = meal.id, portionEvidence = "measured", notes = "=SUM(1,2)\nCSV protects this text; JSON keeps it exact. Unicode: 🥗", photoPaths = listOf("fictional-photo.png")), "2026-09-30T16:00:00Z")
        val unknown = at(Entry(id = "sample-unknown", kind = EntryKind.FOOD, name = "Name-only fictional food", mealId = meal.id), "2026-09-30T16:00:00Z")
        val drink = at(Entry(id = "sample-drink", kind = EntryKind.DRINK, name = "Fictional coffee", mealId = meal.id, quantity = 12.0, unit = "US fl oz", nutrients = mapOf("energy_kcal" to 50.0), portionEvidence = "estimated"), "2026-09-30T16:00:00Z")
        val water = at(Entry(id = "sample-water", kind = EntryKind.DRINK, name = "Water", quantity = 16.0, unit = "US fl oz", countsAsWater = true, nutrients = mapOf("energy_kcal" to 0.0)), "2026-10-01T14:00:00Z")
        val feeling = at(Entry(id = "sample-checkin", kind = EntryKind.CHECKIN, name = "Morning check-in", mealId = meal.id, tags = listOf("satisfied", "custom-example"), ratings = mapOf("energy" to 4), notes = "A linked observation on the following day."), "2026-10-01T12:00:00Z")
        val steps = Entry(id = "sample-steps", kind = EntryKind.STEPS, name = "Daily steps", localDate = "2026-10-01", steps = 0)
        val activity = at(Entry(id = "sample-activity", kind = EntryKind.ACTIVITY, name = "Fictional walk", distanceMeters = Units.distanceMeters(1.0, "mi"), durationSeconds = 2400, endAt = "2026-10-02T04:20:00Z"), "2026-10-02T03:40:00Z")
        val weight1 = at(Entry(id = "sample-weight-1", kind = EntryKind.WEIGHT, name = "Fictional weight", quantity = 150.0, unit = "lb", weightKg = Units.weightKg(150.0, "lb")), "2026-10-01T11:00:00Z")
        val weight2 = at(Entry(id = "sample-weight-2", kind = EntryKind.WEIGHT, name = "Fictional weight", weightKg = 68.0), "2026-10-01T23:00:00Z")
        val recipe = Recipes.create("Fictional recipe", listOf(food.copy(mealId = null, photoPaths = emptyList())), 2.0, 250.0, id = "sample-recipe")
        val loggedRecipe = Recipes.log(recipe, 1.0, "serving", Instant.parse("2026-10-01T22:00:00Z"), ZoneId.of("America/New_York")).copy(id = "sample-recipe-serving")
        val journal = Journal(listOf(meal, food, unknown, drink, water, feeling, steps, activity, weight1, weight2, recipe, loggedRecipe, reference))
        val photo = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV2QAAAAASUVORK5CYII=")
        val options = ExportOptions(includeLibrary = true, includeRecipes = true)
        File(directory, "journal.json").writeText(JournalExports.json(journal, options))
        File(directory, "journal.md").writeText(JournalExports.markdown(journal, options))
        File(directory, "journal-csv.zip").writeBytes(JournalExports.csvZip(journal, options))
        File(directory, "journal-backup.zip").writeBytes(JournalExports.backup(journal, PhotoReader { photo }))
        File(directory, "one-day-context.json").writeText(JournalExports.json(journal, ExportOptions("2026-10-01", "2026-10-01")))
        File(directory, "README.md").writeText("# Fictional Newleaf export samples\n\nInvented acceptance values only, with a 1×1 placeholder PNG in the backup. No owner data or credentials. journal.json, journal-csv.zip and journal.md show all records/library/recipes; journal-backup.zip also preserves original attachment bytes. one-day-context.json demonstrates yesterday's meal included as context for today's check-in, excluded from today's totals.\n")
        JournalExports.previewRestore(File(directory, "journal-backup.zip").readBytes())
        println("Verified fictional samples in ${directory.absolutePath}")
    }
}
