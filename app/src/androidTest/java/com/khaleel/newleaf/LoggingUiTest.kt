package com.khaleel.newleaf

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real touch/semantics flows on the test installation; fixtures never ship in the release. */
@RunWith(AndroidJUnit4::class)
class LoggingUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun quickWaterUndoAndUnknownFoodLogging() {
        val fixtureName = "UI fixture: name-only food ${java.util.UUID.randomUUID()}"
        ui.waitUntil(15_000) {
            ui.onAllNodesWithText("Start your journal").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithText("Quick water").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithText("Start your journal").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("Start your journal").performScrollTo().performClick()
        }
        ui.onNodeWithText("16 oz").performScrollTo().performClick()
        ui.onNodeWithText("16 US fl oz water logged").assertIsDisplayed()
        ui.onNodeWithText("Undo").performClick()
        ui.onNodeWithText("+  Log your day").performClick()
        ui.onNode(hasText("Food") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performScrollTo().performClick()
        ui.onNodeWithText("Name").performTextInput(fixtureName)
        ui.onNodeWithText("Save food").assertIsDisplayed().performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText(fixtureName).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText(fixtureName).performScrollTo().performClick()
        ui.onNodeWithText("Calories unknown", substring = true).assertExists()
    }
}
