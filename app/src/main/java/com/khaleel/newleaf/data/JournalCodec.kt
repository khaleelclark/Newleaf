package com.khaleel.newleaf.data

import com.google.gson.*

/** Constructor defaults are restored explicitly: Gson otherwise bypasses Kotlin defaults. */
internal object JournalCodec {
    private val plain = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val children = GsonBuilder().serializeNulls().disableHtmlEscaping()
        .registerTypeAdapter(SourceReference::class.java, JsonDeserializer<SourceReference> { json, _, _ ->
            plain.fromJson(merge(plain.toJsonTree(SourceReference()).asJsonObject, json.asJsonObject), SourceReference::class.java)
        })
        .registerTypeAdapter(NutrientEvidence::class.java, JsonDeserializer<NutrientEvidence> { json, _, _ ->
            plain.fromJson(merge(plain.toJsonTree(NutrientEvidence()).asJsonObject, json.asJsonObject), NutrientEvidence::class.java)
        })
        .registerTypeAdapter(PortionDefinition::class.java, JsonDeserializer<PortionDefinition> { json, _, _ ->
            plain.fromJson(merge(plain.toJsonTree(PortionDefinition(label = json.asJsonObject["label"].asString)).asJsonObject, json.asJsonObject), PortionDefinition::class.java)
        }).create()
    val gson: Gson = children.newBuilder()
        .registerTypeAdapter(Entry::class.java, JsonDeserializer<Entry> { json, _, _ -> decodeEntry(json.asJsonObject) })
        .registerTypeAdapter(AppSettings::class.java, JsonDeserializer<AppSettings> { json, _, _ -> decodeSettings(json.asJsonObject) })
        .create()
    val pretty: Gson = gson.newBuilder().setPrettyPrinting().create()
    private fun merge(defaults: JsonObject, input: JsonObject): JsonObject {
        val result = defaults.deepCopy()
        val extra = (input.get("extensions") as? JsonObject)?.deepCopy() ?: JsonObject()
        input.entrySet().forEach { (key, value) ->
            if (key in defaults.keySet()) {
                require(!value.isJsonNull || defaults[key].isJsonNull) { "$key cannot be null" }
                result.add(key, value.deepCopy())
            } else extra.add(key, value.deepCopy())
        }
        result.add("extensions", extra)
        return result
    }
    fun decodeEntry(input: JsonObject, depth: Int = 0): Entry {
        require(depth < 12) { "Snapshot nesting exceeds supported depth" }
        require(input.has("kind") && input.has("name")) { "Record requires kind/name" }
        val kind = EntryKind.valueOf(input["kind"].asString)
        val occurredAt = input["occurredAt"]?.asString ?: throw IllegalArgumentException("Record requires occurredAt")
        val zoneId = input["zoneId"]?.asString ?: throw IllegalArgumentException("Record requires zoneId")
        require(input.has("id") && input.has("localDate")) { "Record requires durable ID/local date" }
        val defaults = plain.toJsonTree(Entry(kind = kind, name = input["name"].asString, occurredAt = occurredAt, zoneId = zoneId)).asJsonObject
        val merged = merge(defaults, input)
        // Recursively normalize snapshots using the same schema and preserve their unknown fields.
        listOf("ingredientSnapshots", "composition").forEach { key ->
            val array = JsonArray()
            merged[key].asJsonArray.forEach { array.add(plain.toJsonTree(decodeEntry(it.asJsonObject, depth + 1))) }
            merged.add(key, array)
        }
        if (!merged["recipeSnapshot"].isJsonNull) merged.add("recipeSnapshot", plain.toJsonTree(decodeEntry(merged["recipeSnapshot"].asJsonObject, depth + 1)))
        return children.fromJson(merged, Entry::class.java)
    }
    fun decodeSettings(input: JsonObject): AppSettings = plain.fromJson(merge(plain.toJsonTree(AppSettings()).asJsonObject, input), AppSettings::class.java)
    fun entry(json: String): Entry = gson.fromJson(json, Entry::class.java)
    fun settings(json: String): AppSettings = gson.fromJson(json, AppSettings::class.java)
    fun toJson(value: Any): String = gson.toJson(value)
    fun same(a: Entry, b: Entry): Boolean = gson.toJsonTree(a) == gson.toJsonTree(b)
}
