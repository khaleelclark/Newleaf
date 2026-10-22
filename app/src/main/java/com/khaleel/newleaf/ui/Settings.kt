package com.khaleel.newleaf.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
    Caption("Drink volume");Choices(listOf("US fl oz","mL"),if(settings.volumeUnit.equals("ml",true))"mL" else "US fl oz"){onChange(settings.copy(volumeUnit=if(it=="mL")"ml" else "fl oz"))}
    Caption("Body weight");Choices(listOf("lb","kg"),settings.weightUnit){onChange(settings.copy(weightUnit=it))}
    Caption("Activity distance");Choices(listOf("mi","km"),settings.distanceUnit){onChange(settings.copy(distanceUnit=it))}
    Caption("Appearance");Choices(listOf("system","light","dark"),settings.theme){onChange(settings.copy(theme=it))}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(settings:AppSettings,onClose:()->Unit,onSave:(AppSettings)->Unit,onExport:()->Unit,onBackup:()->Unit,onRestore:()->Unit,onLibrary:()->Unit={}) {
    var local by remember{mutableStateOf(settings)};var goal by remember{mutableStateOf(number(settings.waterGoalMl?.let{Units.displayVolume(it,settings.volumeUnit)}))}
    var sourceStatus by remember{mutableStateOf<GatewayStatus?>(null)};var checking by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    var advancedSources by remember{mutableStateOf(false)}
    var showingLicenses by remember{mutableStateOf(false)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        local=local.copy(remindersEnabled=granted)
        if(!granted)error="Notification permission was declined. Reminders remain off."
    }
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight(.95f)){
        Column(Modifier.padding(horizontal=22.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(15.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Section("Settings");TextButton(onClick=onClose){Text("Close")}}
            Action("Saved foods & recipes",Modifier.fillMaxWidth(),onClick=onLibrary)
            Disclosure("Units & appearance"){UnitPreferences(local){next->if(next.volumeUnit!=local.volumeUnit && goal.toDoubleOrNull()!=null)goal=number(Units.convert(goal.toDouble(),local.volumeUnit,next.volumeUnit));local=next}}
            Disclosure("Goals & reminders") {
            Panel{Section("Water goal", "Optional. Set a personal goal, or leave blank.");Field("Daily goal · ${if(local.volumeUnit.equals("ml",true))"mL" else "US fl oz"}",goal,{goal=it},numeric=true)}
            Panel{
                Section("Meal check-in reminders", "Optional, about one hour after a meal. Off by default.")
                Row {Checkbox(local.remindersEnabled,{enabled->
                    if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else local=local.copy(remindersEnabled=enabled)
                });Text("Remind me to record how I felt",Modifier.padding(top=12.dp))}
                Caption("Reminders use Android's inexact scheduling. Dismissing one never creates a journal observation.")
            }
            }
            Disclosure("Nutrition sources") {
                Caption("Barcode lookup needs no account. Food search needs a free USDA key.")
                Caption("Open Food Facts · free barcode lookup, no account needed.\nUSDA FoodData Central · free food search and Describe suggestions. A free USDA API key signup is required.\nNo subscription or paid nutrition service. No hosting needed with your personal key.")
                OutlinedTextField(value=local.usdaApiKey,onValueChange={local=local.copy(usdaApiKey=it);sourceStatus=null},label={Text("Personal USDA API key")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                Action("Get a free USDA key",Modifier.fillMaxWidth()){runCatching{uriHandler.openUri("https://fdc.nal.usda.gov/api-key-signup/")}.onFailure{error="The USDA signup page could not open. Visit fdc.nal.usda.gov/api-key-signup/ in your browser."}}
                Caption("Your personal key is protected on this phone with Android Keystore and excluded from exports and backups. Search and Describe connect directly to USDA. Open Food Facts barcode lookup works without a key or gateway.")
                TextButton(onClick={advancedSources=!advancedSources}){Text(if(advancedSources)"Hide optional gateway"else"Advanced · optional gateway")}
                if(advancedSources) {
                    Caption("Optional fallback when your personal USDA key is blank. Shared provider keys stay on the gateway.")
                    Field("Gateway URL · https://your-host",local.gatewayUrl,{local=local.copy(gatewayUrl=it);sourceStatus=null})
                    OutlinedTextField(value=local.gatewayToken,onValueChange={local=local.copy(gatewayToken=it);sourceStatus=null},label={Text("Device access token")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    Caption("The device token is protected locally and excluded from exports and backups.")
                }
                Action(if(checking)"Checking…" else if(local.usdaApiKey.isNotBlank())"Check setup"else"Check source status",enabled=!checking){scope.launch{checking=true;error=null;try{sourceStatus=NutritionClient(local).status()}catch(e:Exception){error=e.message ?: "Nutrition source unavailable"}finally{checking=false}}}
                sourceStatus?.let{status->listOf("USDA" to status.usda,"Open Food Facts" to status.openFoodFacts).forEach{(name,state)->
                    val label=if(name=="USDA" && local.usdaApiKey.isNotBlank())"Personal key entered · not checked online"else if(state.enabled && state.configured)"Configured · not checked online"else"Unavailable"
                    Caption("$name · $label${state.reason?.let{" · $it"} ?: ""}")
                }}
                Caption("Setup status shows configuration only. Use Search or Describe to check your USDA key with the service.")
                Caption("Search and Describe send food queries directly to USDA with your personal key, or through your optional gateway when no personal key is set. Describe accepts comma/newline food lists and suggests matches that need your confirmation. Barcode lookup sends only the code to Open Food Facts. The rest of your journal stays on this phone.")
            }
            Panel{Section("Your record, in your hands");Action("Export JSON, CSV or Markdown",Modifier.fillMaxWidth(),onClick=onExport);Action("Create full backup",Modifier.fillMaxWidth(),onClick=onBackup);Action("Restore from file",Modifier.fillMaxWidth(),onClick=onRestore);Caption("Exports are readable wherever you save them. Uninstalling or clearing app data removes this phone's journal. Save a full backup first.")}
            Disclosure("About Newleaf"){Caption("Version ${com.khaleel.newleaf.BuildConfig.VERSION_NAME} · A private everyday journal.");Action("Third-party notices & licenses",Modifier.fillMaxWidth()){showingLicenses=true}}
            error?.let{ErrorMessage(it)}
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
