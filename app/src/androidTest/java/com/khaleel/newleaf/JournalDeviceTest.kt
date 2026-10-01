package com.khaleel.newleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khaleel.newleaf.data.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only on the development emulator/test installation, never seeded into release data. */
@RunWith(AndroidJUnit4::class)
class JournalDeviceTest {
    @Test fun roomPersistenceDraftAndStepReplacement() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val food = Entry(id="test-food-$suffix", kind=EntryKind.FOOD, name="Persistence fixture", nutrients=mapOf("energy_kcal" to 300.0, "protein_g" to null))
        val step1 = Entry(id="test-steps-1-$suffix", kind=EntryKind.STEPS, name="Steps", localDate="2099-01-01", steps=100)
        val step2 = step1.copy(id="test-steps-2-$suffix",steps=0)
        var repository = JournalRepository(context)
        repository.awaitLoaded()
        val before = repository.state.value
        try {
            repository.upsertAll(listOf(food,step1))
            repository.upsert(step2)
            repository.saveDraft("test-$suffix","A durable unfinished meal")
            repository.close()
            repository = JournalRepository(context)
            repository.awaitLoaded()
            val restored=repository.state.value
            assertEquals(300.0,restored.entries.single { it.id==food.id }.nutrients["energy_kcal"]!!,0.0)
            assertNull(restored.entries.single { it.id==food.id }.nutrients["protein_g"])
            assertEquals(1,restored.entries.count { it.kind==EntryKind.STEPS && it.localDate=="2099-01-01" })
            assertEquals(0L,restored.entries.single { it.id==step2.id }.steps)
            assertEquals("A durable unfinished meal",restored.drafts["test-$suffix"])
        } finally {
            repository.restoreJournal(before,replace=true)
            repository.close()
        }
    }

    @Test fun encryptedCredentialIsAvailableLocallyAndNeverExported() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var repository = JournalRepository(context)
        repository.awaitLoaded()
        val original = repository.state.value.settings
        val token = "fictional-device-token-${UUID.randomUUID()}"
        try {
            repository.saveSettings(original.copy(gatewayUrl="https://nutrition.example.invalid",gatewayToken=token))
            repository.close()
            repository = JournalRepository(context)
            repository.awaitLoaded()
            assertEquals(token,repository.state.value.settings.gatewayToken)
            val json=JournalExports.json(repository.state.value)
            assertFalse(json.contains(token))
            assertFalse(json.contains("fictional-device-token"))
        } finally {
            repository.saveSettings(original)
            repository.close()
        }
    }

    @Test fun missingValuesAndLinkedDrinkSurvivePortableBackup() {
        val meal=Entry(id="fixture-meal",kind=EntryKind.MEAL,name="Fictional lunch",nutrients=mapOf("energy_kcal" to 350.0))
        val food=Entry(id="fixture-food",kind=EntryKind.FOOD,name="Fictional food",mealId=meal.id,nutrients=mapOf("energy_kcal" to 300.0,"sodium_mg" to null))
        val drink=Entry(id="fixture-drink",kind=EntryKind.DRINK,name="Fictional drink",mealId=meal.id,quantity=12.0,unit="fl oz",nutrients=mapOf("energy_kcal" to 50.0))
        val journal=Journal(entries=listOf(meal.copy(componentIds=listOf(food.id,drink.id)),food,drink))
        val preview=JournalExports.previewRestore(JournalExports.backup(journal))
        assertEquals(setOf(meal.id,food.id,drink.id),preview.journal.entries.map { it.id }.toSet())
        assertNull(preview.journal.entries.single { it.id==food.id }.nutrients["sodium_mg"])
        val energy=Summaries.day(preview.journal.entries,food.localDate).nutrients["energy_kcal"]!!.knownSum
        assertEquals(350.0,energy!!,0.0)
        assertEquals(2,Nutrition.contributors(preview.journal.entries).size)
    }
}
