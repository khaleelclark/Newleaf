package com.khaleel.newleaf.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khaleel.newleaf.data.*
import com.khaleel.newleaf.nutrition.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun EntryEditor(draft:EditorDraft,journal:Journal,busy:Boolean,onChange:(EditorDraft)->Unit,onSave:()->Unit,onClose:()->Unit,saveError:String?=null) {
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var lookupMode by remember { mutableStateOf("Manual") };var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FoodCandidate>>(emptyList()) }
    var reviewingCandidate by remember { mutableStateOf<FoodCandidate?>(null) };var reviewingEntry by remember { mutableStateOf<Entry?>(null) }
    var itemEditing by remember { mutableStateOf<String?>(null) };var selectingStandalone by remember{mutableStateOf(false)};var itemDialog by remember { mutableStateOf(false) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved->
        photoPath?.let{path->val file=File(path)
            if(saved && file.length() in 1..64L*1024*1024)onChange(draft.copy(photos=draft.photos+path))
            else {file.delete();if(saved)error="Camera photo is empty or exceeds the supported 64 MB limit"}
        }
        photoPath=null
    }
    fun startCamera() { runCatching { val f=File(context.filesDir,"photos/${UUID.randomUUID()}.jpg");f.parentFile?.mkdirs();photoPath=f.absolutePath;takePhoto.launch(FileProvider.getUriForFile(context,"${context.packageName}.files",f)) }.onFailure { error="Camera could not open. You can choose a photo instead." } }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->if(granted)startCamera()else error="Camera permission declined. Choose a photo or enter a barcode below." }
    val pickPhoto=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri->
        uri?.let { scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val mime=context.contentResolver.getType(uri)
                val ext=when(mime){"image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp";"image/heic"->"heic";"image/heif"->"heif";"image/gif"->"gif";else->null}
                require(ext!=null){"Choose a JPEG, PNG, WebP, HEIC/HEIF, or GIF photo"}
                val file=File(context.filesDir,"photos/${UUID.randomUUID()}.$ext")
                file.parentFile?.mkdirs()
                try {
                    context.contentResolver.openInputStream(uri)?.use { input->file.outputStream().use { output->
                        val buffer=ByteArray(8192);var count:Int;var total=0L
                        while(input.read(buffer).also{count=it}!=-1){total+=count;require(total<=64L*1024*1024){"Photo is larger than the supported 64 MB limit. Choose a smaller copy."};output.write(buffer,0,count)}
                        require(total>0){"The chosen photo is empty"}
                    }} ?: throw IllegalArgumentException("The selected photo could not be opened")
                    file.absolutePath
                } catch(e:Exception){file.delete();throw e}
            } }.onSuccess { onChange(draft.copy(photos=draft.photos+it)) }.onFailure { error=it.message ?: "Photo could not be copied; your entry is still here." }
        } }
    }
    fun lookup(mode:String,input:String) {
        if(loading || input.isBlank())return
        loading=true;error=null
        scope.launch { try { val client=NutritionClient(journal.settings);results=when(mode){"Describe"->client.describe(input);"Barcode"->listOfNotNull(client.barcode(input));else->client.search(input)};if(results.isEmpty())error="No match found. Add a manual item and keep your original input." }catch(e:Exception){error=e.message ?: "Lookup unavailable. Continue manually."}finally{loading=false} }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()) { result->result.contents?.let { query=it;lookupMode="Barcode";lookup("Barcode",it) } }
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,dragHandle={BottomSheetDefaults.DragHandle()},modifier=Modifier.fillMaxHeight(.95f)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal=22.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(!draft.isNew)"Edit ${kindName(draft.kind).lowercase()}" else "Add ${kindName(draft.kind).lowercase()}",style=MaterialTheme.typography.headlineMedium);TextButton(onClick=onClose){Text("Close")} }
            Caption("${draft.date} · ${draft.zone}")
            Field(if(draft.kind==EntryKind.MEAL)"Meal name" else "Name",draft.name,{onChange(draft.copy(name=it))})
            if(draft.kind in listOf(EntryKind.MEAL,EntryKind.RECIPE,EntryKind.TEMPLATE)) {
                if(draft.kind==EntryKind.MEAL) { Choices(listOf("Breakfast","Lunch","Dinner","Snack"),draft.category){onChange(draft.copy(category=it))};Field("Category (custom allowed)",draft.category,{onChange(draft.copy(category=it))}) }
                Section(if(draft.kind==EntryKind.RECIPE)"Ingredients" else "Your meal", "Add foods and drinks; each item's nutrition is counted once.")
                draft.items.forEach { item->Panel { Text(item.name,style=MaterialTheme.typography.titleMedium);Caption("${number(item.quantity)} ${item.unit} · ${item.nutrients["energy_kcal"]?.let { "${number(it)} kcal" } ?: "Calories unknown"} · ${item.portionEvidence}");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Edit"){reviewingEntry=item;reviewingCandidate=null;itemEditing=item.id;itemDialog=true};Action("Remove"){onChange(draft.copy(items=draft.items.filterNot {it.id==item.id}))}} } }
                Choices(listOf("Manual","Search","Barcode","Describe"),lookupMode){lookupMode=it;error=null}
                when(lookupMode) {
                    "Manual"->Action("+ Add food or drink",Modifier.fillMaxWidth()){selectingStandalone=false;reviewingCandidate=null;reviewingEntry=null;itemEditing=null;itemDialog=true}
                    "Describe"->{ Field("Describe your meal",draft.originalText,{onChange(draft.copy(originalText=it))},lines=3);Caption("Analyzing sends this description to your configured nutrition gateway and Nutritionix. Review every item and estimated amount before saving.");Action(if(loading)"Analyzing…" else "Analyze description",enabled=!loading){lookup("Describe",draft.originalText)} }
                    else->{Field(if(lookupMode=="Barcode")"Barcode digits" else "Search foods",query,{query=it});Caption(if(lookupMode=="Search")"Search sends your query to USDA through your configured gateway." else "Barcode lookup sends only this code to Open Food Facts.");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action(if(loading)"Looking up…" else "Look up",enabled=!loading){lookup(lookupMode,query)};if(lookupMode=="Barcode")Action("Open scanner"){scanner.launch(ScanOptions().setDesiredBarcodeFormats(listOf(ScanOptions.EAN_8,ScanOptions.EAN_13,ScanOptions.UPC_A,ScanOptions.ITF)).setPrompt("Scan a food barcode · manual entry is also available").setBeepEnabled(false).setOrientationLocked(false))}}}
                }
                if(lookupMode=="Barcode" && query.isNotBlank())Action("Create a custom item with this barcode") {
                    reviewingEntry=Entry(kind=EntryKind.FOOD,name="",sourceId=query,sourceReference=SourceReference(provider="manual",barcode=query))
                    reviewingCandidate=null;itemEditing=null;itemDialog=true
                }
                error?.let { Caption(it) }
                if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                results.forEach { result->Panel { Text(result.name,style=MaterialTheme.typography.titleMedium);Caption(listOfNotNull(result.brand,result.source,"${number(result.quantity)} ${result.unit}",if(result.estimated)"Estimated portion" else null).joinToString(" · "));Action("Review item",enabled=!loading){ scope.launch { loading=true;try { reviewingCandidate=if(result.needsDetails || result.source.lowercase().contains("usda"))NutritionClient(journal.settings).details(result) else result;reviewingEntry=null;itemEditing=null;itemDialog=true } catch(e:Exception){error=e.message} finally{loading=false} } } } }
                val local=journal.entries.filter { it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK) && (query.isBlank() || it.name.contains(query,true)) }.distinctBy { "${it.name}:${it.quantity}:${it.unit}" }.sortedByDescending { it.isFavorite }.take(8)
                if(local.isNotEmpty()) { Section("Favorites & recent");local.forEach { item->Action("${if(item.isFavorite)"♡  " else ""}${item.name} · ${number(item.quantity)} ${item.unit}",Modifier.fillMaxWidth()){ reviewingEntry=item.copy(id=UUID.randomUUID().toString(),mealId=null,isLibrary=false);reviewingCandidate=null;itemEditing=null;itemDialog=true } } }
                if(draft.kind==EntryKind.RECIPE) { Field("Yield · servings",draft.servings,{onChange(draft.copy(servings=it))},numeric=true);Field("Final cooked weight · g (optional)",draft.yieldGrams,{onChange(draft.copy(yieldGrams=it))},numeric=true);Caption("Gram portions require final cooked weight. Missing ingredient nutrition remains unknown.") }
                if(draft.kind==EntryKind.MEAL && draft.isNew)Choices(listOf("Log meal","Save meal template","Create recipe"),if(draft.saveAs=="template")"Save meal template" else if(draft.saveAs=="recipe")"Create recipe" else "Log meal"){onChange(draft.copy(saveAs=when(it){"Create recipe"->"recipe";"Save meal template"->"template";else->"journal"}))}
                if(draft.saveAs=="recipe" && draft.kind==EntryKind.MEAL){Field("Yield · servings",draft.servings,{onChange(draft.copy(servings=it))},numeric=true);Field("Final cooked weight · g",draft.yieldGrams,{onChange(draft.copy(yieldGrams=it))},numeric=true)}
            }
            if(draft.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)) {
                Section("Find a food or drink", "Manual logging is always available.")
                Choices(listOf("Manual","Search","Barcode"),lookupMode){lookupMode=it;error=null;results=emptyList()}
                if(lookupMode!="Manual") {
                    Field(if(lookupMode=="Barcode")"Barcode digits"else"Search foods",query,{query=it})
                    Caption(if(lookupMode=="Barcode")"Only the barcode goes to Open Food Facts."else"Your food query goes to USDA through the configured gateway.")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Action(if(loading)"Looking up…"else"Look up",enabled=!loading){lookup(lookupMode,query)}
                        if(lookupMode=="Barcode")Action("Open scanner"){scanner.launch(ScanOptions().setDesiredBarcodeFormats(listOf(ScanOptions.EAN_8,ScanOptions.EAN_13,ScanOptions.UPC_A,ScanOptions.ITF)).setPrompt("Scan a food barcode").setBeepEnabled(false).setOrientationLocked(false))}
                    }
                    if(lookupMode=="Barcode" && query.isNotBlank())Action("Create a custom item with this barcode"){
                        reviewingEntry=Entry(kind=draft.kind,name=draft.name,sourceId=query,sourceReference=SourceReference(provider="manual",barcode=query));reviewingCandidate=null;itemEditing=null;selectingStandalone=true;itemDialog=true
                    }
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    results.forEach{candidate->Panel{
                        Text(candidate.name,style=MaterialTheme.typography.titleMedium)
                        Caption(listOfNotNull(candidate.brand,candidate.source,if(candidate.estimated)"Estimated portion"else null).joinToString(" · "))
                        Action("Review portion",enabled=!loading){scope.launch{
                            loading=true
                            try{val result=if(candidate.needsDetails || candidate.source.lowercase().contains("usda"))NutritionClient(journal.settings).details(candidate)else candidate
                                reviewingCandidate=if(draft.kind==EntryKind.DRINK)result.copy(kind=EntryKind.DRINK)else result
                                reviewingEntry=null;itemEditing=null;selectingStandalone=true;itemDialog=true
                            }catch(e:Exception){error=e.message}finally{loading=false}
                        }}
                    }}
                }
                if(lookupMode!="Manual")error?.let{Caption(it)}
                if(draft.kind==EntryKind.DRINK) { Choices(listOf("Water","Coffee","Tea","Milk","Juice","Soda","Other"),draft.name){onChange(draft.copy(name=it,water=it=="Water"))};Row { Checkbox(draft.water,{onChange(draft.copy(water=it))});Text("Count as water",Modifier.padding(top=12.dp))} }
                Field(if(draft.kind==EntryKind.DRINK)"Volume" else "Quantity (optional)",draft.quantity,{onChange(changePortion(draft,it,draft.unit))},numeric=true)
                Choices(if(draft.kind==EntryKind.DRINK)listOf("fl oz","ml")else listOf("serving","g","oz","piece","cup"),draft.unit){next->onChange(changePortion(draft,convertEditorQuantity(draft,next),next))}
                if(draft.kind==EntryKind.FOOD)Field("Portion unit or label",draft.unit,{onChange(changePortion(draft,draft.quantity,it))})
                NutrientFields(draft.nutrients){updated->onChange(draft.copy(nutrients=updated,overrides=(draft.overrides+updated.filter{(k,v)->v!=draft.nutrients[k]}.keys).distinct()))}
                Choices(listOf("unspecified","measured","estimated"),draft.evidence){onChange(draft.copy(evidence=it))}
                if(draft.isNew)Choices(listOf("Log now","Save to library"),if(draft.saveAs=="library")"Save to library"else"Log now"){onChange(draft.copy(saveAs=if(it=="Save to library")"library"else"journal"))}
                Row { Checkbox(draft.favorite,{onChange(draft.copy(favorite=it))});Text("Save usual portion as a favorite",Modifier.padding(top=12.dp)) }
                if(draft.kind==EntryKind.DRINK) {
                    Section("Drink additions", "Keep milk, sugar, or other additions inside this drink's total.")
                    draft.items.forEach { item->Panel{Text(item.name);Caption("${number(item.quantity)} ${item.unit} · ${item.nutrients["energy_kcal"]?.let{"${number(it)} kcal"} ?: "Calories unknown"}");Action("Remove addition"){onChange(draft.copy(items=draft.items-item))}} }
                    Action("+ Add milk, sugar or another addition"){selectingStandalone=false;reviewingCandidate=null;reviewingEntry=null;itemEditing=null;itemDialog=true}
                    Caption("Nutrition fields above describe the base drink. Additions contribute once inside its total.")
                }
            }
            if(draft.kind==EntryKind.ACTIVITY) {
                Choices(listOf("Walk","Hike","Run","Cycling","Workout"),draft.name){onChange(draft.copy(name=it))}
                Field("Distance · ${journal.settings.distanceUnit} (optional)",draft.distance,{onChange(draft.copy(distance=it))},numeric=true)
                Field("Duration · minutes (optional)",draft.duration,{onChange(draft.copy(duration=it))},numeric=true)
                Choices(listOf("Easy","Moderate","Hard"),draft.intensity){onChange(draft.copy(intensity=it))}
                Caption("Activities stay separate from daily steps. No exercise calories are estimated.")
            }
            if(draft.kind==EntryKind.STEPS) { val existing=journal.entries.find{it.kind==EntryKind.STEPS && it.localDate==draft.date};existing?.let { Caption("Current total: ${it.steps}. Saving replaces this date's total.") };Field("Daily step total",draft.steps,{onChange(draft.copy(steps=it))},numeric=true);Caption("Zero is a valid total. Leave a day unlogged when unknown.") }
            if(draft.kind==EntryKind.WEIGHT) {Field("Body weight",draft.weight,{onChange(draft.copy(weight=it))},numeric=true);Choices(listOf("lb","kg"),draft.unit){next->val kg=draft.weight.toDoubleOrNull()?.let{Units.weightKg(it,draft.unit)};onChange(draft.copy(unit=next,weight=kg?.let{decimal(Units.displayWeight(it,next))} ?: draft.weight))};Caption("Each measurement is kept. Trends use the daily mean.")}
            if(draft.kind==EntryKind.CHECKIN) {
                Section("How did you feel?", "Optional observations. 1 = low, 5 = high.")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { (listOf("Satisfied","Still hungry","Energized","Sleepy","Bloated","Uncomfortable","Neutral")+draft.tags).distinct().forEach { tag->Choice(tag,tag in draft.tags){onChange(draft.copy(tags=if(tag in draft.tags)draft.tags-tag else draft.tags+tag))} } }
                var custom by remember { mutableStateOf("") };Field("Custom feeling",custom,{custom=it});Action("Add tag",enabled=custom.isNotBlank()){onChange(draft.copy(tags=draft.tags+custom.trim()));custom=""}
                listOf("Hunger","Fullness","Energy").forEach { label->Caption(label);Choices(listOf("Skip","1","2","3","4","5"),draft.ratings[label.lowercase()]?.toString() ?: "Skip"){onChange(draft.copy(ratings=if(it=="Skip")draft.ratings-label.lowercase() else draft.ratings+(label.lowercase() to it.toInt())))} }
                Section("Linked meal", "Link explicitly, or keep this as a general observation.")
                Action("${if(draft.mealId==null)"✓ " else ""}No linked meal"){onChange(draft.copy(mealId=null))}
                journal.entries.filter{it.kind==EntryKind.MEAL && !it.isLibrary}.sortedByDescending{it.occurredAt}.take(15).forEach{meal->Action("${if(draft.mealId==meal.id)"✓ " else ""}${meal.name} · ${meal.localDate} ${entryTime(meal)}"){onChange(draft.copy(mealId=meal.id))}}
            }
            if(draft.kind==EntryKind.NOTE) { Choices(listOf("unmarked","partial","complete"),draft.completeness){onChange(draft.copy(completeness=it))};Caption("Daily logging completeness is your choice; it is never inferred.") }
            Field("Notes (optional)",draft.notes,{onChange(draft.copy(notes=it))},lines=3)
            Section(if(draft.kind==EntryKind.CHECKIN)"Observed at" else "Date & time")
            Field("Date · YYYY-MM-DD",draft.date,{onChange(draft.copy(date=it,offset=""))})
            if(draft.kind !in listOf(EntryKind.STEPS,EntryKind.NOTE)) {Field("Time · HH:mm (seconds optional)",draft.time,{onChange(draft.copy(time=it,offset=""))});Field("Time zone",draft.zone,{onChange(draft.copy(zone=it,offset=""))});val offsets=runCatching{ZoneId.of(draft.zone).rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(draft.date),LocalTime.parse(draft.time)))}.getOrDefault(emptyList());if(offsets.size>1){Caption("This clock time occurs twice. Choose its UTC offset.");Choices(offsets.map { it.toString() },draft.offset){onChange(draft.copy(offset=it))}} }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Section("Photos", "Stored privately on this phone. Included in exports only when you choose.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("Choose photo"){pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))};Action("Camera"){if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera()else cameraPermission.launch(Manifest.permission.CAMERA)} }
            if(draft.photos.isNotEmpty())PhotoGallery(draft.photos)
            draft.photos.forEachIndexed { index,path->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Caption("Photo ${index+1} · ${File(path).name.takeLast(16)}",Modifier.weight(1f));TextButton(onClick={onChange(draft.copy(photos=draft.photos-path))}){Text("Remove")}} }
            Caption("Draft saved locally while you type. Close to continue later.")
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            saveError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Action(if(busy)"Saving…" else if(!draft.isNew)"Save changes" else if(draft.saveAs!="journal" || draft.kind in listOf(EntryKind.RECIPE,EntryKind.TEMPLATE))"Save to library" else "Save ${kindName(draft.kind).lowercase()}",Modifier.fillMaxWidth(),primary=true,enabled=!busy,onClick=onSave)
        }
        }
    }
    if(itemDialog) PortionDialog(reviewingCandidate,reviewingEntry,allowAdditions=selectingStandalone || draft.kind!=EntryKind.DRINK,onDismiss={itemDialog=false}) { item->
        if(selectingStandalone)onChange(selectStandaloneItem(draft,item,journal.settings))
        else onChange(draft.copy(items=if(itemEditing!=null)draft.items.map{if(it.id==itemEditing)item.copy(id=it.id)else it}else draft.items+item))
        itemDialog=false;selectingStandalone=false
    }
}

