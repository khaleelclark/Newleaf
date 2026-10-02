package com.khaleel.newleaf.nutrition

import com.khaleel.newleaf.data.Entry
import com.khaleel.newleaf.data.EntryKind
import com.khaleel.newleaf.data.NutrientEvidence
import com.khaleel.newleaf.data.PortionDefinition
import com.khaleel.newleaf.data.SourceReference
import com.khaleel.newleaf.data.Units

enum class NutrientBasis { PER_100_G, PER_100_ML, PER_SERVING, CONSUMED, UNKNOWN }

data class FoodCandidate(
    val name: String,
    val brand: String? = null,
    val quantity: Double? = null,
    val unit: String = "g",
    val nutrients: Map<String, Double?>,
    val basis: NutrientBasis,
    val basisQuantity: Double? = when (basis) { NutrientBasis.PER_100_G, NutrientBasis.PER_100_ML -> 100.0; NutrientBasis.PER_SERVING -> 1.0; else -> quantity },
    val basisUnit: String = when (basis) { NutrientBasis.PER_100_G -> "g"; NutrientBasis.PER_100_ML -> "mL"; NutrientBasis.PER_SERVING -> "serving"; else -> unit },
    /** Weight/volume of ONE source serving unit, not of the whole parsed quantity. */
    val servingMassG: Double? = null,
    val servingVolumeMl: Double? = null,
    val portions: List<PortionDefinition> = emptyList(),
    val source: String,
    val sourceId: String? = null,
    val barcode: String? = null,
    val region: String? = null,
    val estimated: Boolean = false,
    val kind: EntryKind = EntryKind.FOOD,
    val originalText: String = "",
    val sourceReference: SourceReference,
    val nutrientUnits: Map<String, String> = emptyMap(),
    val nutrientProviderCodes: Map<String, String> = emptyMap(),
    val estimatedNutrients: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val preparation: String = "",
    val matchStatus: String = "unreviewed",
    val confidence: Double? = null,
    val needsDetails: Boolean = false
) {
    init {
        require(name.isNotBlank())
        require(nutrients.values.all { it == null || it.isFinite() })
        require(basisQuantity == null || basisQuantity.isFinite() && basisQuantity > 0)
        require(servingMassG == null || servingMassG.isFinite() && servingMassG > 0)
        require(servingVolumeMl == null || servingVolumeMl.isFinite() && servingVolumeMl > 0)
        require(portions.all { it.quantity.isFinite() && it.quantity > 0 && (it.massGrams == null || it.massGrams.isFinite() && it.massGrams > 0) && (it.volumeMl == null || it.volumeMl.isFinite() && it.volumeMl > 0) })
        when (basis) {
            NutrientBasis.UNKNOWN -> require(nutrients.values.all { it == null }) { "Unknown nutrient basis cannot carry known values" }
            NutrientBasis.PER_100_G -> require(basisQuantity == 100.0 && basisUnit == "g")
            NutrientBasis.PER_100_ML -> require(basisQuantity == 100.0 && basisUnit.equals("mL", true))
            NutrientBasis.PER_SERVING -> require(basisQuantity == 1.0 && basisUnit == "serving")
            NutrientBasis.CONSUMED -> require(basisQuantity != null && basisUnit.isNotBlank())
        }
    }
    val incompleteNutrition: Boolean get() = com.khaleel.newleaf.data.Nutrition.coreCodes.any { nutrients[it] == null }
    private fun same(a: String, b: String) = a.trim().equals(b.trim(), true)
    private fun referencePortion(unit: String): PortionDefinition? = portions.firstOrNull { same(it.label, unit) }
    private fun mass(quantity: Double, unit: String): Double? = (Units.massGrams(quantity, unit)
        ?: referencePortion(unit)?.let { it.massGrams?.times(quantity / it.quantity) }
        ?: (if (servingMassG != null && servingVolumeMl != null) Units.volumeMl(quantity, unit)?.times(servingMassG / servingVolumeMl) else null)
        ?: (if (same(unit, basisUnit) || same(unit, "serving")) servingMassG?.times(quantity) else null)).takeIf { it == null || it.isFinite() }
    private fun volume(quantity: Double, unit: String): Double? = (Units.volumeMl(quantity, unit)
        ?: referencePortion(unit)?.let { it.volumeMl?.times(quantity / it.quantity) }
        ?: (if (servingMassG != null && servingVolumeMl != null) Units.massGrams(quantity, unit)?.times(servingVolumeMl / servingMassG) else null)
        ?: (if (same(unit, basisUnit) || same(unit, "serving")) servingVolumeMl?.times(quantity) else null)).takeIf { it == null || it.isFinite() }

    /** Convert a reviewed amount using explicit package/serving measurements only. */
    fun convertAmount(quantity: Double, from: String, to: String): Double? {
        if (!quantity.isFinite() || quantity <= 0) return null
        Units.convert(quantity, from, to)?.let { return it.takeIf { n -> n.isFinite() && n > 0 } }
        val targetMass = mass(1.0, to)
        val targetVolume = volume(1.0, to)
        val sourceMass = mass(quantity, from)
        val sourceVolume = volume(quantity, from)
        return (when {
            targetMass != null && sourceMass != null -> sourceMass / targetMass
            targetVolume != null && sourceVolume != null -> sourceVolume / targetVolume
            else -> null
        }).takeIf { it != null && it.isFinite() && it > 0 }
    }

    fun toEntry(quantity: Double? = this.quantity, unit: String = this.unit, kind: EntryKind = this.kind): Entry {
        require(kind == EntryKind.FOOD || kind == EntryKind.DRINK)
        require(quantity == null || quantity.isFinite() && quantity > 0) { "Portion must be positive" }
        val basisQ = basisQuantity?.takeIf { it.isFinite() && it > 0 }
        val factor = (if (quantity == null || basisQ == null) null else when (basis) {
            NutrientBasis.UNKNOWN -> null
            NutrientBasis.PER_100_G -> mass(quantity, unit)?.div(100.0)
            NutrientBasis.PER_100_ML -> volume(quantity, unit)?.div(100.0)
            NutrientBasis.PER_SERVING, NutrientBasis.CONSUMED -> {
                val directly = Units.convert(quantity, unit, basisUnit)
                val massBasis = mass(basisQ, basisUnit)
                val volumeBasis = volume(basisQ, basisUnit)
                val massInput = mass(quantity, unit)
                val volumeInput = volume(quantity, unit)
                when {
                    directly != null -> directly / basisQ
                    massBasis != null && massInput != null -> massInput / massBasis
                    volumeBasis != null && volumeInput != null -> volumeInput / volumeBasis
                    else -> null
                }
            }
        }
        ).takeIf { it == null || it.isFinite() }
        val scaled = nutrients.mapValues { (_, value) -> if (factor == null) null else value?.times(factor)?.takeIf { it.isFinite() } }
        val evidence = nutrients.keys.associateWith { code -> NutrientEvidence(
            status = if (scaled[code] == null) "unknown" else if (estimated || code in estimatedNutrients) "estimated" else "known",
            origin = "database_reference", unit = nutrientUnits[code] ?: Units.nutrientUnit(code),
            basis = "consumed; reference=$basisQ $basisUnit; scale=${factor ?: "unknown"}",
            providerCode = nutrientProviderCodes[code]) }
        return Entry(kind = kind, name = name, brand = brand.orEmpty(), preparation = preparation,
            quantity = quantity, unit = unit, nutrients = scaled, basisQuantity = basisQ, basisUnit = basisUnit,
            source = source, sourceId = sourceId, originalText = originalText,
            portionEvidence = if (quantity == null) "unspecified" else if (estimated) "estimated" else "unspecified",
            sourceReference = sourceReference, nutrientEvidence = evidence, portionDefinitions = portions,
            matchStatus = matchStatus, massGrams = quantity?.let { mass(it, unit) },
            volumeMl = quantity?.let { volume(it, unit) }, countsAsWater = kind == EntryKind.DRINK && name.trim().lowercase() in setOf("water", "plain water"),
            metadata = metadata + mapOf("nutrition_basis" to basis.name, "nutrition_scale" to (factor?.toString() ?: "unknown"),
                "region" to region.orEmpty(), "portion_inference" to if (estimated) "provider_amount_unverified" else "not_reported"))
    }
}
