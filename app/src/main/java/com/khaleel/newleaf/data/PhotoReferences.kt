package com.khaleel.newleaf.data

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Includes immutable nested snapshots and arbitrary JSON draft schemas. */
internal object PhotoReferences {
    fun collect(journal: Journal): Set<String>? {
        fun paths(entry: Entry): List<String> = entry.photoPaths + entry.recipeSnapshot?.let(::paths).orEmpty() +
            entry.ingredientSnapshots.flatMap(::paths) + entry.composition.flatMap(::paths)
        val referenced = journal.entries.flatMap(::paths).toMutableSet()
        fun draftStrings(value: JsonElement) {
            when {
                value.isJsonObject -> value.asJsonObject.entrySet().forEach { draftStrings(it.value) }
                value.isJsonArray -> value.asJsonArray.forEach(::draftStrings)
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                    val text = value.asString
                    if (text.startsWith("/") || text.startsWith("content://")) referenced += text
                }
            }
        }
        for ((_, draft) in journal.drafts) {
            if (draft.isBlank()) continue
            try { draftStrings(JsonParser.parseString(draft)) }
            catch (_: RuntimeException) { return null } // Unreadable drafts must never lose their photos.
        }
        return referenced
    }
}
