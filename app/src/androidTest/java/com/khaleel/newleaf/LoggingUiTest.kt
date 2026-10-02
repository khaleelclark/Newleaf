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
                ui.onAllNodesWithText("Log").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithText("Start your journal").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("Start your journal").performScrollTo().performClick()
        }
        ui.onNodeWithText("+ 16 oz water").performScrollTo().performClick()
        ui.onNodeWithText("16 US fl oz water logged").assertIsDisplayed()
        ui.onNodeWithText("Undo").performClick()
        ui.onNodeWithText("Log").performClick()
        ui.onNodeWithText("Meal").assertDoesNotExist()
        ui.onNodeWithText("Weight").assertDoesNotExist()
        ui.onNodeWithText("More ways to log").assertExists()
        ui.onNode(hasText("Food") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performScrollTo().performClick()
        ui.onNodeWithText("Notes (optional)").assertDoesNotExist()
        ui.onNodeWithText("Date · YYYY-MM-DD").assertDoesNotExist()
        ui.onNodeWithText("Calories · kcal (whole consumed portion)").assertDoesNotExist()
        ui.onNodeWithText("Notes & photos").assertDoesNotExist()
        ui.onNodeWithText("Nutrition & saved food").assertDoesNotExist()
        ui.onNodeWithText("Change date & time").assertDoesNotExist()
        ui.onNodeWithText("What did you have?").assertExists()
        ui.onNodeWithText("Amount (optional)").assertExists()
        ui.onNodeWithText("Calories (optional)").assertExists()
        ui.onNodeWithText("Find a food").assertExists()
        ui.onNodeWithText("Manual").assertDoesNotExist()
        ui.onNodeWithText("Search").assertDoesNotExist()
        ui.onNodeWithText("Barcode").assertDoesNotExist()
        ui.onNodeWithText("More details").performScrollTo().performClick()
        ui.onNodeWithText("Notes & photos").assertExists()
        ui.onNodeWithText("Nutrition & saved food").assertExists()
        ui.onNodeWithText("Change date & time").assertExists()
        ui.onNodeWithText("Hide more details").performScrollTo().performClick()
        ui.onNodeWithText("Name").performScrollTo().performTextInput(fixtureName)
        ui.onNodeWithText("Save food").assertIsDisplayed().performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText(fixtureName).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNode(hasText(fixtureName) and hasText("Calories unknown")).performScrollTo().performClick()
        ui.onNodeWithText("Edit").assertExists()
        ui.onNodeWithText("Calories · kcal: Unknown").assertDoesNotExist()
        ui.onNodeWithText("Details").performScrollTo().performClick()
        ui.onNodeWithText("Calories · kcal: Unknown").assertExists()
    }
}
