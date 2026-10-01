package com.khaleel.newleaf.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

object JournalValidation {
    fun settings(settings: AppSettings) {
        require(settings.weightUnit in setOf("lb", "kg"))
        require(settings.volumeUnit in setOf("fl oz", "ml", "mL", "US fl oz"))
        require(settings.distanceUnit in setOf("mi", "km"))
        require(settings.theme in setOf("system", "light", "dark"))
        positive(settings.waterGoalMl, "water goal")
    }
    private fun positive(value: Double?, label: String) { require(value == null || value.isFinite() && value > 0) { "$label must be positive" } }
    private fun nonnegative(value: Double?, label: String) { require(value == null || value.isFinite() && value >= 0) { "$label cannot be negative" } }
    fun entry(entry: Entry, depth: Int = 0) {
        require(depth < 12) { "Snapshot nesting exceeds supported depth" }
        require(entry.id.isNotBlank() && entry.name.isNotBlank()) { "Entry requires ID and name" }
        require(entry.version > 0)
        require(entry.sourceReference?.snapshotAllowed != false) { "Source does not permit a durable journal snapshot" }
        val instant = Instant.parse(entry.occurredAt)
        val zone = ZoneId.of(entry.zoneId)
        LocalDate.parse(entry.localDate)
        Instant.parse(entry.createdAt); Instant.parse(entry.updatedAt)
        val local = OffsetDateTime.parse(entry.localTimestamp)
        if (!entry.dateOnly) {
            require(entry.localDate == instant.atZone(zone).toLocalDate().toString()) { "Event date does not match its zone/instant" }
            require(local.toInstant() == instant && local.offset == instant.atZone(zone).offset) { "Local timestamp does not match event time" }
        }
        entry.endAt?.let { require(!Instant.parse(it).isBefore(instant)) { "End time precedes start" } }
        positive(entry.quantity, "quantity"); positive(entry.basisQuantity, "basis"); positive(entry.weightKg, "weight")
        positive(entry.yieldServings, "servings"); positive(entry.yieldGrams, "cooked yield")
        positive(entry.massGrams, "mass"); positive(entry.volumeMl, "volume")
        nonnegative(entry.distanceMeters, "distance")
        require(entry.steps == null || entry.steps >= 0); require(entry.durationSeconds == null || entry.durationSeconds >= 0)
        require(entry.kind != EntryKind.STEPS || entry.steps != null) { "Steps require an explicit total" }
        require(entry.kind != EntryKind.WEIGHT || entry.weightKg != null) { "Weight requires canonical kg" }
        require(entry.portionEvidence in setOf("measured", "estimated", "unspecified"))
        require(entry.matchStatus in setOf("confirmed", "unreviewed", "unmatched"))
        require(entry.loggingCompleteness in setOf("unmarked", "partial", "complete"))
        require(entry.ratings.values.all { it in 1..5 })
        entry.nutrients.forEach { (code, value) -> require(code.isNotBlank()); nonnegative(value, code) }
        entry.nutrientEvidence.values.forEach {
            require(it.status in setOf("known", "estimated", "unknown"))
            require(it.coverageKnown == null || it.coverageTotal != null && it.coverageKnown in 0..it.coverageTotal)
            require(it.coverageTotal == null || it.coverageTotal >= 0)
        }
        entry.portionDefinitions.forEach { positive(it.quantity, "portion"); positive(it.massGrams, "portion mass"); positive(it.volumeMl, "portion volume") }
        entry.recipeSnapshot?.let { entry(it, depth + 1) }
        entry.ingredientSnapshots.forEach { entry(it, depth + 1) }
        entry.composition.forEach { entry(it, depth + 1) }
    }
    fun journal(journal: Journal) {
        settings(journal.settings)
        require(journal.entries.map { it.id }.distinct().size == journal.entries.size) { "Duplicate record IDs" }
        val byId = journal.entries.associateBy { it.id }
        journal.entries.forEach { entry ->
            entry(entry)
            entry.mealId?.let { id -> require(byId[id]?.kind == EntryKind.MEAL || id in entry.missingContextIds) { "Missing meal $id" } }
            entry.componentIds.forEach { id -> require(id in byId || id in entry.missingContextIds || entry.ingredientSnapshots.any { it.id == id }) { "Missing component $id" } }
            entry.recipeId?.let { id -> require(entry.recipeSnapshot != null || byId[id]?.kind == EntryKind.RECIPE || id in entry.missingContextIds) { "Missing recipe $id" } }
        }
        for (kind in listOf(EntryKind.STEPS, EntryKind.NOTE)) {
            val daily = journal.entries.filter { it.kind == kind && !it.isLibrary }
            require(daily.map { it.localDate }.distinct().size == daily.size) { "More than one $kind total on a date" }
        }
    }
}
