package com.khaleel.newleaf.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.khaleel.newleaf.data.Entry
import com.khaleel.newleaf.nutrition.FoodCandidate
import kotlinx.coroutines.launch

/** Home → camera → product/category/amount → one explicit journal save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickScanSheet(
    lookup:suspend (String)->FoodCandidate?,
    busy:Boolean,
    saveError:String?,
    onSave:(Entry)->Unit,
    onClose:()->Unit,
    onManual:(String)->Unit,
    capture:(((String?)->Unit)->Unit)?=null
) {
    val context=LocalContext.current
    val currentLookup by rememberUpdatedState(lookup)
    val flow=remember { BarcodeLookupFlow { currentLookup(it) } }
    val scope=rememberCoroutineScope()
    var candidate by remember { mutableStateOf<FoodCandidate?>(null) }
    fun find(code:String=flow.code) {
        if(flow.loading || code.isBlank())return
        scope.launch { flow.find(code)?.let{candidate=it} }
    }
    fun captured(code:String?) {
        if(code.isNullOrBlank())onClose()else find(code)
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()) { captured(it.contents) }
    fun launchCamera() {
        if(capture!=null)capture(::captured)
        else scanner.launch(ScanOptions()
            .setDesiredBarcodeFormats(listOf(ScanOptions.EAN_8,ScanOptions.EAN_13,ScanOptions.UPC_A,ScanOptions.ITF))
            .setPrompt("Point at the product barcode · Back to cancel")
            .setBeepEnabled(false).setOrientationLocked(false))
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(it)launchCamera()else flow.cameraDenied()
    }
    fun scan() {
        if(flow.loading || busy)return
        if(capture!=null || ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)launchCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { scan() }
    val found=candidate
    if(found!=null) {
        PortionDialog(found,null,quickLog=true,busy=busy,saveError=saveError,
            onDismiss={if(!busy)onClose()},onConfirm=onSave)
    } else {
        ModalBottomSheet(
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            onDismissRequest={if(!busy)onClose()},
            containerColor=MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding().imePadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Section(if(flow.loading)"Finding your product…"else"Scan a product")
                if(flow.loading) {
                    CircularProgressIndicator()
                    Caption("Checking the barcode. Nothing has been saved yet.")
                } else {
                    flow.message?.let { ErrorMessage(it) }
                    Field("Barcode number",flow.code,flow::edit,numeric=true)
                    if(flow.code.isNotBlank())Action("Find product",Modifier.fillMaxWidth(),primary=true){find()}
                    Action("Scan again",Modifier.fillMaxWidth(),enabled=!busy){scan()}
                    TextButton(onClick={onManual(flow.code)}){Text("Enter it yourself")}
                }
                TextButton(onClick=onClose,enabled=!busy){Text("Cancel")}
            }
        }
    }
}
