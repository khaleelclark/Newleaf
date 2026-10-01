package com.khaleel.newleaf.ui

import com.khaleel.newleaf.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UiContractTest {
    @Test fun metricDrinkFromPreferencesRetainsItsVolume() {
        val settings=AppSettings(volumeUnit="ml")
        val draft=EditorDraft.new(EntryKind.DRINK,LocalDate.of(2026,10,1),settings).copy(quantity="100")
        val entry=draft.toEntries(settings).single()
        assertEquals(100.0,entry.volumeMl!!,0.000001)
    }

    @Test fun editingWeightAfterChangingDisplayUnitsPreservesCanonicalWeight() {
        val at=Instant.parse("2026-10-01T08:00:00Z")
        val entry=EventTimes.at(Entry(kind=EntryKind.WEIGHT,name="Morning",quantity=180.0,unit="lb",weightKg=81.6466266),at,ZoneId.of("UTC"))
        val settings=AppSettings(weightUnit="kg")
        val result=EditorDraft.edit(entry,listOf(entry),settings).toEntries(settings).single()
        assertEquals(entry.weightKg!!,result.weightKg!!,0.000001)
    }

    @Test fun editingAnUnchangedMealPreservesChildNutritionAndLinks() {
        val at=Instant.parse("2026-10-01T12:00:00Z")
        val meal=EventTimes.at(Entry(id="meal",kind=EntryKind.MEAL,name="Lunch",componentIds=listOf("food","drink")),at,ZoneId.of("UTC"))
        val food=EventTimes.at(Entry(id="food",kind=EntryKind.FOOD,name="Food",mealId="meal",quantity=100.0,unit="g",nutrients=mapOf("energy_kcal" to 300.0)),at,ZoneId.of("UTC"))
        val drink=EventTimes.at(Entry(id="drink",kind=EntryKind.DRINK,name="Drink",mealId="meal",quantity=12.0,unit="fl oz",nutrients=mapOf("energy_kcal" to 50.0)),at,ZoneId.of("UTC"))
        val result=EditorDraft.edit(meal,listOf(meal,food,drink),AppSettings()).toEntries(AppSettings())
        JournalValidation.journal(Journal(entries=result))
        assertEquals(350.0,Summaries.day(result,meal.localDate).nutrients["energy_kcal"]!!.knownSum!!,0.0)
        assertEquals(listOf("meal","meal"),result.filter { it.kind!=EntryKind.MEAL }.map { it.mealId })
    }
}
