package com.khaleel.newleaf.data

enum class Completeness { EMPTY, PARTIAL, COMPLETE }
data class NutrientCoverage(val knownSum: Double?, val totalContributors: Int, val knownContributors: Int,
                            val unknownContributors: Int, val estimatedContributors: Int, val completeness: Completeness, val partialContributors: Int = 0)

object Nutrition {
    val coreCodes = listOf("energy_kcal", "protein_g", "carbohydrate_g", "fat_g", "saturated_fat_g", "fiber_g", "sugar_g", "sodium_mg")
    fun contributors(entries: List<Entry>): List<Entry> = entries.filter { !it.isLibrary && it.kind in setOf(EntryKind.FOOD, EntryKind.DRINK) }
    fun aggregate(entries: List<Entry>, code: String): NutrientCoverage {
        val known = entries.mapNotNull { it.nutrients[code] }
        val unknown = entries.size - known.size
        val partial = entries.count { entry -> entry.nutrients[code] != null && entry.nutrientEvidence[code]?.let { it.coverageKnown != null && it.coverageTotal != null && it.coverageKnown < it.coverageTotal } == true }
        return NutrientCoverage(if (known.isEmpty()) null else known.sum(), entries.size, known.size, unknown,
            entries.count { it.nutrients[code] != null && (it.nutrientEvidence[code]?.status == "estimated" || it.portionEvidence == "estimated") },
            when { entries.isEmpty() -> Completeness.EMPTY; unknown > 0 || partial > 0 -> Completeness.PARTIAL; else -> Completeness.COMPLETE }, partial)
    }
    fun all(entries: List<Entry>): Map<String, NutrientCoverage> = (coreCodes + entries.flatMap { it.nutrients.keys }).distinct().associateWith { aggregate(entries, it) }

    /** Reference values must declare their basis. Unknown conversions preserve unknown nutrition. */
    fun scalePortion(reference: Entry, quantity: Double, unit: String): Entry {
        require(quantity.isFinite() && quantity > 0)
        require(reference.metadata["nutrition_scale"] == null) { "This snapshot is already scaled; use its original reference basis to rescale" }
        val basis = reference.basisQuantity
        require(basis != null && basis.isFinite() && basis > 0) { "Nutrition reference requires a positive explicit basis" }
        val definition = reference.portionDefinitions.firstOrNull { it.label.equals(unit, true) }
        val converted = Units.convert(quantity, unit, reference.basisUnit) ?: definition?.let { portion ->
            val count = quantity / portion.quantity
            when {
                portion.massGrams != null -> Units.convert(count * portion.massGrams, "g", reference.basisUnit)
                portion.volumeMl != null -> Units.convert(count * portion.volumeMl, "ml", reference.basisUnit)
                else -> null
            }
        }
        val factor = converted?.div(basis)
        return reference.copy(quantity = quantity, unit = unit, nutrients = reference.nutrients.mapValues { (_, value) -> if (factor == null) null else value?.times(factor) },
            massGrams = Units.massGrams(quantity, unit) ?: definition?.massGrams?.times(quantity / definition.quantity),
            volumeMl = Units.volumeMl(quantity, unit) ?: definition?.volumeMl?.times(quantity / definition.quantity),
            nutrientEvidence = reference.nutrients.keys.associateWith { code ->
                val evidence = reference.nutrientEvidence[code] ?: NutrientEvidence(unit = Units.nutrientUnit(code))
                evidence.copy(status = if (factor == null || reference.nutrients[code] == null) "unknown" else evidence.status,
                    basis = "consumed; reference $basis ${reference.basisUnit}; scale=${factor ?: "unknown"}")
            }, metadata = reference.metadata + ("nutrition_scale" to (factor?.toString() ?: "unknown")))
    }
    fun composeDrink(drink: Entry, components: List<Entry>): Entry {
        require(drink.kind == EntryKind.DRINK)
        val all = listOf(drink.copy(composition = emptyList())) + components
        val totals = all(all)
        return drink.copy(metadata = drink.metadata + mapOf(
            "drink_base_nutrients" to JournalCodec.toJson(drink.nutrients),
            "drink_base_evidence" to JournalCodec.toJson(drink.nutrientEvidence)),
            composition = components.map { JournalCodec.entry(JournalCodec.toJson(it)).copy(isLibrary = true) }, nutrients = totals.mapValues { it.value.knownSum },
            nutrientEvidence = totals.mapValues { (code, coverage) -> NutrientEvidence(
                status = when { coverage.knownSum == null -> "unknown"; coverage.estimatedContributors > 0 -> "estimated"; else -> "known" }, origin = "composition", unit = Units.nutrientUnit(code),
                basis = "consumed; known=${coverage.knownContributors}/${coverage.totalContributors}",
                coverageKnown = coverage.knownContributors - coverage.partialContributors, coverageTotal = coverage.totalContributors) })
    }
}
