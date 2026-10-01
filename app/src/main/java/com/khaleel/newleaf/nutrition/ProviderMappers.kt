package com.khaleel.newleaf.nutrition

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.khaleel.newleaf.data.EntryKind
import com.khaleel.newleaf.data.Nutrition
import com.khaleel.newleaf.data.PortionDefinition
import com.khaleel.newleaf.data.SourceReference
import com.khaleel.newleaf.data.Units
import java.time.Instant

internal fun JsonObject.text(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.let {
    if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) null else it.asString.trim().takeIf(String::isNotEmpty)
}
internal fun JsonObject.number(key: String): Double? = get(key)?.takeUnless { it.isJsonNull }?.let {
    if (!it.isJsonPrimitive) null else it.asString.toDoubleOrNull()?.takeIf(Double::isFinite)
}
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
internal fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()
private fun malformed(): Nothing = throw NutritionException(NutritionError.MALFORMED_RESPONSE)
private fun positive(n: Double?): Double? = n?.takeIf { it > 0 }
private fun tags(value: JsonElement?): List<String> = when {
    value == null -> emptyList()
    value.isJsonArray -> value.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else if (it.isJsonObject) it.asJsonObject.text("id") else null }
    value.isJsonObject -> value.asJsonObject.keySet().toList()
    else -> emptyList()
}
private fun isDrink(name: String): Boolean {
    val n = name.lowercase().trim()
    if (Regex("\\b(powder|flour|chocolate|ice cream|sauce|yogurt|cheese|cake|cookie|bread|butter)\\b").containsMatchIn(n)) return false
    return Regex("^(water|coffee|tea|milk|juice|soda|beer|wine|smoothie|lemonade|espresso|cappuccino|latte|cola)\\b|\\b(juice|beverage|drink|soda|coffee|tea|water)\\b").containsMatchIn(n)
}

private class Values {
    val values = Nutrition.coreCodes.associateWith<String, Double?> { null }.toMutableMap()
    val units = mutableMapOf<String, String>()
    val codes = mutableMapOf<String, String>()
    val estimated = mutableSetOf<String>()
    val originals = mutableMapOf<String, String>()
    fun put(code: String, value: Double?, unit: String, providerCode: String, inferred: Boolean = false) {
        values[code] = value
        units[code] = unit
        codes[code] = providerCode
        if (inferred) estimated.add(code)
    }
}

object ProviderMappers {
    private val usdaCodes = mapOf("1008" to "energy_kcal", "1003" to "protein_g", "1005" to "carbohydrate_g", "1004" to "fat_g",
        "1258" to "saturated_fat_g", "1079" to "fiber_g", "2000" to "sugar_g", "1063" to "sugar_g", "1093" to "sodium_mg")
    private val nxCodes = mapOf("208" to "energy_kcal", "203" to "protein_g", "205" to "carbohydrate_g", "204" to "fat_g",
        "606" to "saturated_fat_g", "291" to "fiber_g", "269" to "sugar_g", "307" to "sodium_mg")
    // Numeric attribute/unit facts from the official Nutritionix nutrient dictionary (2026-10-01).
    private val nxUnits = mapOf("301" to "mg", "601" to "mg", "605" to "g", "303" to "mg", "306" to "mg",
        "539" to "g", "324" to "IU", "221" to "g", "207" to "g", "262" to "mg", "268" to "kJ",
        "304" to "mg", "305" to "mg", "309" to "mg", "312" to "mg", "315" to "mg", "317" to "ug",
        "401" to "mg", "404" to "mg", "405" to "mg", "406" to "mg", "410" to "mg", "415" to "mg",
        "418" to "ug", "328" to "ug", "430" to "ug", "318" to "IU", "320" to "ug", "323" to "mg",
        "255" to "g", "209" to "g", "210" to "g", "211" to "g", "212" to "g", "213" to "g", "214" to "g")
    private fun normalize(value: Double?, from: String, to: String): Double? {
        if (value == null) return null
        if (from.equals(to, true)) return value
        if (to == "kcal" && from.equals("kJ", true)) return value / 4.184
        val gram = Units.massGrams(value, from)
        val target = Units.massGrams(1.0, to)
        return if (gram != null && target != null) gram / target else null
    }