@Composable
internal fun NutrientFields(values:Map<String,String>,onChange:(Map<String,String>)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Field("Calories · kcal (whole consumed portion)",values["energy_kcal"] ?: "",{onChange(values+("energy_kcal" to it))},numeric=true)
    TextButton(onClick={expanded=!expanded}) {Text(if(expanded)"Hide more nutrition" else "More nutrition")}
    if(expanded)nutrientLabels.filterKeys{it!="energy_kcal"}.forEach{(key,label)->Field("$label (optional)",values[key] ?: "",{onChange(values+(key to it))},numeric=true)}
    Caption("Blank means unknown. Enter label/reference values for this consumed portion.")
}

@Composable
internal fun PortionDialog(candidate:FoodCandidate?,original:Entry?,allowAdditions:Boolean=true,onDismiss:()->Unit,onConfirm:(Entry)->Unit) {
    var name by remember { mutableStateOf(candidate?.name ?: original?.name ?: "") }
    var quantity by remember { mutableStateOf(decimal(candidate?.quantity ?: original?.quantity ?: 1.0)) }
    var unit by remember { mutableStateOf(candidate?.unit ?: original?.unit ?: "serving") }
    var kind by remember { mutableStateOf(candidate?.kind ?: original?.kind ?: EntryKind.FOOD) }
    var evidence by remember { mutableStateOf(if(candidate?.estimated==true)"estimated" else original?.portionEvidence ?: "unspecified") }
    var additions by remember { mutableStateOf(original?.composition.orEmpty()) };var adding by remember{mutableStateOf(false)}
    var overrides by remember { mutableStateOf<Map<String,String>>(emptyMap()) };var error by remember { mutableStateOf<String?>(null) }
    val scaled=remember(candidate,original,quantity,unit,kind) {runCatching { if(candidate!=null)candidate.toEntry(quantity.toDoubleOrNull(),unit,kind) else {
        val old=original?.copy(nutrients=baseDrinkNutrition(original),nutrientEvidence=baseDrinkEvidence(original)) ?: Entry(kind=kind,name=name);val q=quantity.toDoubleOrNull();val ratio=if(original?.quantity!=null && original.quantity!!>0 && q!=null)portionFactor(original,original.quantity!!,original.unit,q,unit) else if(original==null)1.0 else null
        old.copy(kind=kind,quantity=q,unit=unit,nutrients=old.nutrients.mapValues{it.value?.let{n->ratio?.times(n)}},metadata=rescaledMetadata(original,q,unit),volumeMl=if(kind==EntryKind.DRINK)q?.let{canonicalPortion(original,it,unit,true)} else null,massGrams=if(kind==EntryKind.FOOD)q?.let{canonicalPortion(original,it,unit,false)}else null)
    } }.getOrNull() }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(22.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Section("Review portion",candidate?.source ?: original?.source ?: "Manual entry")
                (candidate?.sourceReference ?: original?.sourceReference)?.let{source->
                    if(source.attribution.isNotBlank())Caption(source.attribution)
                    if(source.license.isNotBlank())Caption(source.license)
                    if(!source.snapshotAllowed || !source.exportAllowed)Caption("Storage/export permissions for this source are pending review. Full exports and backups containing it are blocked until resolved.")
                }
                Field("Food or drink",name,{name=it});if(allowAdditions)Choices(listOf("Food","Drink"),if(kind==EntryKind.DRINK)"Drink" else "Food"){kind=if(it=="Drink")EntryKind.DRINK else EntryKind.FOOD}
                Field("Quantity",quantity,{quantity=it},numeric=true)
                val units=((candidate?.portions?.map{it.label} ?: emptyList())+listOf(unit,"serving","g","oz","piece","mL","fl oz")).distinct()
                Choices(units,unit){unit=it};Field("Unit / serving label",unit,{unit=it})
                Choices(listOf("unspecified","measured","estimated"),evidence){evidence=it}
                Caption("${scaled?.nutrients?.get("energy_kcal")?.let{"${number(it)} kcal"} ?: "Calories unknown for this portion"} · ${scaled?.matchStatus ?: "Review match"}")
                NutrientFields((scaled?.nutrients ?: emptyMap()).mapValues{decimal(it.value)}+overrides){updated->overrides=updated.filter{(k,v)->v!=decimal(scaled?.nutrients?.get(k))}}
                if(kind==EntryKind.DRINK && allowAdditions){Section("Additions");additions.forEach{item->Caption("${item.name} · ${number(item.quantity)} ${item.unit}");Action("Remove ${item.name}"){additions=additions-item}};Action("+ Add milk / sugar / other"){adding=true}}
                error?.let { Caption(it) }
                Action("Confirm item",Modifier.fillMaxWidth(),primary=true,enabled=name.isNotBlank()) {
                    runCatching {
                        val q=quantity.takeIf{it.isNotBlank()}?.toDouble();require(q==null || q>0 && q.isFinite()){ "Enter a positive portion" }
                        val base=requireNotNull(scaled){"This portion could not be converted"}
                        val nutrients=base.nutrients+overrides.mapValues{(_,v)->v.takeIf{it.isNotBlank()}?.toDouble()?.also{require(it>=0 && it.isFinite()){"Nutrition must be zero or greater"}}}
                        val reviewed=base.copy(name=name.trim(),quantity=q,unit=unit,portionEvidence=evidence,kind=kind,matchStatus="confirmed",nutrients=nutrients,nutrientEvidence=base.nutrientEvidence+overrides.mapValues{(code,v)->NutrientEvidence(status=if(v.isBlank())"unknown"else"known",origin="user_override",unit=Units.nutrientUnit(code),basis="consumed")},sourceReference=base.sourceReference?.let{it.copy(overrides=it.overrides+overrides)},countsAsWater=kind==EntryKind.DRINK && (original?.countsAsWater==true || name.equals("water",true)))
                        onConfirm(if(kind==EntryKind.DRINK && additions.isNotEmpty())Nutrition.composeDrink(reviewed,additions)else reviewed.copy(composition=emptyList()))
                    }.onFailure{error=it.message}
                }
                Action("Cancel",Modifier.fillMaxWidth(),onClick=onDismiss)
            }
        }
    }
    if(adding)PortionDialog(null,null,onDismiss={adding=false},onConfirm={additions=additions+it;adding=false},allowAdditions=false)
}

