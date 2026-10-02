package com.khaleel.newleaf.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import com.khaleel.newleaf.nutrition.NutritionClient
import com.khaleel.newleaf.nutrition.FoodCandidate
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.khaleel.newleaf.data.*
import com.khaleel.newleaf.reminders.MealReminders
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewleafApp(repository:JournalRepository,pendingMealId:String?=null,onPendingHandled:()->Unit={},quickBarcodeLookup:(suspend (String)->FoodCandidate?)?=null,quickBarcodeCapture:(((String?)->Unit)->Unit)?=null) {
    val journal by repository.state.collectAsState()
    val context=LocalContext.current
    val scope=rememberCoroutineScope();val snackbar=remember { SnackbarHostState() };val gson=remember { Gson() }
    var loaded by remember { mutableStateOf(false) };var loadError by remember{mutableStateOf<String?>(null)};var tab by rememberSaveable { mutableStateOf("Today") }
    var date by remember { mutableStateOf(LocalDate.now()) };var screen by rememberSaveable { mutableStateOf<String?>(null) }
    var portabilityOrigin by remember { mutableStateOf<String?>(null) }
    var draftKey by rememberSaveable{mutableStateOf("editor")}
    var draft by remember { mutableStateOf<EditorDraft?>(null) };var detail by remember { mutableStateOf<Entry?>(null) }
    var busy by remember { mutableStateOf(false) };var editorError by remember{mutableStateOf<String?>(null)};var deleteTarget by remember { mutableStateOf<Entry?>(null) }
    var deleteFoods by remember { mutableStateOf(true) };var deleteDrinks by remember { mutableStateOf(false) };var deleteCheckins by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {try{val stored=repository.awaitLoaded();
        if(screen=="editor" && draft==null)draft=stored.drafts[draftKey]?.let{runCatching{gson.fromJson(it,EditorDraft::class.java)}.getOrNull()}
        loaded=true}catch(e:Exception){loadError=e.message ?: "Journal could not be opened"}}
    LaunchedEffect(draft,draftKey) { draft?.let { current->delay(250);try{repository.saveDraft(draftKey,gson.toJson(current))}catch(e:Exception){snackbar.showSnackbar("Draft could not be saved")}} }
    fun notify(message:String) { scope.launch { snackbar.showSnackbar(message) } }
    fun open(kind:EntryKind,linked:String?=null) { editorError=null;draftKey="editor:${UUID.randomUUID()}";draft=EditorDraft.new(kind,if(tab=="Today")LocalDate.now() else date,journal.settings,linked);screen="editor" }
    fun edit(entry:Entry) {editorError=null;draftKey="editor:${UUID.randomUUID()}";draft=EditorDraft.edit(entry,journal.entries,journal.settings);detail=null;screen="editor"}
    fun save() {
        val current=draft ?: return;val savingKey=draftKey;if(busy)return;busy=true;editorError=null
        scope.launch {try{
            val real=when(current.saveAs){"recipe"->current.copy(kind=EntryKind.RECIPE);"template"->current.copy(kind=EntryKind.TEMPLATE);else->current}
            val entries=real.toEntries(journal.settings)
            val before=repository.state.value
            val entryIds=entries.map{it.id}.toSet()
            val addedIds=entryIds-before.entries.map{it.id}.toSet()
            val removedIds=if(real.kind==EntryKind.MEAL && !real.isNew)before.entries.filter{it.mealId==real.id && it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK) && it.id !in entryIds}.map{it.id}.toSet()else emptySet()
            val undo=repository.applyEdits(entries,removedIds)
            repository.clearDraft(savingKey);draft=null;screen=null
            scope.launch {
                val result=snackbar.showSnackbar(if(!real.isNew)"Changes saved" else "${kindName(real.kind)} saved",actionLabel="Undo")
                if(result==SnackbarResult.ActionPerformed)try{repository.applyEdits(undo,addedIds)}catch(e:Exception){notify(e.message ?: "Undo could not be applied")}
            }
        }catch(e:Exception){editorError=e.message ?: "Save failed. Your draft is still here."}finally{busy=false} }
    }
    fun saveScanned(entry:Entry) {
        if(busy)return
        busy=true;editorError=null
        val selected=if(tab=="Today")LocalDate.now()else date
        val zone=ZoneId.systemDefault()
        val saved=EventTimes.at(entry.copy(id=UUID.randomUUID().toString(),isLibrary=false),selected.atTime(LocalTime.now()).atZone(zone).toInstant(),zone)
        scope.launch {
            try {
                repository.upsert(saved)
                screen=null
                scope.launch {
                    if(snackbar.showSnackbar("Added to your journal",actionLabel="Undo")==SnackbarResult.ActionPerformed)repository.delete(setOf(saved.id))
                }
            } catch(e:Exception) {
                editorError="Couldn't save yet. Your product is still here. Try again."
            } finally { busy=false }
        }
    }
    fun quickWater(oz:Int) {
        if(busy)return;busy=true
        scope.launch { try { val zone=ZoneId.systemDefault();val selected=if(tab=="Today")LocalDate.now() else date;val instant=selected.atTime(LocalTime.now()).atZone(zone).toInstant()
            val water=EventTimes.at(Entry(kind=EntryKind.DRINK,name="Water",quantity=oz.toDouble(),unit="fl oz",volumeMl=oz*29.5735295625,countsAsWater=true,portionEvidence="measured",nutrients=mapOf("energy_kcal" to 0.0)),instant,zone)
            repository.upsert(water);busy=false
            if(snackbar.showSnackbar("$oz US fl oz water logged",actionLabel="Undo")==SnackbarResult.ActionPerformed)repository.delete(setOf(water.id))
        }catch(e:Exception){notify(e.message ?: "Water could not be saved")}finally{busy=false} }
    }
    LaunchedEffect(loaded) {
        if(loaded)runCatching{
            val activePhotos=draft?.let{PhotoReferences.collect(Journal(drafts=mapOf(draftKey to gson.toJson(it))))}
            if(draft==null || activePhotos!=null)repository.purgeUnreferencedPhotos(minimumAgeMillis=24L*60*60*1000,protectedPaths=activePhotos.orEmpty())
        }
    }
    LaunchedEffect(loaded,journal.entries,journal.settings.remindersEnabled) {
        if(loaded)runCatching{MealReminders.sync(context,journal)}.onFailure{notify("Reminders could not be scheduled")}
    }
    LaunchedEffect(loaded,pendingMealId) {
        if(loaded && pendingMealId!=null){
            val meal=journal.entries.find{it.id==pendingMealId && it.kind==EntryKind.MEAL && !it.isLibrary}
            if(meal!=null){draftKey="editor:${UUID.randomUUID()}";draft=EditorDraft.new(EntryKind.CHECKIN,LocalDate.now(),journal.settings,meal.id);screen="editor";tab="Today";date=LocalDate.now()}
            else notify("This meal is no longer in your journal")
            onPendingHandled()
        }
    }
    NewleafTheme(journal.settings.theme) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal=20.dp,vertical=10.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("Today","History","Trends","More").forEach { label->Box(Modifier.weight(1f).heightIn(min=52.dp).semantics{selected=tab==label}.clickable(enabled=loaded && !busy,role=Role.Tab) {if(label=="More")screen="log"else{tab=label;if(label=="Today")date=LocalDate.now()}}.padding(vertical=12.dp),contentAlignment=Alignment.Center){Text(label,color=if(tab==label)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge)} }
                }
            }
        }) { padding->
            if(!loaded)Box(Modifier.fillMaxSize().padding(padding).padding(24.dp),contentAlignment=Alignment.Center){if(loadError!=null)Caption(loadError!!)else CircularProgressIndicator()}
            else Column(Modifier.padding(padding).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start=22.dp,end=16.dp,top=14.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){LeafMark();Text("Newleaf",style=MaterialTheme.typography.titleLarge)}
                    TextButton(onClick={editorError=null;tab="Today";date=LocalDate.now();screen="scan"},enabled=!busy,modifier=Modifier.semantics{contentDescription="Scan barcode"}){Icon(Icons.Outlined.QrCodeScanner,contentDescription=null);Spacer(Modifier.width(6.dp));Text("Scan")}
                    TextButton(onClick={screen="settings"}){Icon(Icons.Outlined.Settings,contentDescription="Settings");Spacer(Modifier.width(6.dp));Text("Settings")}
                }
                if(tab=="Trends")TrendsScreen(journal)
                else JournalScreen(journal,if(tab=="Today")LocalDate.now() else date,tab=="History",busy,
                    onDate={date=it;tab=if(it==LocalDate.now())"Today" else "History"},onWater=::quickWater,onEntry={detail=it;screen="detail"},
                    onSteps={open(EntryKind.STEPS)},onLog={open(it)},onExport={portabilityOrigin=null;screen="export"})
            }
        }
        if(loaded && !journal.settings.onboardingComplete)WelcomeSheet(journal.settings){settings->scope.launch{try{repository.saveSettings(settings.copy(onboardingComplete=true))}catch(e:Exception){notify(e.message ?: "Settings could not be saved")}}}
        when(screen) {
            "log"->ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={screen=null},containerColor=MaterialTheme.colorScheme.background){Column(Modifier.padding(horizontal=22.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Section("What would you like to log?")
                listOf(EntryKind.FOOD,EntryKind.DRINK,EntryKind.ACTIVITY).forEach{kind->Action(kindName(kind),Modifier.fillMaxWidth(),primary=true){open(kind)}}
                Disclosure("More ways to log") {
                    listOf(EntryKind.MEAL,EntryKind.WEIGHT,EntryKind.STEPS,EntryKind.CHECKIN,EntryKind.NOTE).forEach{kind->Action(kindName(kind),Modifier.fillMaxWidth()){open(kind)}}
                }
                val savedDrafts=journal.drafts.filterKeys{it=="editor" || it.startsWith("editor:")}
                if(savedDrafts.isNotEmpty())Disclosure("Saved drafts") {
                    savedDrafts.forEach { (key,saved)->
                        val restored=runCatching{gson.fromJson(saved,EditorDraft::class.java)}.getOrNull()
                        if(restored!=null)Action("Continue ${restored.name.ifBlank{kindName(restored.kind).lowercase()}} draft",Modifier.fillMaxWidth()){draftKey=key;draft=restored;screen="editor"}
                    }
                    TextButton(onClick={scope.launch{savedDrafts.keys.forEach{repository.clearDraft(it)};draft=null;notify("Drafts discarded")}}){Text("Discard saved drafts")}
                }
                Spacer(Modifier.height(24.dp))
            }}
            "scan"->QuickScanSheet(lookup={if(quickBarcodeLookup!=null)quickBarcodeLookup(it)else NutritionClient(journal.settings).barcode(it)},busy=busy,saveError=editorError,capture=quickBarcodeCapture,
                onSave=::saveScanned,onClose={screen=null;editorError=null},onManual={code->
                    open(EntryKind.FOOD)
                    draft=draft?.let{current->current.copy(base=code.takeIf{it.isNotBlank()}?.let{Entry(id=current.id,kind=EntryKind.FOOD,name="",sourceReference=SourceReference(provider="manual",barcode=it))})}
                })
            "editor"->draft?.let {current->EntryEditor(current,journal,busy,{draft=it;editorError=null},::save,{screen=null},editorError)}
            "detail"->detail?.let {entry->EntryDetail(entry,journal,onClose={screen=null;detail=null},onEdit={edit(it)},onDelete={deleteTarget=entry;deleteFoods=true;deleteDrinks=false;deleteCheckins=false},onCheckin={detail=null;open(EntryKind.CHECKIN,entry.id)},onRepeat={
                draftKey="editor:${UUID.randomUUID()}"
                val targetDate=if(tab=="Today")LocalDate.now()else date
                val components=if(entry.kind==EntryKind.TEMPLATE)entry.ingredientSnapshots else journal.entries.filter{it.mealId==entry.id && it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)}
                draft=EditorDraft.new(EntryKind.MEAL,targetDate,journal.settings).copy(name=entry.name,category=entry.category,notes=entry.notes,photos=entry.photoPaths,items=components.map{it.copy(id=UUID.randomUUID().toString(),mealId=null,isLibrary=false)},originalText=entry.originalText);detail=null;screen="editor"
            },onFavorite={scope.launch{try{repository.upsert(entry.copy(isFavorite=!entry.isFavorite));detail=entry.copy(isFavorite=!entry.isFavorite)}catch(e:Exception){notify(e.message ?: "Favorite could not be saved")}}},onTemplate={
                draftKey="editor:${UUID.randomUUID()}"
                draft=EditorDraft.new(EntryKind.TEMPLATE,date,journal.settings).copy(name=entry.name,notes=entry.notes,items=journal.entries.filter{it.mealId==entry.id && it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)}.map{it.copy(id=UUID.randomUUID().toString(),mealId=null,isLibrary=true)});detail=null;screen="editor"
            })}
            "library"->LibrarySheet(journal,onClose={screen=null},onEdit=::edit,onDelete={deleteTarget=it},onNew={open(it)},onSelect={entry->
                draftKey="editor:${UUID.randomUUID()}"
                when(entry.kind){EntryKind.TEMPLATE->{draft=EditorDraft.new(EntryKind.MEAL,date,journal.settings).copy(name=entry.name,notes=entry.notes,items=entry.ingredientSnapshots.map{it.copy(id=UUID.randomUUID().toString(),mealId=null,isLibrary=false)});screen="editor"}
                EntryKind.RECIPE->{detail=entry;screen="recipePortion"}
                else->{val fresh=EventTimes.at(entry.copy(id=UUID.randomUUID().toString(),isLibrary=false,mealId=null,version=1,createdAt=Instant.now().toString()),date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant());draft=EditorDraft.edit(fresh,journal.entries,journal.settings).copy(isNew=true);screen="editor"}
                }
            })
            "recipePortion"->detail?.let {recipe->RecipePortionSheet(recipe,date,journal.settings,onClose={screen="library"}){entry->draftKey="editor:${UUID.randomUUID()}";draft=EditorDraft.edit(entry,journal.entries,journal.settings).copy(isNew=true);screen="editor"}}
            "settings"->SettingsSheet(journal.settings,onClose={screen=null},onSave={settings->scope.launch{try{repository.saveSettings(settings);screen=null;notify("Preferences saved")}catch(e:Exception){notify(e.message ?: "Preferences could not be saved")}}},onExport={portabilityOrigin="settings";screen="export"},onBackup={portabilityOrigin="settings";screen="backup"},onRestore={portabilityOrigin="settings";screen="restore"},onLibrary={screen="library"})
            "export","backup","restore"->PortabilitySheet(journal,repository,screen ?: "export",date,onClose={screen=portabilityOrigin},onMessage=::notify)
        }
        deleteTarget?.let {target->AlertDialog(onDismissRequest={deleteTarget=null},title={Text("Delete ${target.name}?")},text={Column{
            Text("This removes the record from your journal. Undo will be available.")
            if(journal.entries.any{it.mealId==target.id}) {
                Row{Checkbox(deleteFoods,{deleteFoods=it});Text("Delete linked food items",Modifier.padding(top=12.dp))}
                Row{Checkbox(deleteDrinks,{deleteDrinks=it});Text("Delete linked drinks",Modifier.padding(top=12.dp))}
                Row{Checkbox(deleteCheckins,{deleteCheckins=it});Text("Delete linked check-ins",Modifier.padding(top=12.dp))}
                Caption("Unchecked categories are kept as standalone entries.")
            }
        }},confirmButton={TextButton(onClick={scope.launch{try{val deleting=setOf(target.id)+journal.entries.filter{it.mealId==target.id && ((it.kind==EntryKind.FOOD && deleteFoods)||(it.kind==EntryKind.DRINK && deleteDrinks)||(it.kind==EntryKind.CHECKIN && deleteCheckins))}.map{it.id};val before=repository.deleteWithDependents(deleting,false);deleteTarget=null;detail=null;screen=null;if(snackbar.showSnackbar("Entry deleted",actionLabel="Undo")==SnackbarResult.ActionPerformed)repository.upsertAll(before)}catch(e:Exception){notify(e.message ?: "Delete failed")}}}){Text("Delete")}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("Keep entry")}})}
    }
}

