package com.khaleel.newleaf.ui

import com.google.gson.Gson
import com.khaleel.newleaf.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EditorStateTest {
    private val settings=AppSettings()
    @Test fun nameOnlyFoodKeepsUnknownNutrition() {
        val entries=EditorDraft.new(EntryKind.FOOD,LocalDate.parse("2026-09-30"),settings).copy(name="Toast",time="12:30").toEntries(settings)
        assertEquals("2026-09-30",entries.single().localDate)
        assertNull(entries.single().nutrients["energy_kcal"])
        JournalValidation.journal(Journal(entries))
    }
    @Test fun reusableRecipeKeepsEmbeddedSnapshotsAndDoesNotChangeHistory() {
        val ingredient=Entry(kind=EntryKind.FOOD,name="Ingredient",quantity=100.0,unit="g",nutrients=mapOf("energy_kcal" to 200.0))
        val recipe=EditorDraft.new(EntryKind.RECIPE,LocalDate.now(),settings).copy(name="Soup",items=listOf(ingredient),servings="2",yieldGrams="250").toEntries(settings).single()
        JournalValidation.journal(Journal(listOf(recipe)))
        val logged=Recipes.log(recipe,1.0,"serving")
        assertEquals(100.0,logged.nutrients["energy_kcal"]!!,0.0)
        val revised=EditorDraft.edit(recipe,listOf(recipe),settings).copy(items=listOf(ingredient.copy(nutrients=mapOf("energy_kcal" to 400.0)))).toEntries(settings).single()
        assertEquals(2,revised.version)
        assertEquals(1,logged.recipeVersion)
        assertEquals(100.0,logged.nutrients["energy_kcal"]!!,0.0)
        assertEquals(200.0,logged.recipeSnapshot!!.nutrients["energy_kcal"]!!,0.0)
    }
    @Test fun reviewOfNewRecipeEntryRetainsSourceSnapshot() {
        val recipe=Recipes.create("Soup",listOf(Entry(kind=EntryKind.FOOD,name="Ingredient",nutrients=mapOf("energy_kcal" to 100.0))),2.0)
        val entry=Recipes.log(recipe,1.0,"serving")
        val reviewed=EditorDraft.edit(entry,listOf(recipe),settings).copy(isNew=true).toEntries(settings).single()
        assertEquals(recipe.id,reviewed.recipeId)
        assertEquals(1,reviewed.recipeVersion)
        assertNotNull(reviewed.recipeSnapshot)
        assertEquals("recipe",reviewed.source)
    }
    @Test fun editorRoundTripDoesNotRoundCanonicalNumbers() {
        val water=Entry(kind=EntryKind.DRINK,name="Water",quantity=16.0,unit="fl oz",volumeMl=16*29.5735295625,nutrients=mapOf("energy_kcal" to 0.0),countsAsWater=true)
        val body=Entry(kind=EntryKind.WEIGHT,name="Body weight",weightKg=75.123456789,quantity=null,unit="kg")
        val waterRoundTrip=EditorDraft.edit(water,listOf(water),settings).toEntries(settings).single()
        val bodyRoundTrip=EditorDraft.edit(body,listOf(body),settings).toEntries(settings).single()
        assertEquals(water.volumeMl!!,waterRoundTrip.volumeMl!!,0.0)
        assertEquals(body.weightKg!!,bodyRoundTrip.weightKg!!,0.00000000001)
    }
    @Test fun drinkAdditionsContributeOnceAndSurviveEditing() {
        val sugar=Entry(kind=EntryKind.FOOD,name="Sugar",quantity=1.0,unit="tsp",nutrients=mapOf("energy_kcal" to 20.0))
        val drink=EditorDraft.new(EntryKind.DRINK,LocalDate.now(),settings).copy(name="Coffee",quantity="12",nutrients=mapOf("energy_kcal" to "5"),items=listOf(sugar),water=false).toEntries(settings).single()
        assertEquals(25.0,Summaries.day(listOf(drink),drink.localDate).nutrients["energy_kcal"]!!.knownSum!!,0.0)
        val edited=EditorDraft.edit(drink,listOf(drink),settings).toEntries(settings).single()
        assertEquals(25.0,edited.nutrients["energy_kcal"]!!,0.0)
        assertEquals(1,edited.composition.size)
    }
    @Test fun persistedDraftRetainsUnicodeAndStableIds() {
        val draft=EditorDraft.new(EntryKind.MEAL,LocalDate.now(),settings).copy(name="Café 🥣",notes="Two lines\nSecond line",items=listOf(Entry(kind=EntryKind.FOOD,name="Toast")))
        val restored=Gson().fromJson(Gson().toJson(draft),EditorDraft::class.java)
        assertEquals(draft.id,restored.id)
        assertEquals(draft.items.single().id,restored.items.single().id)
        assertEquals(draft.notes,restored.notes)
    }
    @Test fun dateOnlyZeroStepsDoNotRequireAnInventedObservationTime() {
        val entry=EditorDraft.new(EntryKind.STEPS,LocalDate.parse("2026-03-08"),settings).copy(time="02:30",zone="America/New_York",steps="0").toEntries(settings).single()
        assertEquals("2026-03-08",entry.localDate)
        assertTrue(entry.dateOnly)
        assertEquals(0L,entry.steps)
        JournalValidation.journal(Journal(listOf(entry)))
    }

    @Test fun standaloneLookupKeepsEventFieldsAndProviderSnapshot() {
        val draft=EditorDraft.new(EntryKind.FOOD,LocalDate.parse("2026-09-30"),settings).copy(time="08:15",zone="UTC",notes="My note")
        val food=Entry(kind=EntryKind.FOOD,name="Matched food",quantity=150.0,unit="g",nutrients=mapOf("energy_kcal" to 300.0),source="USDA",sourceId="123",sourceReference=SourceReference(provider="usda",itemId="123"))
        val selected=selectStandaloneItem(draft,food,settings)
        val entry=selected.toEntries(settings).single()
        assertEquals(draft.id,entry.id)
        assertEquals("2026-09-30",entry.localDate)
        assertEquals("My note",entry.notes)
        assertEquals("USDA",entry.source)
        assertEquals(300.0,entry.nutrients["energy_kcal"]!!,0.0)
    }
    @Test fun standalonePortionsScaleConsumedSnapshotsOnceAndPreserveOverrides() {
        val food=Entry(kind=EntryKind.FOOD,name="Food",quantity=100.0,unit="g",nutrients=mapOf("energy_kcal" to 200.0),basisQuantity=100.0,basisUnit="g",metadata=mapOf("nutrition_scale" to "1.0"))
        val initial=EditorDraft.edit(food,listOf(food),settings)
        val enlarged=changePortion(initial,"150","g")
        assertEquals(300.0,enlarged.toEntries(settings).single().nutrients["energy_kcal"]!!,0.0)
        val overridden=initial.copy(nutrients=mapOf("energy_kcal" to "400"),overrides=listOf("energy_kcal"))
        val result=changePortion(overridden,"150","g").toEntries(settings).single()
        assertEquals(600.0,result.nutrients["energy_kcal"]!!,0.0)
        assertEquals("user_entered",result.nutrientEvidence["energy_kcal"]!!.origin)
    }
    @Test fun namedServingCanUseItsExplicitMassDefinition() {
        val food=Entry(kind=EntryKind.FOOD,name="Food",quantity=2.0,unit="slice",massGrams=60.0,nutrients=mapOf("energy_kcal" to 200.0),portionDefinitions=listOf(PortionDefinition("slice",1.0,massGrams=30.0)))
        val initial=EditorDraft.edit(food,listOf(food),settings)
        val result=changePortion(initial,"90","g").toEntries(settings).single()
        assertEquals(300.0,result.nutrients["energy_kcal"]!!,0.0)
        assertEquals(90.0,result.massGrams!!,0.0)
    }

}
