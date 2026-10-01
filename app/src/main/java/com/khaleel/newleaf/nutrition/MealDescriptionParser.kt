package com.khaleel.newleaf.nutrition

import com.google.gson.JsonNull
import com.google.gson.JsonObject

/** Bounded syntax only: no nutrition, density or serving-size inference. */
internal object MealDescriptionParser {
    data class Item(val input: String, val query: String, val quantity: Double?, val unit: String, val recognized: Boolean) {
        fun envelope(food: JsonObject?, needsDetails: Boolean = false): JsonObject = JsonObject().apply {
            addProperty("input_text", input); addProperty("query", query)
            addProperty("quantity_hint", quantity); addProperty("unit_hint", unit)
            addProperty("unit_recognized", recognized); addProperty("quantity_estimated", true)
            addProperty("match_estimated", true); addProperty("needs_details", needsDetails)
            addProperty("match_status", if (food == null) "no_match" else "provisional")
            add("food", food ?: JsonNull.INSTANCE)
        }
    }
    private val aliases = mapOf(
        "g" to "g", "gram" to "g", "grams" to "g", "kg" to "kg", "kilogram" to "kg", "kilograms" to "kg",
        "mg" to "mg", "oz" to "oz", "ounce" to "oz", "ounces" to "oz", "lb" to "lb", "pound" to "lb", "pounds" to "lb",
        "ml" to "mL", "milliliter" to "mL", "milliliters" to "mL", "l" to "L", "liter" to "L", "liters" to "L",
        "fl oz" to "US fl oz", "fluid ounce" to "US fl oz", "fluid ounces" to "US fl oz",
        "cup" to "cup", "cups" to "cup", "tbsp" to "tablespoon", "tablespoon" to "tablespoon", "tablespoons" to "tablespoon",
        "tsp" to "teaspoon", "teaspoon" to "teaspoon", "teaspoons" to "teaspoon", "serving" to "serving", "servings" to "serving",
        "slice" to "slice", "slices" to "slice", "piece" to "piece", "pieces" to "piece", "item" to "item", "items" to "item")
    private val words = mapOf("one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "half" to .5)
    private val quantities = Regex("^([0-9]+ +[0-9]+/[0-9]+|[0-9]+/[0-9]+|[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*(.*)$")
    private fun invalid(): Nothing = throw NutritionException(NutritionError.INVALID_REQUEST)
    private fun amount(value: String): Double = value.split(Regex(" +")).sumOf { part ->
        val fraction = part.split('/')
        if (fraction.size == 1) fraction[0].toDoubleOrNull() ?: invalid()
        else {
            val numerator = fraction[0].toDoubleOrNull() ?: invalid()
            val denominator = fraction[1].toDoubleOrNull()?.takeIf { it > 0 } ?: invalid()
            numerator / denominator
        }
    }.takeIf { it.isFinite() && it > 0 && it <= 1000000.0 } ?: invalid()

    fun parse(text: String): List<Item> {
        if (text.length > 2000 || text.trim().length !in 2..2000 || text.any { it.code < 32 && it != '\n' && it != '\t' }) invalid()
        val parts = text.split(Regex("[,\\n]")).map(String::trim).filter(String::isNotEmpty)
        if (parts.size !in 1..8) invalid()
        return parts.map { input ->
            var normalized = input.replace(Regex("\\s+"), " ")
            for ((symbol, value) in mapOf("½" to "1/2", "¼" to "1/4", "¾" to "3/4", "⅓" to "1/3", "⅔" to "2/3")) {
                normalized = normalized.replace(symbol, " $value").trim()
            }
            var quantity: Double? = null
            var rest = normalized
            val match = quantities.matchEntire(normalized)
            if (match != null) {
                quantity = amount(match.groupValues[1]); rest = match.groupValues[2].trim()
            } else {
                val word = normalized.substringBefore(' ').lowercase(java.util.Locale.ROOT)
                if (word in words) { quantity = words.getValue(word); rest = normalized.substringAfter(' ', "") }
                else if (normalized.startsWith('-') || normalized.startsWith('+')) invalid()
            }
            var unit = if (quantity == null) "serving" else "item"
            var recognized = quantity == null
            var query = rest
            if (quantity != null) {
                val alias = aliases.keys.sortedByDescending(String::length).firstOrNull {
                    rest.startsWith("$it ", ignoreCase = true)
                }
                if (alias != null) { unit = aliases.getValue(alias); recognized = true; query = rest.substring(alias.length).trim() }
                else if (' ' in rest) { unit = rest.substringBefore(' '); query = rest }
            }
            if (query.length !in 2..200 || query.any { it.code < 32 }) invalid()
            Item(input, query, quantity, unit, recognized)
        }
    }
}