internal fun kindName(kind:EntryKind):String=when(kind){EntryKind.MEAL->"Meal";EntryKind.FOOD->"Food";EntryKind.DRINK->"Drink";EntryKind.ACTIVITY->"Activity";EntryKind.WEIGHT->"Weight";EntryKind.STEPS->"Steps";EntryKind.CHECKIN->"Check-in";EntryKind.NOTE->"Daily note";EntryKind.RECIPE->"Recipe";EntryKind.TEMPLATE->"Saved meal"}

internal fun changePortion(draft:EditorDraft,quantity:String,unit:String):EditorDraft {
    val old=draft.quantity.toDoubleOrNull();val q=quantity.toDoubleOrNull()
    val factor=if(old!=null && old>0 && q!=null)portionFactor(draft.base,old,draft.unit,q,unit)else null
    val recipe=draft.base?.recipeSnapshot
    val values=if(recipe!=null && q!=null && q>0)runCatching{Recipes.log(recipe,q,unit).nutrients.mapValues{decimal(it.value)}}.getOrDefault(draft.nutrients)else if(draft.base!=null && factor!=null)draft.nutrients.mapValues { (_,value)->value.toDoubleOrNull()?.let{decimal(it*factor)} ?: "" } else if(draft.base!=null && unit!=draft.unit)draft.nutrients.mapValues{""}else draft.nutrients
    return draft.copy(quantity=quantity,unit=unit,nutrients=values)
}
