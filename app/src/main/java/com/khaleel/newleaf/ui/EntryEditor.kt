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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun EntryEditor(draft:EditorDraft,journal:Journal,busy:Boolean,onChange:(EditorDraft)->Unit,onSave:()->Unit,onClose:()->Unit,saveError:String?=null,barcodeLookup:suspend (String)->FoodCandidate?={NutritionClient(journal.settings).barcode(it)},barcodeCapture:(((String)->Unit)->Unit)?=null) {
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val usdaRoute=if(journal.settings.usdaApiKey.isNotBlank())"directly to USDA"else if(journal.settings.gatewayUrl.isNotBlank() && journal.settings.gatewayToken.isNotBlank())"to USDA through your optional gateway"else"to USDA after you add a free personal key in Settings"
    var lookupMode by rememberSaveable { mutableStateOf(if(draft.lookupBarcode.isNullOrBlank())"Manual"else"Barcode") };var query by rememberSaveable { mutableStateOf("") }
    var findingFood by rememberSaveable { mutableStateOf(!draft.lookupBarcode.isNullOrBlank()) }
    var barcodeLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    var lookupGeneration by remember { mutableIntStateOf(0) }
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
    fun selectMethod(mode:String) {
        lookupGeneration++
        lookupJob?.cancel();lookupJob=null
        loading=false;error=null;results=emptyList();lookupMode=mode
    }
    fun lookup(mode:String,input:String) {
        if(loading || input.isBlank())return
        val generation=++lookupGeneration
        loading=true;error=null;results=emptyList()
        lookupJob=scope.launch {
            try {
                val client=NutritionClient(journal.settings)
                val found=if(mode=="Describe")client.describe(input)else client.search(input)
                currentCoroutineContext().ensureActive()
                if(generation==lookupGeneration) {
                    results=found
                    if(found.isEmpty())error="No match found. Try another name or enter it yourself."
                }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e
            } catch(e:Exception) {
                currentCoroutineContext().ensureActive()
                if(generation==lookupGeneration)error=if(e is NutritionException)e.message else "Lookup unavailable. Continue manually."
            } finally { if(generation==lookupGeneration)loading=false }
        }
    }
    fun reviewCandidate(candidate:FoodCandidate,standalone:Boolean) {
        if(loading)return
        val generation=++lookupGeneration
        loading=true;error=null
        lookupJob=scope.launch {
            try {
                val found=if(candidate.needsDetails || candidate.source.lowercase().contains("usda"))NutritionClient(journal.settings).details(candidate)else candidate
                currentCoroutineContext().ensureActive()
                if(generation==lookupGeneration) {
                    reviewingCandidate=if(standalone && draft.kind==EntryKind.DRINK)found.copy(kind=EntryKind.DRINK)else found
                    reviewingEntry=null;itemEditing=null;selectingStandalone=standalone;itemDialog=true
                }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e
            } catch(e:Exception) {
                currentCoroutineContext().ensureActive()
                if(generation==lookupGeneration)error=if(e is NutritionException)e.message else "Food details could not load. Try again or enter it yourself."
            } finally { if(generation==lookupGeneration)loading=false }
        }
    }
    fun reviewBarcode(candidate:FoodCandidate) {
        reviewingCandidate=if(draft.kind==EntryKind.DRINK)candidate.copy(kind=EntryKind.DRINK)else candidate
        reviewingEntry=null;itemEditing=null
        selectingStandalone=draft.kind in listOf(EntryKind.FOOD,EntryKind.DRINK);itemDialog=true
    }
    fun manualBarcode(code:String) {
        reviewingEntry=Entry(kind=if(draft.kind==EntryKind.DRINK)EntryKind.DRINK else EntryKind.FOOD,name=draft.name.takeIf{draft.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)} ?: "",sourceId=code,sourceReference=SourceReference(provider="manual",barcode=code))
        reviewingCandidate=null;itemEditing=null
        selectingStandalone=draft.kind in listOf(EntryKind.FOOD,EntryKind.DRINK);itemDialog=true
    }
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!busy)onClose()},containerColor=MaterialTheme.colorScheme.background,dragHandle={BottomSheetDefaults.DragHandle()},modifier=Modifier.fillMaxHeight(.95f)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal=22.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(!draft.isNew)"Edit ${kindName(draft.kind).lowercase()}" else if(draft.kind==EntryKind.FOOD)"What did you eat?"else if(draft.kind==EntryKind.DRINK)"What did you drink?"else if(draft.kind==EntryKind.MEAL)"What did you have?"else if(draft.kind==EntryKind.ACTIVITY)"What did you do?"else "Add ${kindName(draft.kind).lowercase()}",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));TextButton(onClick=onClose,enabled=!busy){Text("Close")} }
            Caption("${draft.date} · ${draft.time}")
            if(draft.kind==EntryKind.DRINK) {
                val commonDrinks=listOf("Water","Coffee","Tea","Juice","Milk")
                SelectField("Drink",draft.name.takeIf{it in commonDrinks} ?: "Other",commonDrinks+"Other") { next->
                    onChange(selectDrink(draft,if(next=="Other")""else next))
                }
                if(draft.name !in commonDrinks)Field("Drink name",draft.name,{onChange(draft.copy(name=it,water=it.equals("water",true)))})
            } else if(draft.kind!=EntryKind.ACTIVITY && !(draft.kind==EntryKind.WEIGHT && draft.name=="Body weight"))Field(if(draft.kind==EntryKind.MEAL)"Meal name"else if(draft.kind==EntryKind.FOOD)"Food or meal"else "Name",draft.name,{onChange(draft.copy(name=it))},lines=if(draft.kind==EntryKind.FOOD)3 else 1)
            if(draft.kind in listOf(EntryKind.MEAL,EntryKind.RECIPE,EntryKind.TEMPLATE)) {
                if(draft.kind==EntryKind.MEAL)SelectField("Meal (optional)",draft.category,listOf("Breakfast","Lunch","Dinner","Snack","Other")+listOfNotNull(draft.category.takeIf{it.isNotBlank()}),optional=true){onChange(draft.copy(category=it))}
                Section(if(draft.kind==EntryKind.RECIPE)"Ingredients" else "Your meal")
                draft.items.forEach { item->Panel { Text(item.name,style=MaterialTheme.typography.titleMedium);Caption("${number(item.quantity)} ${item.unit} · ${item.nutrients["energy_kcal"]?.let { "${number(it)} kcal" } ?: "Calories unknown"}");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Edit"){reviewingEntry=item;reviewingCandidate=null;itemEditing=item.id;itemDialog=true};Action("Remove"){onChange(draft.copy(items=draft.items.filterNot {it.id==item.id}))}} } }
                if(!findingFood) {
                    Action("Add food or drink",Modifier.fillMaxWidth()){selectingStandalone=false;reviewingCandidate=null;reviewingEntry=null;itemEditing=null;itemDialog=true}
                    TextButton(onClick={findingFood=true;selectMethod("Search")}){Text("Find a food")}
                } else {
                    SelectField("Find by",when(lookupMode){"Barcode"->"Barcode";"Describe"->"Food list";else->"Name"},listOf("Name","Barcode","Food list")){selectMethod(when(it){"Barcode"->"Barcode";"Food list"->"Describe";else->"Search"})}
                    TextButton(onClick={selectMethod("Manual");findingFood=false}){Text("Back to entry")}
                }
                if(findingFood)when(lookupMode) {
                    "Manual"->Unit
                    "Describe"->{
                        Field("Describe foods",draft.originalText,{onChange(draft.copy(originalText=it))},lines=3)
                        Caption("List up to 8 foods separated by commas or new lines, with optional quantity and unit: 2 eggs, 1 cup milk.")
                        Caption("Food queries go $usdaRoute. Check each suggestion and amount before adding.")
                        Action(if(loading)"Finding suggestions…" else "Find suggestions",enabled=!loading){lookup("Describe",draft.originalText)}
                    }
                    "Barcode"->BarcodeLookupSection(barcodeLookup,::reviewBarcode,::manualBarcode,{barcodeLoading=it},draft.lookupBarcode.orEmpty(),{if(it!=draft.lookupBarcode.orEmpty())onChange(draft.copy(lookupBarcode=it))},barcodeCapture)
                    else->{Field("Search foods",query,{query=it});Caption("Search sends your query $usdaRoute.");Action(if(loading)"Looking up…" else "Look up",enabled=!loading && query.isNotBlank()){lookup("Search",query)}}
                }
                error?.let { Caption(it) }
                if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                results.forEach { result->Panel { Text(result.name,style=MaterialTheme.typography.titleMedium);Caption(listOfNotNull(result.brand,result.source,result.quantity?.let{"${number(it)} ${result.unit}"},if(result.quantity==null)"Portion unknown"else if(result.estimated)"Estimated portion" else null).joinToString(" · "));Action("Review item",enabled=!loading){reviewCandidate(result,false)} } }
                val local=journal.entries.filter { it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK) && (query.isBlank() || it.name.contains(query,true)) }.distinctBy { "${it.name}:${it.quantity}:${it.unit}" }.sortedByDescending { it.isFavorite }.take(8)
                if(local.isNotEmpty() && lookupMode=="Manual") { Disclosure("Favorites & recent"){local.forEach { item->Action("${if(item.isFavorite)"♡  " else ""}${item.name} · ${number(item.quantity)} ${item.unit}",Modifier.fillMaxWidth()){ reviewingEntry=item.copy(id=UUID.randomUUID().toString(),mealId=null,isLibrary=false);reviewingCandidate=null;itemEditing=null;itemDialog=true } } } }
                if(draft.kind==EntryKind.RECIPE) { Field("Yield · servings",draft.servings,{onChange(draft.copy(servings=it))},numeric=true);Field("Final cooked weight · g (optional)",draft.yieldGrams,{onChange(draft.copy(yieldGrams=it))},numeric=true);Caption("Gram portions require final cooked weight. Missing ingredient nutrition remains unknown.") }
                if(draft.kind==EntryKind.MEAL && draft.isNew)Disclosure("Save this meal to your library"){Choices(listOf("Log meal","Save meal template","Create recipe"),if(draft.saveAs=="template")"Save meal template" else if(draft.saveAs=="recipe")"Create recipe" else "Log meal"){onChange(draft.copy(saveAs=when(it){"Create recipe"->"recipe";"Save meal template"->"template";else->"journal"}))}}
                if(draft.saveAs=="recipe" && draft.kind==EntryKind.MEAL){Field("Yield · servings",draft.servings,{onChange(draft.copy(servings=it))},numeric=true);Field("Final cooked weight · g",draft.yieldGrams,{onChange(draft.copy(yieldGrams=it))},numeric=true)}
            }
            if(draft.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)) {
                if(draft.kind==EntryKind.DRINK)PortionFields(draft,onChange)
                if(draft.kind==EntryKind.FOOD) {
                    SelectField("Meal (optional)",draft.category,listOf("Breakfast","Lunch","Dinner","Snack","Other")+listOfNotNull(draft.category.takeIf{it.isNotBlank()}),optional=true){onChange(draft.copy(category=it))}
                    if(draft.quantity.isNotBlank())Caption("${draft.quantity} ${draft.unit}")
                }
                PopulatedNutrition(draft.nutrients.mapValues{it.value.toDoubleOrNull()},draft.base?.nutrientEvidence.orEmpty())
                if(!findingFood)TextButton(onClick={findingFood=true;selectMethod("Search")}){Text("Find a food")}
                else {
                    SelectField("Find by",if(lookupMode=="Barcode")"Barcode"else"Name",listOf("Name","Barcode")){selectMethod(if(it=="Barcode")"Barcode"else"Search")}
                    TextButton(onClick={selectMethod("Manual");findingFood=false}){Text("Back to entry")}
                    if(lookupMode=="Barcode")BarcodeLookupSection(barcodeLookup,::reviewBarcode,::manualBarcode,{barcodeLoading=it},draft.lookupBarcode.orEmpty(),{if(it!=draft.lookupBarcode.orEmpty())onChange(draft.copy(lookupBarcode=it))},barcodeCapture)
                    if(lookupMode=="Search") {
                        Field("Search foods",query,{query=it})
                        Caption("Your food query goes $usdaRoute.")
                        Action(if(loading)"Finding…"else"Find food",enabled=!loading && query.isNotBlank()){lookup("Search",query)}
                        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                        results.forEach{candidate->Panel{
                            Text(candidate.name,style=MaterialTheme.typography.titleMedium)
                            candidate.brand?.let{Caption(it)}
                            Action("Choose food",enabled=!loading){reviewCandidate(candidate,true)}
                        }}
                    }
                    error?.let{Caption(it)}
                }
            }
            if(draft.kind==EntryKind.ACTIVITY) {
                SelectField("Activity",draft.name,listOf("Walk","Hike","Run","Cycling","Workout","Other")+listOfNotNull(draft.name.takeIf{it.isNotBlank()})){onChange(draft.copy(name=it))}
                if(draft.name=="Other" || draft.name !in listOf("Walk","Hike","Run","Cycling","Workout"))Field("Activity name",draft.name,{onChange(draft.copy(name=it))})
                Field("Distance · ${journal.settings.distanceUnit} (optional)",draft.distance,{onChange(draft.copy(distance=it))},numeric=true)
                Field("Duration · minutes (optional)",draft.duration,{onChange(draft.copy(duration=it))},numeric=true)

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
            val additionalDetails: @Composable () -> Unit = {
                if(draft.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)) {
                if(draft.kind==EntryKind.FOOD)PortionFields(draft,onChange)
                Field("Calories (optional)",draft.nutrients["energy_kcal"].orEmpty(),{onChange(draft.copy(nutrients=draft.nutrients+("energy_kcal" to it),overrides=(draft.overrides+"energy_kcal").distinct()))},numeric=true)
                if(draft.kind==EntryKind.DRINK)SelectField("Meal (optional)",draft.category,listOf("Breakfast","Lunch","Dinner","Snack","Other")+listOfNotNull(draft.category.takeIf{it.isNotBlank()}),optional=true){onChange(draft.copy(category=it))}
                SelectField("Log as",if(draft.kind==EntryKind.DRINK)"Drink"else"Food",listOf("Food","Drink")){onChange(draft.copy(kind=if(it=="Drink")EntryKind.DRINK else EntryKind.FOOD,water=it=="Drink" && draft.name.equals("water",true)))}
                Disclosure("Nutrition & saved food") {
                nutrientLabels.filterKeys{it!="energy_kcal"}.forEach{(key,label)->Field("$label (optional)",draft.nutrients[key].orEmpty(),{onChange(draft.copy(nutrients=draft.nutrients+(key to it),overrides=(draft.overrides+key).distinct()))},numeric=true)}
                Field("Custom unit / serving label",draft.unit,{onChange(changePortion(draft,draft.quantity,it))})
                SelectField("How was the amount determined?",draft.evidence,listOf("unspecified","measured","estimated")){onChange(draft.copy(evidence=it))}
                if(draft.isNew)Choices(listOf("Log now","Save to library"),if(draft.saveAs=="library")"Save to library"else"Log now"){onChange(draft.copy(saveAs=if(it=="Save to library")"library"else"journal"))}
                Row { Checkbox(draft.favorite,{onChange(draft.copy(favorite=it))});Text("Save usual portion as a favorite",Modifier.padding(top=12.dp)) }
                if(draft.kind==EntryKind.DRINK)Row { Checkbox(draft.water,{onChange(draft.copy(water=it))});Text("Count as water",Modifier.padding(top=12.dp))}
                }
                if(draft.kind==EntryKind.DRINK) {
                    Disclosure("Drink additions") {
                    Caption("Keep milk, sugar, or other additions inside this drink's total.")
                    draft.items.forEach { item->Panel{Text(item.name);Caption("${number(item.quantity)} ${item.unit} · ${item.nutrients["energy_kcal"]?.let{"${number(it)} kcal"} ?: "Calories unknown"}");Action("Remove addition"){onChange(draft.copy(items=draft.items-item))}} }
                    Action("+ Add milk, sugar or another addition"){selectingStandalone=false;reviewingCandidate=null;reviewingEntry=null;itemEditing=null;itemDialog=true}
                    Caption("Nutrition fields above describe the base drink. Additions contribute once inside its total.")
                    }
                }
                }
            if(draft.kind==EntryKind.MEAL)Field("Custom meal category",draft.category,{onChange(draft.copy(category=it))})
            if(draft.kind==EntryKind.ACTIVITY)SelectField("Intensity (optional)",draft.intensity,listOf("Easy","Moderate","Hard"),optional=true){onChange(draft.copy(intensity=it))}
            Disclosure("Notes & photos") {
            Field("Notes (optional)",draft.notes,{onChange(draft.copy(notes=it))},lines=3)
            Section("Photos", "Stored privately on this phone. Included in exports only when you choose.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("Choose photo"){pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))};Action("Camera"){if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera()else cameraPermission.launch(Manifest.permission.CAMERA)} }
            if(draft.photos.isNotEmpty())PhotoGallery(draft.photos)
            draft.photos.forEachIndexed { index,path->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Caption("Photo ${index+1} · ${File(path).name.takeLast(16)}",Modifier.weight(1f));TextButton(onClick={onChange(draft.copy(photos=draft.photos-path))}){Text("Remove")}} }
            }
            Disclosure(if(draft.kind==EntryKind.CHECKIN)"Observation time" else "Change date & time") {
            Section(if(draft.kind==EntryKind.CHECKIN)"Observed at" else "Date & time")
            Field("Date · YYYY-MM-DD",draft.date,{onChange(draft.copy(date=it,offset=""))})
            if(draft.kind !in listOf(EntryKind.STEPS,EntryKind.NOTE)) {Field("Time · HH:mm (seconds optional)",draft.time,{onChange(draft.copy(time=it,offset=""))});Disclosure("Time zone"){Field("Time zone",draft.zone,{onChange(draft.copy(zone=it,offset=""))})};val offsets=runCatching{ZoneId.of(draft.zone).rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(draft.date),LocalTime.parse(draft.time)))}.getOrDefault(emptyList());if(offsets.size>1){Caption("This clock time occurs twice. Choose its UTC offset.");Choices(offsets.map { it.toString() },draft.offset){onChange(draft.copy(offset=it))}} }
            }
            }
            Disclosure("More details",content=additionalDetails)
            if(lookupMode=="Manual")error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            saveError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Action(if(busy)"Saving…" else if(!draft.isNew)"Save changes" else if(draft.saveAs!="journal" || draft.kind in listOf(EntryKind.RECIPE,EntryKind.TEMPLATE))"Save to library" else "Save ${kindName(draft.kind).lowercase()}",Modifier.fillMaxWidth(),primary=true,enabled=!busy && !loading && !barcodeLoading && !itemDialog && draft.name.isNotBlank(),onClick=onSave)
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
private fun PortionFields(draft:EditorDraft,onChange:(EditorDraft)->Unit) {
    val amount: @Composable (Modifier)->Unit = { modifier->Field(if(draft.kind==EntryKind.DRINK)"Volume (optional)"else"Amount (optional)",draft.quantity,{onChange(changePortion(draft,it,draft.unit))},modifier,numeric=true) }
    val unit: @Composable (Modifier)->Unit = { modifier->
        Column(modifier) {SelectField("Unit",draft.unit,(listOf(draft.unit)+if(draft.kind==EntryKind.DRINK)listOf("fl oz","ml","serving")else listOf("serving","g","oz","piece","cup"))+draft.base?.portionDefinitions.orEmpty().map{it.label}){next->onChange(changePortion(draft,convertEditorQuantity(draft,next),next))}}
    }
    if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(10.dp)){amount(Modifier);unit(Modifier)}
    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.Bottom) {amount(Modifier.weight(1f));unit(Modifier.weight(1f))}
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
internal fun PortionDialog(candidate:FoodCandidate?,original:Entry?,allowAdditions:Boolean=true,quickLog:Boolean=false,busy:Boolean=false,saveError:String?=null,onDismiss:()->Unit,onConfirm:(Entry)->Unit) {
    var name by remember { mutableStateOf(candidate?.name ?: original?.name ?: "") }
    val packageSize=candidate?.takeIf{it.barcode!=null}?.portions?.firstOrNull{it.label=="package"}
    var quantity by remember { mutableStateOf(if(packageSize!=null)"1"else if(candidate!=null)decimal(candidate.quantity)else decimal(original?.quantity)) }
    var unit by remember { mutableStateOf(if(packageSize!=null)"package"else candidate?.unit ?: original?.unit ?: "serving") }
    var percent by remember { mutableStateOf(false) }
    val consumedQuantity=quantity.toDoubleOrNull()?.let{if(percent)it/100.0 else it}
    var kind by remember { mutableStateOf(candidate?.kind ?: original?.kind ?: EntryKind.FOOD) }
    var category by remember { mutableStateOf(if(quickLog)""else original?.category.orEmpty()) }
    var submitting by remember { mutableStateOf(false) }
    LaunchedEffect(busy,saveError) { if(!busy)submitting=false }
    val locked=busy || submitting
    var evidence by remember { mutableStateOf(if(candidate?.estimated==true)"estimated" else original?.portionEvidence ?: "unspecified") }
    var additions by remember { mutableStateOf(original?.composition.orEmpty()) };var adding by remember{mutableStateOf(false)}
    var overrides by remember { mutableStateOf<Map<String,String>>(emptyMap()) };var error by remember { mutableStateOf<String?>(null) }
    val scaled=remember(candidate,original,quantity,unit,kind,percent) {runCatching { if(candidate!=null)candidate.toEntry(consumedQuantity,unit,kind) else {
        val old=original?.copy(nutrients=baseDrinkNutrition(original),nutrientEvidence=baseDrinkEvidence(original)) ?: Entry(kind=kind,name=name);val q=quantity.toDoubleOrNull();val ratio=if(original?.quantity!=null && original.quantity!!>0 && q!=null)portionFactor(original,original.quantity!!,original.unit,q,unit) else if(original==null)1.0 else null
        old.copy(kind=kind,quantity=q,unit=unit,nutrients=old.nutrients.mapValues{it.value?.let{n->ratio?.times(n)}},metadata=rescaledMetadata(original,q,unit),volumeMl=if(kind==EntryKind.DRINK)q?.let{canonicalPortion(original,it,unit,true)} else null,massGrams=if(kind==EntryKind.FOOD)q?.let{canonicalPortion(original,it,unit,false)}else null)
    } }.getOrNull() }
    Dialog(onDismissRequest={if(!locked)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(22.dp).navigationBarsPadding().imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Section(if(quickLog)"Log scanned item"else"Review portion")
                Text(candidate?.name ?: original?.name ?: "New item",style=MaterialTheme.typography.headlineMedium)
                candidate?.brand?.takeIf{it.isNotBlank()}?.let{Caption(it)}
                if(packageSize!=null) {
                    val size=packageSize.volumeMl?.let{"${number(it)} mL"} ?: "${number(packageSize.massGrams)} g"
                    val label=candidate?.metadata?.get("off_package_label").orEmpty()
                    Caption("Whole package: $size${if(label.isNotBlank() && !label.equals(size,true)) " ($label)" else ""}. Check this against your label.")
                    Caption("Enter 0.5 package or 50 percent for half. Change the amount to what you had.")
                    candidate?.servingVolumeMl?.let{Caption("One label serving: ${number(it)} mL")}
                    candidate?.servingMassG?.let{Caption("One label serving: ${number(it)} g")}
                } else if(candidate?.barcode!=null && candidate.quantity!=null) {
                    Caption("Label amount: ${decimal(candidate.quantity)} ${candidate.unit}. Change it to the amount you had.")
                    Caption("Package size not listed. Use the amount on your label; a serving may be less than the whole package.")
                }
                if(candidate!=null && candidate.quantity==null && packageSize==null)Caption("Amount unknown. Leave blank if you don't know.")
                if(candidate?.metadata?.get("description_mode")=="usda_assisted") {
                    Caption(if(candidate.quantity==null)"No amount supplied. Leave blank if unknown."else"Amount suggested from your text. Check it before adding.")
                    if(candidate.metadata["unit_recognized"]=="false")Caption("Check the unit. Nutrition cannot be converted yet.")
                } else if(candidate?.estimated==true)Caption("Suggested amount. Check it before adding.")
                if(candidate==null && original?.name.isNullOrBlank())Field("Food or drink",name,{name=it},enabled=!locked)
                if(quickLog) {
                    SelectField("Category",category,listOf("Breakfast","Lunch","Dinner","Snack","Other"),enabled=!locked,placeholder="Choose a category"){category=it}
                }
                if(allowAdditions)SelectField("Log as",if(kind==EntryKind.DRINK)"Drink"else"Food",listOf("Food","Drink"),enabled=!locked){kind=if(it=="Drink")EntryKind.DRINK else EntryKind.FOOD}
                Field(if(percent)"Percent of package"else"Amount (optional)",quantity,{quantity=it;error=null},numeric=true,enabled=!locked)
                val units=((candidate?.portions?.map{it.label} ?: emptyList())+(if(packageSize!=null)listOf("Percent of package")else emptyList())+listOf(unit,"serving")+if(kind==EntryKind.DRINK)listOf("mL","fl oz")else listOf("g","oz")).distinct()
                SelectField("Unit",if(percent)"Percent of package"else unit,units,enabled=!locked){next->
                    val nextPercent=next=="Percent of package"
                    val nextUnit=if(nextPercent)"package"else next
                    val converted=consumedQuantity?.let { q->
                        candidate?.convertAmount(q,unit,nextUnit) ?: Units.convert(q,unit,nextUnit) ?: original?.let {
                            val volume=kind==EntryKind.DRINK
                            val current=canonicalPortion(it,q,unit,volume)
                            val target=canonicalPortion(it,1.0,nextUnit,volume)
                            if(current!=null && target!=null && target>0)current/target else null
                        }
                    }
                    quantity=decimal(converted?.let{if(nextPercent)it*100.0 else it})
                    unit=nextUnit;percent=nextPercent;error=null
                }
                (candidate?.sourceReference ?: original?.sourceReference)?.let{source->
                    if(!source.snapshotAllowed || !source.exportAllowed)Caption("This source has storage/export restrictions. Full exports and backups are blocked until resolved.")
                }
                val previewCalories=if("energy_kcal" in overrides)overrides["energy_kcal"]?.toDoubleOrNull()else scaled?.nutrients?.get("energy_kcal")
                Caption(previewCalories?.let{"${number(it)} kcal"} ?: "Calories unknown for this portion")
                PopulatedNutrition((scaled?.nutrients.orEmpty()+overrides.mapValues{it.value.toDoubleOrNull()}),scaled?.nutrientEvidence.orEmpty(),includeCalories=false)
                if(candidate!=null && candidate.nutrients.values.any{it!=null} && (scaled==null || scaled.nutrients.values.none{it!=null})) {
                    Caption("Nutrition is listed, but this amount cannot be converted. Choose a measured amount or label serving.")
                    PopulatedNutrition(candidate.nutrients,candidate.nutrients.keys.associateWith{code->NutrientEvidence(unit=candidate.nutrientUnits[code] ?: Units.nutrientUnit(code),status=if(code in candidate.estimatedNutrients)"estimated"else"known")},title="Label nutrition · ${decimal(candidate.basisQuantity)} ${candidate.basisUnit}")
                }
                Disclosure("Details") {
                    if(candidate!=null || !original?.name.isNullOrBlank())Field("Food or drink",name,{name=it},enabled=!locked)
                    NutrientFields((scaled?.nutrients ?: emptyMap()).mapValues{decimal(it.value)}+overrides){updated->if(!locked)overrides=updated.filter{(k,v)->v!=decimal(scaled?.nutrients?.get(k))}}
                    Field("Unit / serving label",unit,{unit=it;percent=false},enabled=!locked)
                    SelectField("How was the amount determined?",evidence,listOf("unspecified","measured","estimated"),enabled=!locked){evidence=it}
                    candidate?.originalText?.takeIf{it.isNotBlank()}?.let{Caption("Your input: $it")}
                    Caption("Source: ${candidate?.source ?: original?.source ?: "Manual entry"}")
                    (candidate?.sourceReference ?: original?.sourceReference)?.let{source->
                        if(source.attribution.isNotBlank())Caption(source.attribution)
                        if(source.license.isNotBlank())Caption(source.license)
                    }
                    if(kind==EntryKind.DRINK && allowAdditions){
                        additions.forEach{item->Caption("${item.name} · ${number(item.quantity)} ${item.unit}");Action("Remove ${item.name}",enabled=!locked){additions=additions-item}}
                        Action("Add milk, sugar or another ingredient",enabled=!locked){adding=true}
                    }
                }

            }
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                saveError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                Action(if(locked)"Adding…"else if(quickLog)"Add to journal"else"Confirm item",Modifier.fillMaxWidth(),primary=true,enabled=!locked && name.isNotBlank() && (!quickLog || category.isNotBlank())) {
                    if(busy || submitting || quickLog && category.isBlank())return@Action
                    runCatching {
                        val entered=quantity.takeIf{it.isNotBlank()}?.toDouble();val q=entered?.let{if(percent)it/100.0 else it};require(q==null || q>0 && q.isFinite()){ "Enter a positive portion" }
                        val base=requireNotNull(scaled){"This portion could not be converted"}
                        val nutrients=base.nutrients+overrides.mapValues{(_,v)->v.takeIf{it.isNotBlank()}?.toDouble()?.also{require(it>=0 && it.isFinite()){"Nutrition must be zero or greater"}}}
                        val reviewed=base.copy(name=name.trim(),quantity=q,unit=unit,portionEvidence=evidence,category=category,kind=kind,matchStatus="confirmed",metadata=base.metadata+mapOf("portion_input_quantity" to quantity,"portion_input_unit" to if(percent)"percent_of_package"else unit),nutrients=nutrients,nutrientEvidence=base.nutrientEvidence+overrides.mapValues{(code,v)->NutrientEvidence(status=if(v.isBlank())"unknown"else"known",origin="user_override",unit=Units.nutrientUnit(code),basis="consumed")},sourceReference=base.sourceReference?.let{it.copy(overrides=it.overrides+overrides)},countsAsWater=kind==EntryKind.DRINK && (original?.countsAsWater==true || name.equals("water",true)))
                        if(quickLog)submitting=true
                        onConfirm(if(kind==EntryKind.DRINK && additions.isNotEmpty())Nutrition.composeDrink(reviewed,additions)else reviewed.copy(composition=emptyList()))
                    }.onFailure{submitting=false;error=when(it.message){"Enter a positive portion"->"Enter an amount greater than zero, or leave it blank.";"This portion could not be converted"->"This amount could not be converted. Check the amount and unit.";"Nutrition must be zero or greater"->"Calories and nutrition must be zero or greater.";else->"The item could not be added. Check the amount and nutrition, then try again."}}
                }
                Action("Cancel",Modifier.fillMaxWidth(),enabled=!locked,onClick=onDismiss)
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

/** Show values that are present without asking the user to discover a disclosure. */
@Composable
internal fun PopulatedNutrition(values:Map<String,Double?>,evidence:Map<String,NutrientEvidence>,includeCalories:Boolean=true,title:String="Nutrition for this amount") {
    val extras=values.keys.filter{it !in nutrientLabels && it !in setOf("off_nova_group","off_energy_kj","off_energy")}
    val known=(nutrientLabels.keys+extras).filter{values[it]!=null && (includeCalories || it!="energy_kcal")}
    if(known.isNotEmpty()) {
        Panel(Modifier.fillMaxWidth()) {
            Caption(title)
            known.forEach{code->
                val label=nutrientLabels[code] ?: when(code) {
                    "off_carbohydrates"->"Available carbohydrates · ${evidence[code]?.unit.orEmpty()}"
                    "off_salt"->"Salt · ${evidence[code]?.unit.orEmpty()}"
                    else->code.removePrefix("off_").replace('_',' ').replaceFirstChar{it.uppercase()}+evidence[code]?.unit?.takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()
                }
                Caption("$label: ${number(values[code])}${if(evidence[code]?.status=="estimated")" (estimated)"else""}")
            }
            Caption("Only listed values are shown. Other nutrients are unknown.")
        }
    }
}
