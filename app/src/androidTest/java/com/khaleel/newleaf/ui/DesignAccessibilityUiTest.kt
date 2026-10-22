package com.khaleel.newleaf.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import com.khaleel.newleaf.MainActivity
import com.khaleel.newleaf.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignAccessibilityUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    @Test fun disclosureExposesItsStateAndRetainsTheEnteredValue() {
        val note=mutableStateOf("")
        ui.runOnUiThread { ui.activity.setContent { NewleafTheme("light") {
            Panel {
                Section("Your meal")
                Disclosure("More details") { Field("Notes",note.value,{note.value=it}) }
            }
        } } }
        ui.onNodeWithText("Your meal").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        ui.onNodeWithText("More details")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Collapsed"))
            .performClick()
        ui.onNodeWithText("Notes").performTextInput("Keep my note")
        ui.onNodeWithText("Hide more details")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Expanded"))
            .performClick()
        ui.onNodeWithText("Notes").assertDoesNotExist()
        ui.onNodeWithText("More details").performClick()
        ui.onNodeWithText("Notes").assertTextContains("Keep my note")
    }

    @Test fun selectorExposesExpansionAndSelectedValueAndHonorsDisabledState() {
        val choice=mutableStateOf("")
        val enabled=mutableStateOf(true)
        ui.runOnUiThread { ui.activity.setContent { NewleafTheme("dark") {
            SelectField("Category",choice.value,listOf("Lunch","Dinner"),enabled=enabled.value) {choice.value=it}
        } } }
        ui.onNodeWithContentDescription("Category: not selected")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Collapsed"))
            .performClick()
        ui.onNodeWithContentDescription("Category: not selected")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Expanded"))
        ui.onNodeWithText("Lunch").performClick()
        ui.onNodeWithContentDescription("Category: Lunch")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Collapsed"))
        ui.runOnIdle { enabled.value=false }
        ui.onNodeWithContentDescription("Category: Lunch").assertIsNotEnabled()
        ui.onNodeWithText("Dinner").assertDoesNotExist()
    }

    @Test fun restoredMetricAliasKeepsTheSettingsAndTrendUnitsConsistent()=runBlocking<Unit> {
        val repository=JournalRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before=repository.awaitLoaded()
        val drink=Entry(kind=EntryKind.DRINK,name="Water",quantity=100.0,unit="mL",volumeMl=100.0,countsAsWater=true)
        try {
            val source=Journal(settings=before.settings.copy(volumeUnit="mL",onboardingComplete=true))
            val restored=JournalExports.previewRestore(JournalExports.json(source).toByteArray()).journal
            repository.saveSettings(restored.settings)
            repository.upsert(drink)
            val expected="${number(Summaries.day(repository.state.value.entries,drink.localDate).waterMl)} mL"
            ui.runOnUiThread { ui.activity.setContent { NewleafApp(repository) } }
            ui.waitUntil(10_000){ui.onAllNodesWithText("Log food").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("$expected water").assertExists()
            ui.onNodeWithText("Trends").performClick()
            ui.onNodeWithText("Water").performScrollTo().performClick()
            org.junit.Assert.assertTrue(ui.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty())
            ui.onNodeWithContentDescription("Water chart in mL.",substring=true).assertExists()
            ui.onAllNodesWithText("US fl oz",substring=true).assertCountEquals(0)
            ui.onNodeWithContentDescription("Settings").performClick()
            ui.onNodeWithText("Units & appearance").performScrollTo().performClick()
            ui.onNodeWithText("mL").assertIsSelected()
        } finally { repository.delete(setOf(drink.id));repository.saveSettings(before.settings);repository.close() }
    }
}
