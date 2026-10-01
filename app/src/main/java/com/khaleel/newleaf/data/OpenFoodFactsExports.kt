package com.khaleel.newleaf.data

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser

/** An independently extractable, narrowly scoped OFF-derived nutrition dataset. */
internal object OpenFoodFactsExports {
    const val DATABASE_URL = "https://world.openfoodfacts.org/"
    const val DATABASE_LICENSE_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
    const val CONTENTS_LICENSE_URL = "https://opendatacommons.org/licenses/dbcl/1-0/"
    const val SUBSET_FILE = "open_food_facts/nutrition.json"
    const val NOTICE_FILE = "open_food_facts/LICENSE.md"
    private fun isOff(entry: Entry): Boolean = entry.sourceReference?.provider?.trim()?.lowercase() in
        setOf("open food facts", "openfoodfacts", "off")

    fun subset(entries: List<Entry>): JsonObject {
        fun graph(entry: Entry): List<Entry> = listOf(entry) + entry.recipeSnapshot?.let(::graph).orEmpty() +
            entry.ingredientSnapshots.flatMap(::graph) + entry.composition.flatMap(::graph)
        val unique = linkedMapOf<String, JsonObject>()
        entries.flatMap(::graph).filter(::isOff).forEach { entry ->
            val source = requireNotNull(entry.sourceReference)
            val scale = entry.metadata["nutrition_scale"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            val values = if (entry.composition.isEmpty()) entry.nutrients else {
                val base = entry.metadata["drink_base_nutrients"]?.let { JsonParser.parseString(it).asJsonObject }
                entry.nutrients.keys.associateWith { code -> base?.get(code)?.takeIf { !it.isJsonNull }?.asDouble }
            }
            val referenceBasis = scale != null && entry.basisQuantity != null && entry.basisUnit.isNotBlank()
            val record = JsonObject().apply {
                addProperty("provider", "Open Food Facts")
                addProperty("provider_item_id", source.itemId)
                addProperty("barcode", source.barcode)
                addProperty("retrieved_at", source.retrievedAt)
                addProperty("attribution", "Open Food Facts contributors")
                addProperty("database_url", DATABASE_URL)
                addProperty("database_license_url", DATABASE_LICENSE_URL)
                addProperty("contents_license_url", CONTENTS_LICENSE_URL)
                addProperty("basis_type", if (referenceBasis) "source_reference" else "derived_nutrition_snapshot")
                addProperty("basis_quantity", if (referenceBasis) entry.basisQuantity else null)
                addProperty("basis_unit", if (referenceBasis) entry.basisUnit else null)
                add("nutrients", JsonArray().apply {
                    values.toSortedMap().forEach { (code, value) ->
                        val evidence = entry.nutrientEvidence[code]
                        // Personal override text/values do not enter the independently licensed subset.
                        if (code !in source.overrides && evidence?.origin != "user_override") add(JsonObject().apply {
                            addProperty("code", code)
                            val referenceValue = if (referenceBasis) value?.div(requireNotNull(scale)) else value
                            add("value", referenceValue?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                            addProperty("unit", evidence?.unit?.takeIf { it.isNotBlank() } ?: Units.nutrientUnit(code))
                            addProperty("provider_code", evidence?.providerCode)
                            addProperty("status", if (value == null) "unknown" else evidence?.status ?: "known")
                        })
                    }
                })
            }
            val digest = JournalExports.sha256(JournalCodec.toJson(record).toByteArray(Charsets.UTF_8))
            record.addProperty("snapshot_id", digest)
            unique[digest] = record
        }
        return JsonObject().apply {
            addProperty("schema_version", "1.0")
            add("notice", notice())
            add("nutrition_snapshots", JsonArray().apply { unique.toSortedMap().values.forEach(::add) })
        }
    }
    fun hasRecords(subset: JsonObject) = subset.getAsJsonArray("nutrition_snapshots").size() > 0
    fun notice(): JsonObject = JsonObject().apply {
        addProperty("source", "Open Food Facts contributors")
        addProperty("database_url", DATABASE_URL)
        addProperty("database_license", "ODbL-1.0")
        addProperty("database_license_url", DATABASE_LICENSE_URL)
        addProperty("contents_license", "DbCL-1.0")
        addProperty("contents_license_url", CONTENTS_LICENSE_URL)
        addProperty("scope", "The independently extractable OFF-derived nutrition subset; personal journal notes, photos, weight, events and drafts are outside this subset.")
        addProperty("changes", "Selected nutrient snapshots and documented portion scaling; reference basis reconstructed where scale is available; personal overrides excluded from this subset.")
    }
    fun markdownNotice(): String = """
        Open Food Facts-derived nutrition is attributed to [Open Food Facts contributors]($DATABASE_URL).
        The independently extractable nutrition subset is supplied under [ODbL 1.0]($DATABASE_LICENSE_URL)
        for the database and [DbCL 1.0]($CONTENTS_LICENSE_URL) for its contents.
        Archive recipients can extract `$SUBSET_FILE` independently with this notice.
        This notice covers that subset and does not assign those licenses to private journal notes,
        photos, body weight, events or drafts. It is not a certification of unrestricted reuse.
        Changes comprise selected nutrient snapshots and portion calculations; reference basis is
        reconstructed where available. Personal override text/values are excluded from the subset.
    """.trimIndent()
}
