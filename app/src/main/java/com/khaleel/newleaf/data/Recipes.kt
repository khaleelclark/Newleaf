package com.khaleel.newleaf.data

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object Recipes {
    fun create(name: String, ingredients: List<Entry>, servings: Double, cookedGrams: Double? = null,
               id: String = UUID.randomUUID().toString(), version: Int = 1): Entry {
        require(ingredients.isNotEmpty())
        require(servings.isFinite() && servings > 0)
        require(cookedGrams == null || (cookedGrams.isFinite() && cookedGrams > 0))
        val coverage = Nutrition.all(ingredients)
        return Entry(id = id, kind = EntryKind.RECIPE, name = name, version = version, ingredientSnapshots = ingredients.map { JournalCodec.entry(JournalCodec.toJson(it)) },
            yieldServings = servings, yieldGrams = cookedGrams, nutrients = coverage.mapValues { it.value.knownSum },
            nutrientEvidence = coverage.mapValues { (code, value) -> NutrientEvidence(
                status = when { value.knownSum == null -> "unknown"; value.estimatedContributors > 0 -> "estimated"; else -> "known" }, origin = "recipe_calculation",
                unit = Units.nutrientUnit(code), basis = "whole recipe; known=${value.knownContributors}/${value.totalContributors}",
                coverageKnown = value.knownContributors - value.partialContributors, coverageTotal = value.totalContributors) })
    }
    fun log(recipe: Entry, quantity: Double, unit: String, instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Entry {
        require(recipe.kind == EntryKind.RECIPE)
        require(quantity.isFinite() && quantity > 0)
        val factor = when (unit.lowercase()) {
            "serving", "servings" -> recipe.yieldServings?.let { quantity / it }
            else -> Units.massGrams(quantity, unit)?.let { grams -> recipe.yieldGrams?.let { grams / it } }
        }
        return EventTimes.at(Entry(kind = EntryKind.FOOD, name = recipe.name, quantity = quantity, unit = unit,
            notes = recipe.notes, source = "recipe", sourceId = recipe.id, recipeId = recipe.id, recipeVersion = recipe.version,
            recipeSnapshot = JournalCodec.entry(JournalCodec.toJson(recipe)),
            massGrams = Units.massGrams(quantity, unit), nutrients = recipe.nutrients.mapValues { if (factor == null) null else it.value?.times(factor) },
            nutrientEvidence = recipe.nutrientEvidence.mapValues { it.value.copy(
                status = if (factor == null) "unknown" else it.value.status, basis = "consumed; recipe ${recipe.id}/${recipe.version}; scale=${factor ?: "unknown"}") }), instant, zone)
    }
    fun repeatMeal(meal: Entry, entries: List<Entry>, instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<Entry> {
        require(meal.kind == EntryKind.MEAL || meal.kind == EntryKind.TEMPLATE)
        val components = (if (meal.kind == EntryKind.TEMPLATE && meal.ingredientSnapshots.isNotEmpty()) meal.ingredientSnapshots else entries.filter { it.mealId == meal.id || it.id in meal.componentIds }).filter { it.kind in setOf(EntryKind.FOOD, EntryKind.DRINK) }
        val newMealId = UUID.randomUUID().toString()
        val copied = components.map { original -> val it = JournalCodec.entry(JournalCodec.toJson(original)); EventTimes.at(it.copy(id = UUID.randomUUID().toString(), mealId = newMealId,
            isLibrary = false, version = 1, createdAt = instant.toString(), updatedAt = instant.toString()), instant, zone) }
        val header = EventTimes.at(meal.copy(id = newMealId, kind = EntryKind.MEAL, componentIds = copied.map { it.id },
            isLibrary = false, version = 1, createdAt = instant.toString(), updatedAt = instant.toString()), instant, zone)
        return listOf(header) + copied
    }
}
