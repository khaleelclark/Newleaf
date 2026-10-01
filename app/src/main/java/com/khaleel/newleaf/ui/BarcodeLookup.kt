package com.khaleel.newleaf.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khaleel.newleaf.nutrition.FoodCandidate
import com.khaleel.newleaf.nutrition.NutritionException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One captured/typed code follows the same lookup → review path; nothing is persisted here. */
internal class BarcodeLookupFlow(private val lookup:suspend (String)->FoodCandidate?) {
    var code by mutableStateOf(""); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    fun edit(value:String) { if(!loading){code=value;message=null} }
    fun cameraDenied() {
        message="Camera access wasn't allowed. Type the barcode below, or switch to Search or Manual. Your entry is kept."
    }
    suspend fun find(value:String=code):FoodCandidate? {
        if(loading || value.isBlank())return null
        code=value.trim();loading=true;message=null
        return try {
            lookup(code).also {
                currentCoroutineContext().ensureActive()
                if(it==null)message="No product found for this barcode. Try again, search by name, or enter it yourself." }
        } catch(e:CancellationException) { throw e
        } catch(e:NutritionException) {
            currentCoroutineContext().ensureActive()
            message="Product lookup could not finish. ${e.message} Your barcode is kept; try again or enter it yourself."
            null
        } catch(e:Exception) {
            currentCoroutineContext().ensureActive()
            message="Product lookup could not finish. Try again later or enter it yourself. Your barcode is kept."
            null
        } finally { loading=false }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BarcodeLookupSection(lookup:suspend (String)->FoodCandidate?,onReview:(FoodCandidate)->Unit,onManual:(String)->Unit,onLoading:(Boolean)->Unit,initialCode:String="",onCodeChanged:(String)->Unit={},capture:(((String)->Unit)->Unit)?=null) {
    val context=LocalContext.current
    val currentLookup by rememberUpdatedState(lookup)
    val flow=remember { BarcodeLookupFlow { currentLookup(it) }.apply{edit(initialCode)} }
    val scope=rememberCoroutineScope()
    fun find(code:String=flow.code) {
        if(flow.loading || code.isBlank())return
        onCodeChanged(code.trim())
        onLoading(true)
        scope.launch { try { flow.find(code)?.let(onReview) } finally { onLoading(false) } }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()) { result->result.contents?.let { find(it) } }
    fun openScanner() {
        flow.edit(flow.code)
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(listOf(ScanOptions.EAN_8,ScanOptions.EAN_13,ScanOptions.UPC_A,ScanOptions.ITF)).setPrompt("Scan a food barcode · Back to type it instead").setBeepEnabled(false).setOrientationLocked(false))
    }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->
        if(granted)openScanner()else flow.cameraDenied()
    }
    DisposableEffect(Unit) { onDispose { onLoading(false) } }
    val statusRequester=remember { BringIntoViewRequester() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(statusRequester)) {
        if(flow.loading) {
            Caption("Finding product…")
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        flow.message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
    LaunchedEffect(flow.loading,flow.message) {
        if(flow.loading || flow.message!=null)statusRequester.bringIntoView()
    }
    Action("Scan barcode",Modifier.fillMaxWidth(),primary=true,enabled=!flow.loading){
        if(capture!=null)capture{find(it)}
        else if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)openScanner()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    Caption("Scanning looks up the product automatically. Only the barcode goes to Open Food Facts. Review the portion before saving.")
    Field("Barcode digits",flow.code,{if(!flow.loading){flow.edit(it);onCodeChanged(it)}})
    Action(if(flow.loading)"Finding product…"else if(flow.message!=null)"Try barcode again"else"Find product",Modifier.fillMaxWidth(),enabled=!flow.loading && flow.code.isNotBlank()){find()}
    if(!flow.loading && flow.code.isNotBlank())TextButton(onClick={onManual(flow.code)}){Text("Enter product yourself")}
}