@Composable
internal fun TimelineCard(entry:Entry,journal:Journal,onEntry:(Entry)->Unit) {
    val children=journal.entries.filter{it.mealId==entry.id && it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)}
    val calories=if(entry.kind==EntryKind.MEAL)Nutrition.aggregate(children,"energy_kcal").knownSum else entry.nutrients["energy_kcal"]
    Panel(Modifier.fillMaxWidth().clickable{onEntry(entry)}) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Caption(if(entry.dateOnly)"Daily total" else entryTime(entry));Caption(entry.category.ifBlank{kindName(entry.kind)})}
        Text(entry.name,style=MaterialTheme.typography.titleLarge)
        val detail=when(entry.kind){EntryKind.MEAL->if(children.isEmpty())"No items recorded · nutrition unknown" else children.joinToString(" · "){it.name};EntryKind.DRINK->volumeLabel(entry.volumeMl ?: entry.quantity?.let{Units.volumeMl(it,entry.unit)},journal.settings);EntryKind.ACTIVITY->listOfNotNull(distanceLabel(entry.distanceMeters,journal.settings).takeIf{it.isNotBlank()},entry.durationSeconds?.let{"${number(it/60.0)} min"},entry.intensity.takeIf{it.isNotBlank()}).joinToString(" · ");EntryKind.WEIGHT->weightLabel(entry.weightKg,journal.settings);EntryKind.STEPS->"${entry.steps} steps";EntryKind.CHECKIN->entry.tags.joinToString(" · ").ifBlank{"Observation"};EntryKind.NOTE->entry.notes;else->entry.quantity?.let{"${number(it)} ${entry.unit}"}.orEmpty()}
        if(detail.isNotBlank())Caption(detail)
        if(entry.kind in listOf(EntryKind.MEAL,EntryKind.FOOD,EntryKind.DRINK))Caption(calories?.let{"${number(it)} kcal"} ?: "Calories unknown")
        if(entry.kind==EntryKind.CHECKIN)entry.mealId?.let{id->journal.entries.find{it.id==id}?.let{meal->Caption("${number(EventTimes.elapsedSeconds(entry,meal)/60.0)} min after ${meal.name}")}}
        if(entry.kind==EntryKind.DRINK && entry.mealId!=null)Caption("With ${journal.entries.find{it.id==entry.mealId}?.name ?: "linked meal"}")
    }
}
