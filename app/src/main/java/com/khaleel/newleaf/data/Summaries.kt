package com.khaleel.newleaf.data

import java.time.LocalDate

data class DailySummary(val date: String, val entryCount: Int, val nutrients: Map<String, NutrientCoverage>,
    val waterMl: Double?, val drinkVolumeMl: Double?, val steps: Long?, val activityDistanceMeters: Double?,
    val activityDurationSeconds: Long?, val weightKg: Double?, val weightMeasurements: Int,
    val loggingCompleteness: String = "unmarked")
data class ObservedAverage(val mean: Double?, val observedDays: Int, val partialDays: Int, val totalDays: Int)
data class WeightTrend(val meanKg: Double?, val measuredDays: Int)

object Summaries {
    fun day(entries: List<Entry>, date: String): DailySummary {
        val selected = entries.filter { !it.isLibrary && it.localDate == date }
        val drinks = selected.filter { it.kind == EntryKind.DRINK }
        fun volume(e: Entry) = e.volumeMl ?: e.quantity?.let { Units.volumeMl(it, e.unit) }
        fun sum(values: List<Double>) = values.takeIf { it.isNotEmpty() }?.sum()
        val weights = selected.filter { it.kind == EntryKind.WEIGHT }.mapNotNull { it.weightKg }
        val activities = selected.filter { it.kind == EntryKind.ACTIVITY }
        return DailySummary(date, selected.size, Nutrition.all(Nutrition.contributors(selected)),
            sum(drinks.filter { it.countsAsWater }.mapNotNull(::volume)), sum(drinks.mapNotNull(::volume)),
            selected.singleOrNull { it.kind == EntryKind.STEPS }?.steps,
            sum(activities.mapNotNull { it.distanceMeters }), activities.mapNotNull { it.durationSeconds }.takeIf { it.isNotEmpty() }?.sum(),
            weights.takeIf { it.isNotEmpty() }?.average(), weights.size,
            selected.firstOrNull { it.kind == EntryKind.NOTE }?.loggingCompleteness ?: "unmarked")
    }
    fun range(entries: List<Entry>, from: String, to: String): List<DailySummary> {
        var date = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        require(!date.isAfter(end))
        val result = mutableListOf<DailySummary>()
        while (!date.isAfter(end)) { result += day(entries, date.toString()); date = date.plusDays(1) }
        return result
    }
    fun average(days: List<DailySummary>, code: String): ObservedAverage {
        val observed = days.mapNotNull { it.nutrients[code]?.knownSum }
        return ObservedAverage(observed.takeIf { it.isNotEmpty() }?.average(), observed.size,
            days.count { it.nutrients[code]?.completeness == Completeness.PARTIAL }, days.size)
    }
    fun weightTrend(entries: List<Entry>, ending: String): WeightTrend {
        val end = LocalDate.parse(ending)
        val means = range(entries, end.minusDays(6).toString(), ending).mapNotNull { it.weightKg }
        return WeightTrend(if (means.size >= 3) means.average() else null, means.size)
    }
}
