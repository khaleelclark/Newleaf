package com.khaleel.newleaf.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
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
        if(openBarcode) {
            ui.onNodeWithText("Find a food").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Find by: Name").performScrollTo().performClick()
            ui.onNodeWithText("Barcode").performClick()
        }
        return draft to { saves }
    }

    @Test fun captureAutomaticallyOpensPopulatedReviewAndNeedsConfirmThenSave() {
        var requests=0
        val (draft,saves)=editor { captured->assertEquals(code,captured);requests++;product }
        ui.onNodeWithText("Scan barcode").performScrollTo().performClick()
        ui.onNodeWithText("Review portion").assertIsDisplayed()
        ui.onNodeWithText("Fictional cereal").assertExists()
        ui.onNodeWithText("Fixture brand").assertExists()
        ui.onNode(hasText("Amount (optional)") and SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("1"))).assertIsDisplayed()
        ui.onNodeWithText("90 kcal").assertExists()
        ui.onNodeWithText("Label amount:",substring=true).assertExists()
        ui.onNodeWithText("Calories · kcal (whole consumed portion)").assertDoesNotExist()
        ui.onNodeWithText("Unit / serving label").assertDoesNotExist()
        ui.onNodeWithText("Details").performScrollTo().performClick()
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
        ui.onNodeWithText("Amount unknown. Leave blank",substring=true).assertExists()
        ui.onNodeWithText("Amount (optional)").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
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
        ui.onNodeWithContentDescription("Drink: Water").performClick()
        ui.onNodeWithText("Coffee").performClick()
        ui.onNodeWithText("Volume (optional)").performTextInput("16")
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
        ui.onNodeWithText("Back to entry").performScrollTo().performClick()
        ui.runOnIdle { response.complete(product) }
        ui.waitForIdle()
        ui.onNodeWithText("Review portion").assertDoesNotExist()
        ui.runOnIdle {
            assertEquals("",draft.value.name)
            assertEquals(code,draft.value.lookupBarcode)
            assertEquals(0,saves())
        }
    }

    @Test fun quickScanCategoryAndTypeReachOneExplicitJournalAdd() {
        var confirmed:Entry?=null
        var adds=0
        ui.runOnUiThread { ui.activity.setContent {
            NewleafTheme("light") { PortionDialog(product,null,quickLog=true,onDismiss={},onConfirm={confirmed=it;adds++}) }
        } }
        ui.onNodeWithText("Log scanned item").assertIsDisplayed()
        ui.onNodeWithText("Confirm item").assertDoesNotExist()
        ui.onNodeWithText("Save food").assertDoesNotExist()
        ui.onNodeWithText("Add to journal").assertIsNotEnabled()
        ui.onNodeWithText("Choose a category to enable Add to journal.").assertExists()
        ui.onNodeWithText("Choose a category").performScrollTo().performClick()
        ui.onNodeWithText("Lunch").performClick()
        ui.onNodeWithText("Choose a category to enable Add to journal.").assertDoesNotExist()
        ui.onNode(hasText("Food") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithText("Drink").performClick()
        ui.runOnIdle { assertEquals(0,adds) }
        ui.onNodeWithText("Add to journal").assertIsDisplayed().performClick()
        ui.onNodeWithText("Adding…").assertIsNotEnabled()
        ui.runOnIdle {
            assertEquals(1,adds)
            assertEquals("Lunch",confirmed!!.category)
            assertEquals(EntryKind.DRINK,confirmed!!.kind)
            assertEquals(code,confirmed!!.sourceReference!!.barcode)
        }
    }

    @Test fun quickScanUnknownsBusyCancelAndFailedSaveRetryStaySafe() {
        val busy=mutableStateOf(true)
        val failure=mutableStateOf<String?>(null)
        var adds=0
        var dismisses=0
        var confirmed:Entry?=null
        val unknown=product.copy(quantity=null,unit="serving",basis=NutrientBasis.UNKNOWN,basisQuantity=null,
            nutrients=mapOf("energy_kcal" to null),servingMassG=null,portions=emptyList())
        ui.runOnUiThread { ui.activity.setContent {
            NewleafTheme("light") { PortionDialog(unknown,null,quickLog=true,busy=busy.value,saveError=failure.value,
                onDismiss={dismisses++},onConfirm={confirmed=it;adds++;busy.value=true}) }
        } }
        ui.onNodeWithText("Adding…").assertIsNotEnabled()
        ui.onNodeWithText("Cancel").assertIsNotEnabled()
        ui.onNodeWithText("Calories unknown for this portion").assertExists()
        ui.runOnIdle { assertEquals(0,adds);assertEquals(0,dismisses);busy.value=false }
        ui.onNodeWithText("Add to journal").assertIsNotEnabled()
        ui.onNodeWithText("Choose a category to enable Add to journal.").assertExists()
        ui.onNodeWithText("Choose a category").performScrollTo().performClick()
        ui.onNodeWithText("Snack").performClick()
        ui.onNodeWithText("Add to journal").performClick()
        ui.runOnIdle {
            assertEquals(1,adds)
            assertNull(confirmed!!.quantity)
            assertNull(confirmed!!.nutrients["energy_kcal"])
            assertEquals("Snack",confirmed!!.category)
            failure.value="Could not save. Try again.";busy.value=false
        }
        ui.onNodeWithText("Could not save. Try again.").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion,LiveRegionMode.Polite))
        ui.onNodeWithText("Add to journal").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(2,adds);assertEquals(0,dismisses) }
    }

    @Test fun packageFractionAndUnitSwitchKeepTheSameConsumedAmount() {
        val bottle=product.copy(name="Fictional soda",quantity=100.0,unit="mL",basis=NutrientBasis.PER_100_ML,
            basisQuantity=100.0,basisUnit="mL",nutrients=mapOf("energy_kcal" to 40.0,"sugar_g" to 10.0,"protein_g" to null),
            servingMassG=null,servingVolumeMl=250.0,kind=EntryKind.DRINK,
            portions=listOf(PortionDefinition("package",1.0,volumeMl=500.0),PortionDefinition("serving",1.0,volumeMl=250.0)))
        var confirmed:Entry?=null
        ui.runOnUiThread {ui.activity.setContent {NewleafTheme("light") {PortionDialog(bottle,null,onDismiss={},onConfirm={confirmed=it})}}}
        ui.onNodeWithText("Sugar · g: 50").assertExists()
        ui.onNodeWithText("Amount (optional)").performScrollTo().performTextReplacement("0")
        ui.onNodeWithText("Confirm item").performClick()
        ui.onNodeWithText("Enter an amount greater than zero, or leave it blank.").assertExists()
        ui.runOnIdle {assertNull(confirmed)}
        ui.onNodeWithText("Amount (optional)").performScrollTo().performTextReplacement("0.5")
        ui.onNodeWithText("100 kcal").assertExists()
        ui.onNodeWithText("Sugar · g: 25").assertExists()
        ui.onNodeWithContentDescription("Unit: package").performScrollTo().performClick()
        ui.onNodeWithText("mL").performClick()
        ui.onNodeWithText("Amount (optional)").assertTextContains("250")
        ui.onNodeWithText("100 kcal").assertExists()
        ui.onNodeWithContentDescription("Unit: mL").performScrollTo().performClick()
        ui.onNodeWithText("serving").performClick()
        ui.onNodeWithText("Amount (optional)").assertTextContains("1")
        ui.onNodeWithText("100 kcal").assertExists()
        ui.onNodeWithText("Confirm item").performClick()
        ui.runOnIdle {
            assertEquals(250.0,confirmed!!.volumeMl!!,1e-9)
            assertEquals(100.0,confirmed!!.nutrients["energy_kcal"]!!,1e-9)
            assertNull(confirmed!!.nutrients["protein_g"])
        }
    }

}
