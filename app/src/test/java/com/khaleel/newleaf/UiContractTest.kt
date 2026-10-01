package com.khaleel.newleaf.ui

import com.khaleel.newleaf.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UiContractTest {
    @Test fun editingComposedDrinkRetainsSourceEvidenceAndRemovingAdditionsClearsSnapshots() {
        val base=Entry(kind=EntryKind.DRINK,name="Fictional OFF drink",quantity=100.0,unit="ml",volumeMl=100.0,
            nutrients=mapOf("energy_kcal" to 100.0),basisQuantity=100.0,basisUnit="ml",source="Open Food Facts",
            sourceReference=SourceReference(provider="Open Food Facts",itemId="fictional"),
            nutrientEvidence=mapOf("energy_kcal" to NutrientEvidence(origin="provider",providerCode="energy-kcal_100ml")),
            metadata=mapOf("nutrition_scale" to "1.0"))
        val addition=Entry(kind=EntryKind.FOOD,name="Fictional sugar",nutrients=mapOf("energy_kcal" to 20.0))
        val composed=Nutrition.composeDrink(base,listOf(addition))
        val draft=changePortion(EditorDraft.edit(composed,listOf(composed),AppSettings()),"200","ml")
        val revised=draft.toEntries(AppSettings()).single()
        assertEquals(220.0,revised.nutrients["energy_kcal"]!!,0.0)
        val json=com.google.gson.JsonParser.parseString(JournalExports.json(Journal(listOf(revised)))).asJsonObject
        val snapshot=json.getAsJsonObject("open_food_facts_nutrition").getAsJsonArray("nutrition_snapshots")[0].asJsonObject
        val nutrient=snapshot.getAsJsonArray("nutrients")[0].asJsonObject
        assertEquals(100.0,nutrient["value"].asDouble,0.0)
        assertEquals("energy-kcal_100ml",nutrient["provider_code"].asString)
        val without=draft.copy(items=emptyList()).toEntries(AppSettings()).single()
        assertTrue(without.composition.isEmpty())
        assertEquals(200.0,without.nutrients["energy_kcal"]!!,0.0)
    }
    @Test fun editingProviderPortionPreservesReferenceNutritionInExport() {
        val entry=Entry(kind=EntryKind.FOOD,name="Fictional OFF fixture",quantity=50.0,unit="g",massGrams=50.0,
            nutrients=mapOf("energy_kcal" to 100.0),basisQuantity=100.0,basisUnit="g",
            source="Open Food Facts",sourceReference=SourceReference(provider="Open Food Facts",itemId="fictional"),
            metadata=mapOf("nutrition_scale" to "0.5"))
        val draft=changePortion(EditorDraft.edit(entry,listOf(entry),AppSettings()),"100","g")
        val revised=draft.toEntries(AppSettings()).single()
        assertEquals(200.0,revised.nutrients["energy_kcal"]!!,0.0)
        val json=com.google.gson.JsonParser.parseString(JournalExports.json(Journal(listOf(revised)))).asJsonObject
        val subset=json.getAsJsonObject("open_food_facts_nutrition").getAsJsonArray("nutrition_snapshots")[0].asJsonObject
        assertEquals(200.0,subset.getAsJsonArray("nutrients")[0].asJsonObject["value"].asDouble,0.0)
    }
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
