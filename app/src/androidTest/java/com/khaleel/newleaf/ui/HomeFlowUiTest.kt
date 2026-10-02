package com.khaleel.newleaf.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khaleel.newleaf.MainActivity
import com.khaleel.newleaf.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** The new direct tracker actions exercise real durable saves, without nutrition requests. */
@RunWith(AndroidJUnit4::class)
class HomeFlowUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        ui.waitForIdle()
        val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/newleaf-inspired-$name.png")
        java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()};fd.close()
    }
    private fun withJournal(block:(JournalRepository,Journal)->Unit)=runBlocking {
        val repository=JournalRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before=repository.awaitLoaded()
        try {
            repository.saveSettings(before.settings.copy(onboardingComplete=true,volumeUnit="fl oz"))
            ui.runOnUiThread {ui.activity.setContent {NewleafApp(repository)}}
            ui.waitUntil(10_000){ui.onAllNodesWithText("Log food").fetchSemanticsNodes().isNotEmpty()}
            block(repository,before)
        } finally {repository.restoreJournal(before,replace=true);repository.close()}
    }

    @Test fun directFoodDescriptionNeedsNoNumericFieldsAndSavesExactlyOnce()=withJournal {repository,before->
        screenshot("home")
        ui.onNodeWithContentDescription("Scan barcode").assertIsDisplayed()
        ui.onNodeWithText("Log food").performScrollTo().performClick()
        ui.onNodeWithText("What did you eat?").assertIsDisplayed()
        ui.onNodeWithText("Amount (optional)").assertDoesNotExist()
        ui.onNodeWithText("Calories (optional)").assertDoesNotExist()
        val text="Eggs, toast and an apple ${UUID.randomUUID()}"
        ui.onNodeWithText("Food or meal").performTextInput(text)
        screenshot("food-keyboard")
        ui.onNodeWithText("Save food").assertIsDisplayed().performClick()
        ui.waitUntil(10_000){repository.state.value.entries.any{it.name==text}}
        val added=repository.state.value.entries.filter{it.id !in before.entries.map{old->old.id}}
        assertEquals(1,added.size)
        assertEquals(text,added.single().name)
        assertEquals("",added.single().category)
        assertNull(added.single().quantity)
        assertNull(added.single().nutrients["energy_kcal"])
    }

    @Test fun directDrinkChoiceKeepsVolumeAndWaterSeparate()=withJournal {repository,before->
        ui.onNodeWithText("Log drink").performScrollTo().performClick()
        ui.onNodeWithText("What did you drink?").assertIsDisplayed()
        ui.onNodeWithContentDescription("Drink: Water").performClick()
        ui.onNodeWithText("Coffee").performClick()
        ui.onNodeWithText("Volume (optional)").performTextInput("12")
        screenshot("drink")
        ui.onNodeWithText("Save drink").assertIsDisplayed().performClick()
        ui.waitUntil(10_000){repository.state.value.entries.any{it.id !in before.entries.map{old->old.id}}}
        val added=repository.state.value.entries.filter{it.id !in before.entries.map{old->old.id}}
        assertEquals(1,added.size)
        assertEquals("Coffee",added.single().name)
        assertEquals(12*29.5735295625,added.single().volumeMl!!,0.000001)
        assertFalse(added.single().countsAsWater)
        assertNull(added.single().nutrients["energy_kcal"])
    }

    @Test fun weekDaySelectionIsUsedByDirectLogging()=withJournal {repository,before->
        val day=LocalDate.now().plusDays(if(LocalDate.now().dayOfWeek.value==1)1L else -1L)
        ui.onNodeWithContentDescription(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))).performScrollTo().performClick()
        ui.onNodeWithText("Log food").performScrollTo().performClick()
        val name="Chosen-day food ${UUID.randomUUID()}"
        ui.onNodeWithText("Food or meal").performTextInput(name)
        ui.onNodeWithText("Save food").performClick()
        ui.waitUntil(10_000){repository.state.value.entries.any{it.name==name}}
        assertEquals(day.toString(),repository.state.value.entries.single{it.name==name}.localDate)
        assertEquals(before.entries.size+1,repository.state.value.entries.size)
    }

    @Test fun movementAndStepsOpenDirectlyAndCancelWithoutSaving()=withJournal {repository,before->
        ui.onNodeWithText("Log activity").performScrollTo().performClick()
        ui.onNodeWithText("What did you do?").assertIsDisplayed()
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithText("Add steps").performScrollTo().performClick()
        ui.onNodeWithText("Daily step total").assertExists()
        ui.onNodeWithText("Close").performClick()
        ui.runOnIdle {assertEquals(before.entries,repository.state.value.entries)}
    }
}
