package com.khaleel.newleaf.data

object Units {
    private fun key(unit: String) = unit.trim().lowercase().replace("µ", "u")
    fun massGrams(value: Double, unit: String): Double? = when (key(unit)) {
        "g", "gram", "grams" -> value
        "kg", "kilogram", "kilograms" -> value * 1000.0
        "oz", "ounce", "ounces" -> value * 28.349523125
        "lb", "lbs", "pound", "pounds" -> value * 453.59237
        "mg" -> value / 1000.0
        else -> null
    }
    fun volumeMl(value: Double, unit: String): Double? = when (key(unit)) {
        "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> value
        "l", "liter", "litre", "liters", "litres" -> value * 1000.0
        "fl oz", "us fl oz", "us fluid ounce", "us fluid ounces" -> value * 29.5735295625
        else -> null
    }
    fun weightKg(value: Double, unit: String): Double? = massGrams(value, unit)?.div(1000.0)
    fun distanceMeters(value: Double, unit: String): Double? = when (key(unit)) {
        "m", "meter", "meters" -> value
        "km", "kilometer", "kilometers" -> value * 1000.0
        "mi", "mile", "miles" -> value * 1609.344
        else -> null
    }
    fun displayWeight(kg: Double, unit: String): Double = if (unit == "lb") kg / 0.45359237 else kg
    fun displayVolume(ml: Double, unit: String): Double = if (unit == "fl oz" || unit == "US fl oz") ml / 29.5735295625 else ml
    fun displayDistance(m: Double, unit: String): Double = if (unit == "mi") m / 1609.344 else m / 1000.0
    fun convert(value: Double, from: String, to: String): Double? {
        if (key(from) == key(to)) return value
        val massFrom = massGrams(value, from)
        val massTo = massGrams(1.0, to)
        if (massFrom != null && massTo != null) return massFrom / massTo
        val volumeFrom = volumeMl(value, from)
        val volumeTo = volumeMl(1.0, to)
        if (volumeFrom != null && volumeTo != null) return volumeFrom / volumeTo
        return null
    }
    fun nutrientUnit(code: String): String = when {
        code == "energy_kcal" -> "kcal"
        code.endsWith("_mg") -> "mg"
        code.endsWith("_ug") -> "ug"
        code.endsWith("_g") -> "g"
        else -> ""
    }
    fun normalizeNutrient(code: String, value: Double, unit: String): Pair<String, Double> = when {
        code == "energy_kj" && unit.equals("kJ", true) -> "energy_kcal" to value / 4.184
        code == "salt_g" && unit == "g" -> "sodium_mg" to value * 400.0
        else -> code to value
    }
}
