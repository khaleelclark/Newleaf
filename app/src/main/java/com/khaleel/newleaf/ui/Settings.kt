package com.khaleel.newleaf.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.khaleel.newleaf.data.*
import com.khaleel.newleaf.nutrition.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeSheet(settings:AppSettings,onContinue:(AppSettings)->Unit) {
    var local by remember{mutableStateOf(settings)}
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={onContinue(local)},containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight(.95f)){Column(Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)){
        LeafMark();Text("Room for your everyday.",style=MaterialTheme.typography.headlineLarge)
        Text("A simple journal for meals, drinks, movement, and how you feel. Log a little or a lot. Your record stays on this phone, and you can export it whenever you want.")
        Caption("Choose your units. You can change these later.")
        UnitPreferences(local){local=it}
        Action("Start your journal",Modifier.fillMaxWidth(),primary=true){onContinue(local)}
        Spacer(Modifier.height(16.dp))
    }}
}

@Composable
private fun UnitPreferences(settings:AppSettings,onChange:(AppSettings)->Unit) {
    Caption("Drink volume");Choices(listOf("US fl oz","mL"),if(settings.volumeUnit=="ml")"mL" else "US fl oz"){onChange(settings.copy(volumeUnit=if(it=="mL")"ml" else "fl oz"))}
    Caption("Body weight");Choices(listOf("lb","kg"),settings.weightUnit){onChange(settings.copy(weightUnit=it))}
    Caption("Activity distance");Choices(listOf("mi","km"),settings.distanceUnit){onChange(settings.copy(distanceUnit=it))}
    Caption("Appearance");Choices(listOf("system","light","dark"),settings.theme){onChange(settings.copy(theme=it))}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(settings:AppSettings,onClose:()->Unit,onSave:(AppSettings)->Unit,onExport:()->Unit,onBackup:()->Unit,onRestore:()->Unit) {
    var local by remember{mutableStateOf(settings)};var goal by remember{mutableStateOf(number(settings.waterGoalMl?.let{Units.displayVolume(it,settings.volumeUnit)}))}
    var sourceStatus by remember{mutableStateOf<GatewayStatus?>(null)};var checking by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var showingLicenses by remember{mutableStateOf(false)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        local=local.copy(remindersEnabled=granted)
        if(!granted)error="Notification permission was declined. Reminders remain off."
    }
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight(.95f)){
        Column(Modifier.padding(horizontal=22.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(15.dp)){
            Section("Make yourself at home", "Preferences, sources, and your data.")
            Panel{Section("Units & appearance");UnitPreferences(local){next->if(next.volumeUnit!=local.volumeUnit && goal.toDoubleOrNull()!=null)goal=number(Units.convert(goal.toDouble(),local.volumeUnit,next.volumeUnit));local=next}}
            Panel{Section("Water goal", "Optional. Set a personal goal, or leave blank.");Field("Daily goal · ${if(local.volumeUnit=="ml")"mL" else "US fl oz"}",goal,{goal=it},numeric=true)}
            Panel{
                Section("Meal check-in reminders", "Optional, about one hour after a meal. Off by default.")
                Row {Checkbox(local.remindersEnabled,{enabled->
                    if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else local=local.copy(remindersEnabled=enabled)
                });Text("Remind me to record how I felt",Modifier.padding(top=12.dp))}
                Caption("Reminders use Android's inexact scheduling. Dismissing one never creates a journal observation.")
            }
            Panel{
                Section("Nutrition sources", "Manual logging, favorites and exports work offline.")
                Caption("Open Food Facts · packaged food barcode lookup\nUSDA FoodData Central · general food search\nNutritionix · meal description analysis")
                Field("Gateway URL · https://your-host",local.gatewayUrl,{local=local.copy(gatewayUrl=it);sourceStatus=null})
                OutlinedTextField(value=local.gatewayToken,onValueChange={local=local.copy(gatewayToken=it);sourceStatus=null},label={Text("Device access token")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                Caption("Provider keys stay on your gateway. The device token is protected locally and excluded from exports and backups.")
                Action(if(checking)"Checking…" else "Check source status",enabled=!checking){scope.launch{checking=true;error=null;try{sourceStatus=NutritionClient(local).status()}catch(e:Exception){error=e.message ?: "Gateway unavailable"}finally{checking=false}}}
                sourceStatus?.let{status->listOf("USDA" to status.usda,"Nutritionix" to status.nutritionix,"Open Food Facts" to status.openFoodFacts).forEach{(name,state)->Caption("$name · ${if(state.enabled && state.configured)"Available" else "Unavailable"}${state.reason?.let{" · $it"} ?: ""}")}}
                Caption("Search sends food queries to USDA. Barcode lookup sends the code to Open Food Facts. Analyze sends your typed description to Nutritionix through your gateway. Nothing sends the rest of your journal.")
            }
            Panel{Section("Your record, in your hands");Action("Export JSON, CSV or Markdown",Modifier.fillMaxWidth(),onClick=onExport);Action("Create full backup",Modifier.fillMaxWidth(),onClick=onBackup);Action("Restore from file",Modifier.fillMaxWidth(),onClick=onRestore);Caption("Exports are readable wherever you save them. Uninstalling or clearing app data removes this phone's journal. Save a full backup first.")}
            Panel{Section("About Newleaf");Caption("Version ${com.khaleel.newleaf.BuildConfig.VERSION_NAME} · A private everyday journal.");Action("Third-party notices & licenses",Modifier.fillMaxWidth()){showingLicenses=true}}
            error?.let{Caption(it)}
            Action("Save preferences",Modifier.fillMaxWidth(),primary=true){runCatching{val n=goal.takeIf{it.isNotBlank()}?.toDouble();require(n==null || n.isFinite() && n>0){"Water goal must be positive or blank"};onSave(local.copy(waterGoalMl=n?.let{Units.volumeMl(it,local.volumeUnit)}))}.onFailure{error=it.message}}
            Spacer(Modifier.height(24.dp))
        }
    }
    if(showingLicenses)LicensesDialog{showingLicenses=false}

}


@Composable
private fun LicensesDialog(onClose:()->Unit) {
    val context=LocalContext.current
    var selected by remember{mutableStateOf("Notices")}
    val text by produceState("Loading…",selected) {
        value=withContext(Dispatchers.IO){runCatching{context.assets.open(if(selected=="Notices")"NOTICE.txt"else"APACHE-2.0.txt").bufferedReader().use{it.readText()}}.getOrDefault("License text could not be opened")}
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Section("Notices & licenses")
                Choices(listOf("Notices","Apache 2.0"),selected){selected=it}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){Text(text,style=MaterialTheme.typography.bodyMedium)}
                Action("Close",Modifier.fillMaxWidth(),primary=true,onClick=onClose)
            }
        }
    }
}
