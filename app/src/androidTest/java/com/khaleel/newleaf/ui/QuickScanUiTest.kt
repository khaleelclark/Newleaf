package com.khaleel.newleaf.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khaleel.newleaf.MainActivity
import com.khaleel.newleaf.data.*
import com.khaleel.newleaf.nutrition.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home scan navigation and actual saving run with offline capture/catalog fixtures. */
@RunWith(AndroidJUnit4::class)
class QuickScanUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val code="012345678905"
    private val product=FoodCandidate(name="Fictional quick-scan cereal",brand="Fixture brand",quantity=1.0,unit="serving",
        nutrients=mapOf("energy_kcal" to 300.0,"protein_g" to null),basis=NutrientBasis.PER_100_G,
        servingMassG=30.0,portions=listOf(PortionDefinition("serving",1.0,massGrams=30.0)),
        source="Open Food Facts",sourceId=code,barcode=code,
        sourceReference=SourceReference(provider="Open Food Facts",barcode=code))

    private fun screenshot(name:String) {
        ui.waitForIdle()
        val output=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/newleaf-simple-$name.png")
        java.io.FileInputStream(output.fileDescriptor).use{it.readBytes()}
        output.close()
    }

    @Test fun homeScanOpensCameraLooksUpChoosesCategoryAndSavesOnce()=runBlocking {
        val repository=JournalRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before=repository.awaitLoaded()
        var captures=0;var requests=0
        try {
            repository.saveSettings(before.settings.copy(onboardingComplete=true))
            ui.runOnUiThread { ui.activity.setContent {
                NewleafApp(repository,quickBarcodeLookup={assertEquals(code,it);requests++;product},
                    quickBarcodeCapture={captured->captures++;captured(code)})
            } }
            ui.waitUntil(10_000){ui.onAllNodes(hasContentDescription("Scan barcode") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            screenshot("home")
            ui.onNodeWithText("History").performClick()
            ui.onNodeWithText("← Previous").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Scan barcode").performClick()
            ui.onNodeWithText("Log scanned item").assertIsDisplayed()
            ui.onAllNodesWithText(product.name)[0].assertExists()
            ui.onNodeWithText("90 kcal").assertExists()
            ui.onNodeWithText("Add to journal").assertIsNotEnabled()
            ui.runOnIdle {assertEquals(1,captures);assertEquals(1,requests);assertEquals(before.entries.size,repository.state.value.entries.size)}
            ui.onNodeWithText("Choose a category").performScrollTo().performClick()
            ui.onNodeWithText("Lunch").performClick()
            screenshot("product-review")
            ui.onNodeWithText("Add to journal").assertIsDisplayed().performClick()
            ui.waitUntil(10_000){repository.state.value.entries.any{it.name==product.name && it.id !in before.entries.map{old->old.id}}}
            val saved=repository.state.value.entries.filter{it.id !in before.entries.map{old->old.id}}
            assertEquals(1,saved.size)
            assertEquals("Lunch",saved.single().category)
            assertEquals(java.time.LocalDate.now().toString(),saved.single().localDate)
            assertEquals(code,saved.single().sourceReference?.barcode)
            assertEquals(90.0,saved.single().nutrients["energy_kcal"]!!,0.000001)
            assertNull(saved.single().nutrients["protein_g"])
            ui.onNodeWithText("Log scanned item").assertDoesNotExist()
        } finally {repository.restoreJournal(before,replace=true);repository.close()}
    }

    @Test fun cameraCancelDoesNotLookUpOrCreateEntry() {
        var requests=0;var saves=0;var closes=0
        ui.runOnUiThread {ui.activity.setContent {NewleafTheme("light") {
            QuickScanSheet({requests++;product},false,null,{saves++},{closes++},{},capture={it(null)})
        }} }
        ui.runOnIdle {assertEquals(0,requests);assertEquals(0,saves);assertEquals(1,closes)}
    }

    @Test fun unknownCodeOffersRetryOrPlainManualEntryWithoutSaving() {
        var requests=0;var saves=0;var manual:String?=null
        ui.runOnUiThread {ui.activity.setContent {NewleafTheme("light") {
            QuickScanSheet({requests++;null},false,null,{saves++},{},{manual=it},capture={it(code)})
        }} }
        ui.onNodeWithText("No product found",substring=true).assertExists()
        ui.onNodeWithText("Barcode number").assertTextContains(code)
        ui.onNodeWithText("Find product").performClick()
        ui.onNodeWithText("Enter it yourself").performClick()
        ui.runOnIdle {assertEquals(2,requests);assertEquals(code,manual);assertEquals(0,saves)}
    }

    @Test fun closingLookupRejectsLateProviderResponse() {
        val response=CompletableDeferred<FoodCandidate?>()
        val visible=mutableStateOf(true)
        var saves=0
        ui.runOnUiThread {ui.activity.setContent {NewleafTheme("light") {
            if(visible.value)QuickScanSheet({withContext(NonCancellable){response.await()}},false,null,{saves++},{visible.value=false},{},capture={it(code)})
        }} }
        ui.onNodeWithText("Finding your product…").assertExists()
        ui.onNodeWithText("Cancel").performClick()
        ui.runOnIdle {response.complete(product)}
        ui.waitForIdle()
        ui.onNodeWithText("Log scanned item").assertDoesNotExist()
        ui.runOnIdle {assertEquals(0,saves)}
    }
}
