package com.khaleel.newleaf.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khaleel.newleaf.MainActivity
import com.khaleel.newleaf.data.*
import com.khaleel.newleaf.nutrition.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Camera capture and lookup are faked; the actual editor/review/confirmation UI runs offline. */
@RunWith(AndroidJUnit4::class)
class BarcodeFlowUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val code="012345678905"
    private val product=FoodCandidate(name="Fictional cereal",brand="Fixture brand",quantity=1.0,unit="serving",
        nutrients=mapOf("energy_kcal" to 300.0,"protein_g" to null),basis=NutrientBasis.PER_100_G,
        servingMassG=30.0,portions=listOf(PortionDefinition("serving",1.0,massGrams=30.0)),
        source="Open Food Facts",sourceId=code,barcode=code,
        sourceReference=SourceReference(provider="Open Food Facts",barcode=code))

    private fun editor(kind:EntryKind=EntryKind.FOOD,openBarcode:Boolean=true,lookup:suspend (String)->FoodCandidate?) : Pair<androidx.compose.runtime.MutableState<EditorDraft>,()->Int> {
        val draft=mutableStateOf(EditorDraft.new(kind,LocalDate.of(2026,10,1),AppSettings()).copy(zone="UTC",time="12:30"))
        var saves=0
        ui.runOnUiThread { ui.activity.setContent {
            NewleafTheme("light") { EntryEditor(draft.value,Journal(),false,{draft.value=it},{saves++},{},
                barcodeLookup=lookup,barcodeCapture={captured->captured(code)}) }
        } }
        if(openBarcode)ui.onNodeWithText("Barcode").performScrollTo().performClick()
        return draft to { saves }
    }

    @Test fun captureAutomaticallyOpensPopulatedReviewAndNeedsConfirmThenSave() {
        var requests=0
        val (draft,saves)=editor { captured->assertEquals(code,captured);requests++;product }
        ui.onNodeWithText("Scan barcode").performScrollTo().performClick()
        ui.onNodeWithText("Review portion").assertIsDisplayed()
        ui.onNodeWithText("Fictional cereal").assertExists()
        ui.onNodeWithText("Fixture brand · Open Food Facts").assertExists()
        ui.onNodeWithText("Quantity").assertTextContains("1")
        ui.onNodeWithText("90 kcal").assertExists()
        ui.onNodeWithText("Starting amount from the product label",substring=true).assertExists()
        ui.onNodeWithText("Calories · kcal (whole consumed portion)").assertDoesNotExist()
        ui.onNodeWithText("Unit / serving label").assertDoesNotExist()
        ui.onNodeWithText("Edit nutrition").performScrollTo().performClick()
        ui.onNodeWithText("Calories · kcal (whole consumed portion)").assertTextContains("90")
        ui.runOnIdle { assertEquals(1,requests);assertEquals("",draft.value.name);assertEquals(0,saves()) }
        ui.onNodeWithText("Confirm item").assertIsDisplayed().performClick()
        ui.runOnIdle {
            assertEquals("Fictional cereal",draft.value.name)
            assertEquals("1",draft.value.quantity)
            assertEquals("90",draft.value.nutrients["energy_kcal"])
            assertNull(draft.value.nutrients["protein_g"]?.takeIf{it.isNotBlank()})
            assertEquals(code,draft.value.base?.sourceReference?.barcode)
            assertEquals(0,saves())
        }
        ui.onNodeWithText("Save food").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1,saves()) }
    }

    @Test fun failedCaptureRetainsCodeAndRetryOpensReviewWithoutSaving() {
        var requests=0
        val (draft,saves)=editor { requests++;if(requests==1)throw NutritionException(NutritionError.UNAVAILABLE)else product }
        ui.onNodeWithText("Scan barcode").performScrollTo().performClick()
        ui.onNodeWithText("Product lookup could not finish",substring=true).assertExists()
        ui.onNodeWithText("Barcode digits").assertTextContains(code)
        ui.onNodeWithText("Try barcode again").performScrollTo().performClick()
        ui.onNodeWithText("Review portion").assertIsDisplayed()
        ui.onNodeWithText("Cancel").performClick()
        ui.runOnIdle { assertEquals(2,requests);assertEquals("",draft.value.name);assertEquals(0,saves()) }
        ui.onNodeWithText("Barcode digits").assertTextContains(code)
    }

    @Test fun noMatchCanContinueManuallyWithBarcodeAndUnknownNutrition() {
        val (draft,saves)=editor { null }
        ui.onNodeWithText("Scan barcode").performScrollTo().performClick()
        ui.onNodeWithText("No product found",substring=true).assertExists()
        ui.onNodeWithText("Enter product yourself").performScrollTo().performClick()
        ui.onNodeWithText("Food or drink").performTextInput("My packaged food")
        ui.onNodeWithText("Confirm item").performClick()
        ui.runOnIdle {
            assertEquals("My packaged food",draft.value.name)
            assertEquals(code,draft.value.base?.sourceReference?.barcode)
            assertNull(draft.value.nutrients["energy_kcal"]?.takeIf{it.isNotBlank()})
            assertEquals(0,saves())
        }
    }

    @Test fun missingProviderPortionRemainsBlankAndMealGetsOnlyConfirmedItem() {
        val (draft,saves)=editor(EntryKind.MEAL) { product.copy(quantity=null,unit="serving",basis=NutrientBasis.UNKNOWN,basisQuantity=null,basisUnit="serving",
            nutrients=mapOf("energy_kcal" to null,"protein_g" to null),servingMassG=null,portions=emptyList(),metadata=mapOf("off_nutrition_status" to "unsupported_input_sets")) }
        ui.onNodeWithText("Scan barcode").performScrollTo().performClick()
        ui.onNodeWithText("Portion unknown. Enter an amount",substring=true).assertExists()
        ui.onNodeWithText("Quantity").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        ui.onNodeWithText("Calories unknown for this portion").assertExists()
        ui.runOnIdle { assertTrue(draft.value.items.isEmpty()) }
        ui.onNodeWithText("Confirm item").performClick()
        ui.runOnIdle {
            assertEquals(1,draft.value.items.size)
            assertNull(draft.value.items.single().quantity)
            assertNull(draft.value.items.single().nutrients["energy_kcal"])
            assertEquals(0,saves())
        }
    }
    @Test fun customDrinkKeepsVolumeUpFrontAndDoesNotCountAsWater() {
        val (draft,saves)=editor(EntryKind.DRINK,openBarcode=false) { error("Manual flow must not look up a provider") }
        ui.onNodeWithText("Notes (optional)").assertDoesNotExist()
        ui.onNodeWithText("Calories · kcal (whole consumed portion)").assertDoesNotExist()
        ui.onNodeWithText("Name").performTextReplacement("Coffee")
        ui.onNodeWithText("Volume").performTextInput("16")
        ui.onNodeWithText("Save drink").assertIsDisplayed().performClick()
        ui.runOnIdle {
            val entry=draft.value.toEntries(AppSettings()).single()
            assertEquals(16*29.5735295625,entry.volumeMl!!,0.000001)
            assertFalse(entry.countsAsWater)
            assertNull(entry.nutrients["energy_kcal"])
            assertEquals(1,saves())
        }
    }

    @Test fun activityCanSaveWithoutIntensityNotesOrInventedExerciseCalories() {
        val (draft,saves)=editor(EntryKind.ACTIVITY,openBarcode=false) { error("Activity must not look up a provider") }
        ui.onNodeWithText("Easy").assertDoesNotExist()
        ui.onNodeWithText("Notes (optional)").assertDoesNotExist()
        ui.onNodeWithText("Distance · mi (optional)").assertExists()
        ui.onNodeWithText("Save activity").assertIsDisplayed().performClick()
        ui.runOnIdle {
            val entry=draft.value.toEntries(AppSettings()).single()
            assertEquals("Walk",entry.name)
            assertNull(entry.durationSeconds)
            assertNull(entry.distanceMeters)
            assertNull(entry.nutrients["energy_kcal"])
            assertEquals(1,saves())
        }
    }

    @Test fun switchingToManualCannotOpenAnAbandonedBarcodeReview() {
        val response=CompletableDeferred<FoodCandidate?>()
        val (draft,saves)=editor { withContext(NonCancellable){response.await()} }
        ui.onNodeWithText("Scan barcode").performScrollTo().performClick()
        ui.onNodeWithText("Manual").performScrollTo().performClick()
        ui.runOnIdle { response.complete(product) }
        ui.waitForIdle()
        ui.onNodeWithText("Review portion").assertDoesNotExist()
        ui.runOnIdle {
            assertEquals("",draft.value.name)
            assertEquals(code,draft.value.lookupBarcode)
            assertEquals(0,saves())
        }
    }

}