    fun usda(food: JsonObject, summary: Boolean = false): FoodCandidate {
        val id = food.number("fdcId")?.let { if (it > 0 && it == it.toLong().toDouble()) it.toLong().toString() else null } ?: malformed()
        val name = food.text("description") ?: malformed()
        val type = food.text("dataType") ?: malformed()
        val size = positive(food.number("servingSize"))
        val servingUnit = food.text("servingSizeUnit").orEmpty()
        val basis = if (type == "Branded") when {
            Units.volumeMl(1.0, servingUnit) != null -> NutrientBasis.PER_100_ML
            Units.massGrams(1.0, servingUnit) != null -> NutrientBasis.PER_100_G
            summary -> NutrientBasis.UNKNOWN
            else -> malformed()
        } else if (type in setOf("Foundation", "SR Legacy", "Survey (FNDDS)", "Experimental")) NutrientBasis.PER_100_G else malformed()
        val v = Values()
        for (element in food.array("foodNutrients")) {
            if (!element.isJsonObject) malformed()
            val row = element.asJsonObject
            val nutrient = row.obj("nutrient") ?: row
            val nid = (nutrient.number("id") ?: row.number("nutrientId"))?.toLong()?.toString() ?: continue
            val nutrientUnit = nutrient.text("unitName") ?: continue
            val value = if (basis == NutrientBasis.UNKNOWN) null else row.number("amount") ?: row.number("value")
            v.originals["provider_nutrient_usda_$nid"] = "${row.number("amount") ?: row.number("value") ?: "null"} $nutrientUnit"
            val derivation = row.obj("foodNutrientDerivation")?.text("description") ?: row.text("derivationDescription")
            if (derivation != null) v.originals["provider_derivation_usda_$nid"] = derivation
            val inferred = derivation?.lowercase()?.let { "calculated" in it || "imputed" in it || "estimated" in it } ?: false
            val code = usdaCodes[nid]
            if (code != null) v.put(code, normalize(value, nutrientUnit, Units.nutrientUnit(code)), Units.nutrientUnit(code), "usda:$nid", inferred)
            else v.put("usda_${nid}_${nutrientUnit.lowercase()}", value, nutrientUnit, "usda:$nid", inferred)
        }
        if (v.values["energy_kcal"] == null) {
            // USDA energy kJ or Atwater values are explicit nutrient values, never calculated from macros here.
            for (nid in listOf("2048", "2047", "1062")) {
                val row = food.array("foodNutrients").firstOrNull { it.isJsonObject && ((it.asJsonObject.obj("nutrient")?.number("id") ?: it.asJsonObject.number("nutrientId"))?.toLong()?.toString() == nid) }?.asJsonObject ?: continue
                val unit = (row.obj("nutrient") ?: row).text("unitName") ?: continue
                val energy = normalize(row.number("amount") ?: row.number("value"), unit, "kcal")
                if (energy != null && basis != NutrientBasis.UNKNOWN) { v.put("energy_kcal", energy, "kcal", "usda:$nid", nid != "1062"); break }
            }
        }
        val portions = food.array("foodPortions").mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val p = element.asJsonObject
            val amount = positive(p.number("amount")) ?: return@mapNotNull null
            val grams = positive(p.number("gramWeight")) ?: return@mapNotNull null
            val rawLabel = p.obj("measureUnit")?.text("name")?.takeUnless { it.equals("undetermined", true) }
                ?: p.text("modifier") ?: p.text("portionDescription") ?: return@mapNotNull null
            val prefix = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s+(.+)$").matchEntire(rawLabel)
            val label = if (prefix?.groupValues?.get(1)?.toDoubleOrNull() == amount) prefix!!.groupValues[2] else rawLabel
            PortionDefinition(label, amount, grams)
        }.toMutableList()
        val servingMass = size?.let { Units.massGrams(it, servingUnit) }
        val servingVolume = size?.let { Units.volumeMl(it, servingUnit) }
        if (servingMass != null || servingVolume != null) portions.add(PortionDefinition("serving", 1.0, servingMass, servingVolume))
        val sourceId = "usda:$id"
        return FoodCandidate(name, brand = food.text("brandName") ?: food.text("brandOwner"),
            quantity = if (basis == NutrientBasis.PER_100_ML || basis == NutrientBasis.PER_100_G) 100.0 else null,
            unit = if (basis == NutrientBasis.PER_100_ML) "mL" else "g", nutrients = v.values,
            basis = basis, servingMassG = servingMass, servingVolumeMl = servingVolume, portions = portions,
            source = "USDA FoodData Central", sourceId = sourceId, region = food.text("marketCountry") ?: if (type != "Branded") "US" else null,
            kind = if (basis == NutrientBasis.PER_100_ML || isDrink(name)) EntryKind.DRINK else EntryKind.FOOD,
            sourceReference = SourceReference(provider = "USDA FoodData Central", itemId = sourceId, retrievedAt = Instant.now().toString(),
                attribution = "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central.", license = "CC0-1.0"),
            nutrientUnits = v.units, nutrientProviderCodes = v.codes, estimatedNutrients = v.estimated,
            metadata = v.originals + mapOf("data_type" to type, "source_url" to "https://fdc.nal.usda.gov/food-details/$id/nutrients"), needsDetails = summary)
    }

    /** Local/gateway hints are provisional. Missing matches remain editable unknown candidates. */
    fun usdaDescription(item: JsonObject, originalText: String): FoodCandidate {
        val query = item.text("query") ?: malformed()
        val fragment = item.text("input_text") ?: malformed()
        val unit = item.text("unit_hint") ?: malformed()
        val status = item.text("match_status") ?: malformed()
        fun flag(key: String): Boolean = item.get(key)?.takeIf {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
        }?.asBoolean ?: malformed()
        if (!flag("match_estimated") || !flag("quantity_estimated")) malformed()
        val recognized = flag("unit_recognized")
        val needsDetails = flag("needs_details")
        val quantityField = item.get("quantity_hint") ?: malformed()
        if (!quantityField.isJsonNull && (!quantityField.isJsonPrimitive || !quantityField.asJsonPrimitive.isNumber)) malformed()
        val quantity = if (quantityField.isJsonNull) null else (positive(item.number("quantity_hint")) ?: malformed())
        if (quantity != null && quantity > 1000000.0) malformed()
        val foodField = item.get("food") ?: malformed()
        val base = if (foodField.isJsonNull) {
            if (status != "no_match" || needsDetails) malformed()
            FoodCandidate(name = query, nutrients = Nutrition.coreCodes.associateWith { null },
                basis = NutrientBasis.UNKNOWN, source = "USDA FoodData Central", quantity = null,
                sourceReference = SourceReference(provider = "USDA FoodData Central",
                    attribution = "USDA-assisted search: no matching database record; nutrition unknown.", license = "CC0-1.0"),
                kind = if (isDrink(query)) EntryKind.DRINK else EntryKind.FOOD)
        } else {
            if (!foodField.isJsonObject || status != "provisional") malformed()
            // A missing branded basis stays unknown rather than guessing from the text.
            usda(foodField.asJsonObject, summary = true).copy(needsDetails = needsDetails)
        }
        return base.copy(quantity = quantity, unit = unit, estimated = true, originalText = originalText,
            matchStatus = if (status == "no_match") "no_match" else "unreviewed", metadata = base.metadata + mapOf(
                "description_mode" to "usda_assisted", "description_item_input" to fragment,
                "description_query" to query, "match_estimated" to "true", "quantity_estimated" to "true",
                "quantity_hint_status" to if (quantity == null) "unspecified" else "parsed_unreviewed",
                "unit_recognized" to recognized.toString(), "review_required" to "true"))
    }

    /** Historical response import only. No active client or gateway requests Nutritionix. */
    fun nutritionix(food: JsonObject, originalText: String): FoodCandidate {
        val name = food.text("food_name") ?: malformed()
        val q = positive(food.number("serving_qty")) ?: malformed()
        val unit = food.text("serving_unit") ?: malformed()
        val totalMass = positive(food.number("serving_weight_grams"))
        val v = Values()
        val fields = mapOf("nf_calories" to "energy_kcal", "nf_protein" to "protein_g", "nf_total_carbohydrate" to "carbohydrate_g",
            "nf_total_fat" to "fat_g", "nf_saturated_fat" to "saturated_fat_g", "nf_dietary_fiber" to "fiber_g", "nf_sugars" to "sugar_g", "nf_sodium" to "sodium_mg",
            "nf_cholesterol" to "cholesterol_mg", "nf_potassium" to "potassium_mg", "nf_p" to "nutritionix_nf_p")
        fields.forEach { (field, code) ->
            v.put(code, food.number(field), if (field == "nf_p") "provider-defined" else Units.nutrientUnit(code), "nutritionix:$field")
            v.originals["provider_nutrient_$field"] = "${food.number(field) ?: "null"} ${Units.nutrientUnit(code)}"
        }
        food.array("full_nutrients").forEach { element ->
            if (!element.isJsonObject) malformed()
            val row = element.asJsonObject
            val attr = row.number("attr_id")?.toLong()?.toString() ?: return@forEach
            v.originals["provider_nutrient_nutritionix_$attr"] = "${row.number("value") ?: "null"} ${nxUnits[attr] ?: nxCodes[attr]?.let(Units::nutrientUnit) ?: "provider-defined"}"
            val code = nxCodes[attr]
            if (code == null) v.put("nutritionix_attr_$attr", row.number("value"), nxUnits[attr] ?: "provider-defined", "nutritionix:$attr")
            else if (v.values[code] == null) v.put(code, row.number("value"), Units.nutrientUnit(code), "nutritionix:$attr")
        }
        val portions = food.array("alt_measures").mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val p = element.asJsonObject
            val label = p.text("measure") ?: return@mapNotNull null
            val count = positive(p.number("qty")) ?: return@mapNotNull null
            val mass = positive(p.number("serving_weight")) ?: return@mapNotNull null
            PortionDefinition(label, count, mass)
        }.toMutableList()
        if (totalMass != null) portions.add(0, PortionDefinition(unit, q, totalMass))
        val id = food.text("nix_item_id")?.let { "nutritionix:$it" } ?: (food.obj("tags")?.number("tag_id") ?: food.number("tag_id"))?.toLong()?.let { "nutritionix:tag:$it" }
            ?: food.number("ndb_no")?.toLong()?.let { "nutritionix:ndb:$it" }
        return FoodCandidate(name, brand = food.text("brand_name"), quantity = q, unit = unit,
            nutrients = v.values, basis = NutrientBasis.CONSUMED, basisQuantity = q, basisUnit = unit,
            servingMassG = totalMass?.div(q), servingVolumeMl = Units.volumeMl(1.0, unit), portions = portions,
            source = "Nutritionix", sourceId = id, region = null, estimated = true,
            kind = if (Units.volumeMl(1.0, unit) != null || isDrink(name)) EntryKind.DRINK else EntryKind.FOOD,
            originalText = originalText, sourceReference = SourceReference(provider = "Nutritionix", itemId = id,
                retrievedAt = Instant.now().toString(), attribution = "Nutritionix / Syndigo", license = "account-specific contract; server rights gate",
                exportAllowed = true, snapshotAllowed = true), // gateway refuses this request unless owner confirmed rights
            nutrientUnits = v.units, nutrientProviderCodes = v.codes,
            metadata = v.originals + mapOf("portion_inference" to "provider does not expose supplied-versus-inferred evidence",
                "input_scope" to "full description; per-item original fragment not returned", "provider_region" to "not reported"))
    }

    fun openFoodFacts(response: JsonObject, barcode: String): FoodCandidate? {
        val result = response.obj("result")?.text("id").orEmpty()
        if (response.text("status") == "failure") {
            if (result in setOf("product_not_found", "en:product-not-found", "product-not-found")) return null
            malformed()
        }
        val product = response.obj("product") ?: malformed()
        val returnedCode = product.text("code") ?: response.text("code") ?: malformed()
        if (returnedCode.padStart(14, '0') != barcode.padStart(14, '0')) malformed()
        val name = product.text("product_name") ?: product.text("product_name_en") ?: product.text("generic_name") ?: "Product $barcode"
        // Deliberately ignore aggregated_set: it stitches nutrition from multiple origins.
        val inputSets = product.obj("nutrition")?.array("input_sets") ?: JsonArray()
        val candidates = inputSets.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .filter { it.text("preparation") == "as_sold" && it.text("source") in setOf("manufacturer", "packaging") }
            .sortedBy { if (it.text("source") == "manufacturer") 0 else 1 }
        val chosen = candidates.firstOrNull { it.text("per") in setOf("100g", "100ml", "serving") }
        // A recognized product is still useful when its nutrition cannot be safely
        // consumed. Preserve identity; never substitute prepared/aggregate values.
        val nutritionStatus = when {
            chosen != null -> "supported_input_set"
            inputSets.size() > 0 -> "unsupported_input_sets"
            else -> "missing_input_sets"
        }
        val basis = when(chosen?.text("per")) { "100g" -> NutrientBasis.PER_100_G; "100ml" -> NutrientBasis.PER_100_ML; "serving" -> NutrientBasis.PER_SERVING; else -> NutrientBasis.UNKNOWN }
        val v = Values()
        val nutr = chosen?.obj("nutrients") ?: JsonObject()
        val mapping = mapOf("energy-kcal" to "energy_kcal", "proteins" to "protein_g", "carbohydrates-total" to "carbohydrate_g", "fat" to "fat_g",
            "saturated-fat" to "saturated_fat_g", "fiber" to "fiber_g", "sugars" to "sugar_g", "sodium" to "sodium_mg")
        for ((providerCode, value) in nutr.entrySet()) {
            if (!value.isJsonObject) malformed()
            val row = value.asJsonObject
            val raw = row.number("value") // computed estimates kept separate; never substitute default zero
            val rawUnit = row.text("unit").orEmpty()
            val modifier = row.text("modifier")
            v.originals["provider_nutrient_off_$providerCode"] = "${modifier.orEmpty()}${raw ?: "null"} $rawUnit"
            val code = mapping[providerCode]
            if (code == null) v.put("off_${providerCode.replace('-', '_')}", if (modifier in setOf("<", "<=", ">", ">=")) null else raw, rawUnit, "off:$providerCode", modifier != null)
            else v.put(code, if (modifier in setOf("<", "<=", ">", ">=")) null else normalize(raw, rawUnit, Units.nutrientUnit(code)), Units.nutrientUnit(code), "off:$providerCode", modifier != null)
        }
        if (v.values["energy_kcal"] == null) {
            val kj = nutr.obj("energy-kj") ?: nutr.obj("energy")
            if (kj != null && kj.text("modifier") == null) v.put("energy_kcal", normalize(kj.number("value"), kj.text("unit").orEmpty(), "kcal"), "kcal", "off:energy-kj")
        }
        // Preserve available-vs-total carbohydrates instead of equating net carbs with US total.
        if (v.values["carbohydrate_g"] == null) {
            val available = nutr.obj("carbohydrates")
            val availableG = available?.let { normalize(it.number("value"), it.text("unit").orEmpty(), "g") }
            if (availableG != null && available?.text("modifier") == null && v.values["fiber_g"] != null) {
                v.put("carbohydrate_g", availableG + v.values.getValue("fiber_g")!!, "g", "off:carbohydrates+fiber", true)
            }
        }
        if (v.values["sodium_mg"] == null) {
            val salt = nutr.obj("salt")
            if (salt != null && salt.text("modifier") == null) {
                val saltG = normalize(salt.number("value"), salt.text("unit").orEmpty(), "g")
                v.put("sodium_mg", saltG?.times(400.0), "mg", "off:salt/2.5", true)
            }
        }
        // A per-100-g/mL set often has a companion serving definition. Use only
        // matching origin/preparation measurements, never its nutrient values.
        // Conflicting serving measurements leave the conversion unknown.
        val servingDefinitions = candidates.filter {
            it.text("source") == chosen?.text("source") && it.text("per") == "serving"
        }.mapNotNull {
            val quantity = positive(it.number("per_quantity")) ?: return@mapNotNull null
            val unit = it.text("per_unit").orEmpty()
            val mass = Units.massGrams(quantity, unit)
            val volume = Units.volumeMl(quantity, unit)
            if (mass == null && volume == null) null else mass to volume
        }.distinct()
        val servingDefinition = if (basis == NutrientBasis.PER_SERVING) {
            val quantity = positive(chosen?.number("per_quantity"))
            val unit = chosen?.text("per_unit").orEmpty()
            quantity?.let { Units.massGrams(it, unit) to Units.volumeMl(it, unit) }
        } else servingDefinitions.singleOrNull()
        val servingMass = servingDefinition?.first
        val servingVolume = servingDefinition?.second
        val categories = tags(product.get("categories_tags"))
        val sourceId = "openfoodfacts:$returnedCode"
        return FoodCandidate(name, brand = product.text("brands"), quantity = when(basis) { NutrientBasis.PER_100_G, NutrientBasis.PER_100_ML -> 100.0; NutrientBasis.PER_SERVING -> 1.0; else -> null },
            unit = when(basis) { NutrientBasis.PER_100_G -> "g"; NutrientBasis.PER_100_ML -> "mL"; else -> "serving" },
            nutrients = v.values, basis = basis, servingMassG = servingMass, servingVolumeMl = servingVolume,
            portions = if (servingMass != null || servingVolume != null) listOf(PortionDefinition("serving", 1.0, servingMass, servingVolume)) else emptyList(),
            source = "Open Food Facts", sourceId = sourceId, barcode = barcode,
            region = tags(product.get("countries_tags")).takeIf { it.isNotEmpty() }?.joinToString(","),
            kind = if (basis == NutrientBasis.PER_100_ML || servingVolume != null || "en:beverages" in categories || isDrink(name)) EntryKind.DRINK else EntryKind.FOOD,
            sourceReference = SourceReference(provider = "Open Food Facts", itemId = sourceId, barcode = barcode, retrievedAt = Instant.now().toString(),
                attribution = "Contains information from Open Food Facts contributors (https://world.openfoodfacts.org/), made available under the Open Database License (ODbL).",
                license = "ODbL-1.0 database: https://opendatacommons.org/licenses/odbl/1-0/; DbCL-1.0 contents: https://opendatacommons.org/licenses/dbcl/1-0/",
                exportAllowed = true, snapshotAllowed = true),
            nutrientUnits = v.units, nutrientProviderCodes = v.codes, estimatedNutrients = v.estimated, preparation = chosen?.text("preparation").orEmpty(),
            metadata = v.originals + mapOf("off_nutrition_source" to chosen?.text("source").orEmpty(), "off_nutrition_status" to nutritionStatus, "source_url" to "https://world.openfoodfacts.org/product/$returnedCode",
                "rights_status" to "Retention/export permitted under published ODbL/DbCL; preserve notices and independently licensed nutrition subset",
                "off_license_url" to "https://opendatacommons.org/licenses/odbl/1-0/",
                "off_contents_license_url" to "https://opendatacommons.org/licenses/dbcl/1-0/",
                "off_database_url" to "https://world.openfoodfacts.org/", "match_confidence" to "exact barcode; no numeric confidence supplied"), matchStatus = "unreviewed")
    }
}
