package com.khaleel.newleaf

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline UI checks: no lookup request or configured provider account is needed. */
@RunWith(AndroidJUnit4::class)
class FreeNutritionUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun openJournal() {
        ui.waitUntil(15_000) {
            ui.onAllNodesWithText("Start your journal").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithText("+  Log your day").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithText("Start your journal").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("Start your journal").performScrollTo().performClick()
        }
    }

    @Test fun describeExplainsItemListsAndKeepsTypedInputForManualContinuation() {
        openJournal()
        ui.onNodeWithText("+  Log your day").performClick()
        ui.onNodeWithText("More ways to log").performScrollTo().performClick()
        ui.onNodeWithText("Meal").performScrollTo().performClick()
        ui.onNodeWithText("Describe").performScrollTo().performClick()
        ui.onNodeWithText("Describe foods").performScrollTo().performTextInput("2 eggs, 1 cup milk")
        ui.onNodeWithText("List up to 8 foods separated by commas or new lines", substring=true).assertExists()
        ui.onNodeWithText("Confirm each suggested food and portion", substring=true).assertExists()
        ui.onNodeWithText("Find suggestions").assertExists()
        ui.onNodeWithText("Nutritionix", substring=true).assertDoesNotExist()
        ui.onNodeWithText("Manual").performScrollTo().performClick()
        ui.onNodeWithText("Describe").performScrollTo().performClick()
        ui.onNodeWithText("2 eggs, 1 cup milk").assertExists()
        ui.onNodeWithText("Close").performScrollTo().performClick()
    }

    @Test fun settingsExplainsFreeSourcesAndRequiredUsdaKeySignup() {
        openJournal()
        ui.onNodeWithContentDescription("Settings").performClick()
        ui.onNodeWithText("Nutrition sources").performScrollTo()
        ui.onNodeWithText("free barcode lookup, no account needed", substring=true).assertExists()
        ui.onNodeWithText("A free USDA API key signup is required", substring=true).assertExists()
        ui.onNodeWithText("No subscription or paid nutrition service", substring=true).assertExists()
        ui.onNodeWithText("No hosting needed with your personal key", substring=true).assertExists()
        ui.onNodeWithText("Nutritionix", substring=true).assertDoesNotExist()
        ui.onNodeWithText("Personal USDA API key").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        ui.onNodeWithText("Get a free USDA key").assertExists()
        ui.onNodeWithText("Setup status shows configuration only", substring=true).assertExists()
        ui.onNodeWithText("Open Food Facts barcode lookup works without a key or gateway", substring=true).assertExists()
        ui.onNodeWithText("Gateway URL · https://your-host").assertDoesNotExist()
        ui.onNodeWithText("Device access token").assertDoesNotExist()
        ui.onNodeWithText("Advanced · optional gateway").performScrollTo().performClick()
        ui.onNodeWithText("Gateway URL · https://your-host").assertExists()
        ui.onNodeWithText("Device access token").assertExists()
        ui.onNodeWithText("Hide optional gateway").performScrollTo().performClick()
        ui.onNodeWithText("Gateway URL · https://your-host").assertDoesNotExist()
        ui.onNodeWithText("Save preferences").performScrollTo().performClick()
    }
}
