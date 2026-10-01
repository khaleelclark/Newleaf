package com.khaleel.newleaf.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DomainTest {
    private val date = "2026-10-01"
    private fun food(name: String = "Fixture", nutrients: Map<String, Double?> = emptyMap()) = Entry(kind = EntryKind.FOOD, name = name, nutrients = nutrients)
    @Test fun scale150gFrom100gIs300kcalAndRetainsUnknowns() {
        val scaled = Nutrition.scalePortion(food(nutrients = mapOf("energy_kcal" to 200.0, "sodium_mg" to null)).copy(basisQuantity = 100.0, basisUnit = "g"), 150.0, "g")
        assertEquals(300.0, scaled.nutrients["energy_kcal"]!!, 0.0)
        assertNull(scaled.nutrients["sodium_mg"])
        assertEquals("1.5", scaled.metadata["nutrition_scale"])
    }
    @Test fun unsupportedHouseholdMeasureDoesNotInventNutrition() {
        val scaled = Nutrition.scalePortion(food(nutrients = mapOf("energy_kcal" to 200.0)).copy(basisQuantity = 100.0, basisUnit = "g"), 1.0, "cup")
        assertNull(scaled.nutrients["energy_kcal"])
        val defined = Nutrition.scalePortion(food(nutrients = mapOf("energy_kcal" to 200.0)).copy(basisQuantity = 100.0, basisUnit = "g", portionDefinitions = listOf(PortionDefinition("cup", massGrams = 150.0))), 1.0, "cup")
        assertEquals(300.0, defined.nutrients["energy_kcal"]!!, 0.0)
        assertEquals(150.0, defined.massGrams!!, 0.0)
    }
    @Test fun unitsKeepMassAndVolumeDistinctAndRoundTrip() {
        assertEquals(473.176473, Units.volumeMl(16.0, "US fl oz")!!, 1e-9)
        assertEquals(16.0, Units.displayVolume(Units.volumeMl(16.0, "fl oz")!!, "fl oz"), 1e-12)
        assertNull(Units.volumeMl(16.0, "oz"))
        assertEquals(28.349523125, Units.massGrams(1.0, "oz")!!, 0.0)
        assertNull(Units.convert(1.0, "g", "ml"))
        assertEquals(0.45359237, Units.weightKg(1.0, "lb")!!, 0.0)
        assertEquals(1609.344, Units.distanceMeters(1.0, "mi")!!, 0.0)
        assertEquals(1.0, Units.displayDistance(1609.344, "mi"), 0.0)
    }
    @Test fun coverageKeepsKnownSumWithUnknownFoodAndZero() {
        val partial = Nutrition.aggregate(listOf(food(nutrients = mapOf("energy_kcal" to 300.0)), food()), "energy_kcal")
        assertEquals(300.0, partial.knownSum!!, 0.0); assertEquals(1, partial.unknownContributors)
        assertEquals(Completeness.PARTIAL, partial.completeness)
        assertEquals(0.0, Nutrition.aggregate(listOf(food(nutrients = mapOf("energy_kcal" to 0.0))), "energy_kcal").knownSum!!, 0.0)
        assertNull(Nutrition.aggregate(emptyList(), "energy_kcal").knownSum)
        assertNull(Nutrition.aggregate(listOf(food()), "energy_kcal").knownSum)
    }
    @Test fun mealTotalsAndLibraryNeverDoubleCountFoodOrDrink() {
        val meal = Entry(kind = EntryKind.MEAL, name = "Lunch", nutrients = mapOf("energy_kcal" to 350.0))
        val a = food(nutrients = mapOf("energy_kcal" to 300.0)).copy(mealId = meal.id)
        val drink = Entry(kind = EntryKind.DRINK, name = "Coffee", mealId = meal.id, quantity = 16.0, unit = "fl oz", nutrients = mapOf("energy_kcal" to 50.0))
        val day = Summaries.day(listOf(meal, a, drink, a.copy(id = "library", isLibrary = true)), a.localDate)
        assertEquals(350.0, day.nutrients["energy_kcal"]!!.knownSum!!, 0.0)
        assertEquals(473.176473, day.drinkVolumeMl!!, 1e-9); assertNull(day.waterMl)
    }
    @Test fun drinkCompositionCountsOnlyInsideTheDrink() {
        val coffee = Entry(kind = EntryKind.DRINK, name = "Coffee", nutrients = mapOf("energy_kcal" to 0.0))
        val milk = food("Milk", mapOf("energy_kcal" to 50.0))
        val composed = Nutrition.composeDrink(coffee, listOf(milk))
        val day = Summaries.day(listOf(composed), composed.localDate)
        assertEquals(50.0, day.nutrients["energy_kcal"]!!.knownSum!!, 0.0)
        assertEquals(1, day.nutrients["energy_kcal"]!!.totalContributors)
    }
    @Test fun recipeHistoryUsesSavedVersionAndMissingYieldStaysUnknown() {
        val ingredients = listOf(food(nutrients = mapOf("energy_kcal" to 600.0)))
        val recipe = Recipes.create("Recipe", ingredients, 3.0, version = 1)
        val old = Recipes.log(recipe, 1.0, "serving")
        val revised = Recipes.create("Recipe", listOf(food(nutrients = mapOf("energy_kcal" to 900.0))), 3.0, id = recipe.id, version = 2)
        assertEquals(200.0, old.nutrients["energy_kcal"]!!, 0.0)
        assertEquals(1, old.recipeVersion); assertEquals(600.0, old.recipeSnapshot!!.nutrients["energy_kcal"]!!, 0.0)
        assertEquals(300.0, Recipes.log(revised, 1.0, "serving").nutrients["energy_kcal"]!!, 0.0)
        assertNull(Recipes.log(recipe, 100.0, "g").nutrients["energy_kcal"])
        assertEquals(200.0, Recipes.log(recipe.copy(yieldGrams = 300.0), 100.0, "g").nutrients["energy_kcal"]!!, 0.0)
    }
    @Test fun repeatedMealGetsNewIDsAndPreservesSnapshot() {
        val meal = Entry(kind = EntryKind.MEAL, name = "Meal")
        val item = food(nutrients = mapOf("energy_kcal" to 300.0)).copy(mealId = meal.id)
        val repeated = Recipes.repeatMeal(meal.copy(componentIds = listOf(item.id)), listOf(item), Instant.parse("2026-10-02T12:00:00Z"), ZoneId.of("UTC"))
        assertNotEquals(meal.id, repeated[0].id); assertNotEquals(item.id, repeated[1].id)
        assertEquals(repeated[0].id, repeated[1].mealId); assertEquals("2026-10-02", repeated[1].localDate)
        assertEquals(item.nutrients, repeated[1].nutrients)
    }
    @Test fun emptyDaysAndMeasuredZeroAreDifferent() {
        val empty = Summaries.day(emptyList(), date)
        assertNull(empty.steps); assertNull(empty.weightKg); assertNull(empty.waterMl)
        val zero = Summaries.day(listOf(Entry(kind = EntryKind.STEPS, name = "Steps", localDate = date, steps = 0)), date)
        assertEquals(0L, zero.steps)
    }
    @Test fun weightTrendAveragesDailyMeansAndRequiresThreeDays() {
        fun weight(day: String, kg: Double) = EventTimes.at(Entry(kind = EntryKind.WEIGHT, name = "Weight", weightKg = kg), Instant.parse("${day}T12:00:00Z"), ZoneId.of("UTC"))
        val entries = listOf(weight("2026-09-29", 60.0), weight("2026-09-29", 80.0), weight("2026-09-30", 80.0), weight(date, 90.0))
        assertEquals(70.0, Summaries.day(entries, "2026-09-29").weightKg!!, 0.0)
        assertEquals(80.0, Summaries.weightTrend(entries, date).meanKg!!, 0.0)
        assertEquals(3, Summaries.weightTrend(entries, date).measuredDays)
        assertNull(Summaries.weightTrend(entries.take(3), date).meanKg)
    }
    @Test fun averagesExcludeEmptyDaysAndReportDenominator() {
        val entry = EventTimes.at(food(nutrients = mapOf("energy_kcal" to 300.0)), Instant.parse("2026-10-01T12:00:00Z"), ZoneId.of("UTC"))
        val average = Summaries.average(Summaries.range(listOf(entry), date, "2026-10-03"), "energy_kcal")
        assertEquals(300.0, average.mean!!, 0.0); assertEquals(1, average.observedDays); assertEquals(3, average.totalDays)
    }
    @Test fun daylightSavingGapAndOverlapRequireCorrectionOrChoice() {
        val zone = ZoneId.of("America/New_York")
        assertThrows(IllegalArgumentException::class.java) { EventTimes.resolve(LocalDateTime.parse("2026-03-08T02:30:00"), zone) }
        assertThrows(IllegalArgumentException::class.java) { EventTimes.resolve(LocalDateTime.parse("2026-11-01T01:30:00"), zone) }
        val a = EventTimes.resolve(LocalDateTime.parse("2026-11-01T01:30:00"), zone, ZoneOffset.of("-04:00"))
        val b = EventTimes.resolve(LocalDateTime.parse("2026-11-01T01:30:00"), zone, ZoneOffset.of("-05:00"))
        assertEquals(3600, Duration.between(a, b).seconds)
    }
    @Test fun eventDateStaysInOriginalZoneAndActivityIsNotSplit() {
        val activity = EventTimes.at(Entry(kind = EntryKind.ACTIVITY, name = "Walk", distanceMeters = 1000.0, endAt = "2026-10-02T01:00:00Z"), Instant.parse("2026-10-01T23:30:00Z"), ZoneId.of("UTC"))
        assertEquals(1000.0, Summaries.day(listOf(activity), date).activityDistanceMeters!!, 0.0)
        assertNull(Summaries.day(listOf(activity), "2026-10-02").activityDistanceMeters)
        val shifted = EventTimes.at(activity, Instant.parse(activity.occurredAt), ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-10-02", shifted.localDate)
    }
    @Test fun ratingsAndNonfiniteNumericInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { JournalValidation.entry(food().copy(quantity = Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { JournalValidation.entry(food().copy(ratings = mapOf("hunger" to 0))) }
    }
    @Test fun recipePartialNutrientRetainsKnownSumAndPartialCoverage() {
        val recipe = Recipes.create("Partial recipe", listOf(food(nutrients = mapOf("energy_kcal" to 300.0)), food()), 2.0)
        val logged = Recipes.log(recipe, 1.0, "serving")
        val coverage = Nutrition.aggregate(listOf(logged), "energy_kcal")
        assertEquals(150.0, coverage.knownSum!!, 0.0)
        assertEquals(Completeness.PARTIAL, coverage.completeness)
        assertEquals(1, coverage.partialContributors)
    }
    @Test fun loggingAndRepeatingDeepCopyMutableReferenceSnapshots() {
        val nutrients = mutableMapOf<String, Double?>("energy_kcal" to 600.0)
        val recipe = Recipes.create("Snapshot", listOf(food(nutrients = nutrients)), 3.0)
        val logged = Recipes.log(recipe, 1.0, "serving")
        nutrients["energy_kcal"] = 900.0
        assertEquals(600.0, recipe.ingredientSnapshots.single().nutrients["energy_kcal"]!!, 0.0)
        assertEquals(600.0, logged.recipeSnapshot!!.ingredientSnapshots.single().nutrients["energy_kcal"]!!, 0.0)
    }
    @Test fun applyingPortionScalingTwiceIsRejected() {
        val scaled = Nutrition.scalePortion(food(nutrients = mapOf("energy_kcal" to 200.0)).copy(basisQuantity = 100.0, basisUnit = "g"), 150.0, "g")
        assertThrows(IllegalArgumentException::class.java) { Nutrition.scalePortion(scaled, 150.0, "g") }
    }

    @Test fun drinkCompositionMissingIngredientHasKnownSubtotalButPartialCoverage() {
        val base = Entry(kind = EntryKind.DRINK, name = "Coffee", nutrients = mapOf("energy_kcal" to 0.0))
        val drink = Nutrition.composeDrink(base, listOf(food("Milk", mapOf("energy_kcal" to 50.0)), food("Unknown addition")))
        val coverage = Nutrition.aggregate(listOf(drink), "energy_kcal")
        assertEquals(50.0, coverage.knownSum!!, 0.0)
        assertEquals(Completeness.PARTIAL, coverage.completeness)
        assertEquals(1, coverage.partialContributors)
        assertEquals(0, coverage.estimatedContributors)
        assertEquals(2, drink.nutrientEvidence["energy_kcal"]!!.coverageKnown)
        assertEquals(3, drink.nutrientEvidence["energy_kcal"]!!.coverageTotal)
    }
    @Test fun estimatedRecipePortionsRemainEstimatedWithoutBecomingPartial() {
        val ingredient = food(nutrients = mapOf("energy_kcal" to 100.0)).copy(portionEvidence = "estimated")
        val logged = Recipes.log(Recipes.create("Estimated recipe", listOf(ingredient), 1.0), 1.0, "serving")
        val coverage = Nutrition.aggregate(listOf(logged), "energy_kcal")
        assertEquals(Completeness.COMPLETE, coverage.completeness)
        assertEquals(0, coverage.partialContributors)
        assertEquals(1, coverage.estimatedContributors)
    }

    @Test fun photoReferencesIncludeNestedSnapshotsAndDraftSchemas() {
        val ingredient = food().copy(photoPaths = listOf("/photos/ingredient.jpg"))
        val entry = food().copy(recipeSnapshot = Recipes.create("Recipe", listOf(ingredient), 1.0),
            composition = listOf(food().copy(photoPaths = listOf("/photos/addition.jpg"))))
        val drafts = mapOf("editor" to "{\"photos\":[\"/photos/draft.jpg\"],\"items\":[{\"photoPaths\":[\"/photos/nested-draft.jpg\"]}]}")
        assertEquals(setOf("/photos/ingredient.jpg", "/photos/addition.jpg", "/photos/draft.jpg", "/photos/nested-draft.jpg"), PhotoReferences.collect(Journal(listOf(entry), drafts = drafts)))
        assertNull(PhotoReferences.collect(Journal(drafts = mapOf("unreadable" to "{broken draft"))))
    }

}
