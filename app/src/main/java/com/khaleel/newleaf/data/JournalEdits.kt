package com.khaleel.newleaf.data

import java.time.Instant

/** Pure transaction plan, shared by batch upserts and editor saves. */
internal object JournalEdits {
    data class Result(val journal: Journal, val undo: List<Entry>)

    fun apply(previous: Journal, upserts: List<Entry>, deleteIds: Set<String>): Result {
        require(upserts.map { it.id }.distinct().size == upserts.size) { "Duplicate upsert IDs" }
        require(upserts.none { it.id in deleteIds }) { "An ID cannot be both upserted and deleted" }
        upserts.forEach(JournalValidation::entry)
        val old = previous.entries.associateBy { it.id }
        val incoming = upserts.associateBy { it.id }
        fun dailyKey(entry: Entry): String? = if (!entry.isLibrary && entry.kind in setOf(EntryKind.STEPS, EntryKind.NOTE)) "${entry.kind}:${entry.localDate}" else null
        val incomingDaily = upserts.mapNotNull(::dailyKey).toSet()
        val deleting = deleteIds + previous.entries.filter { it.id !in incoming && dailyKey(it) in incomingDaily }.map { it.id }
        val now = Instant.now().toString()
        val candidates = previous.entries.filter { it.id !in deleting && it.id !in incoming } + upserts
        val revised = candidates.map { candidate ->
            val unlinked = candidate.copy(
                mealId = candidate.mealId?.takeIf { it !in deleting },
                componentIds = candidate.componentIds.filter { id -> id !in deleting || candidate.ingredientSnapshots.any { it.id == id } },
                recipeId = if (candidate.recipeSnapshot == null) candidate.recipeId?.takeIf { it !in deleting } else candidate.recipeId)
            // Preserve object identity for unchanged records: persistence can skip serialization.
            val cleaned = if (unlinked.mealId == candidate.mealId && unlinked.componentIds == candidate.componentIds && unlinked.recipeId == candidate.recipeId) candidate else unlinked
            old[cleaned.id]?.let { existing ->
                if (JournalCodec.same(existing, cleaned)) existing else cleaned.copy(
                    version = maxOf(cleaned.version, existing.version + 1), createdAt = existing.createdAt, updatedAt = now)
            } ?: cleaned
        }
        val journal = previous.copy(entries = revised)
        JournalValidation.journal(journal)
        val nextById = revised.associateBy { it.id }
        val undo = previous.entries.filter { entry -> nextById[entry.id]?.let { !JournalCodec.same(entry, it) } ?: true }
        return Result(journal, undo)
    }
}
