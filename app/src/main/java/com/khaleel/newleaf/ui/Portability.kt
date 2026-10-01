package com.khaleel.newleaf.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khaleel.newleaf.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortabilitySheet(journal:Journal,repository:JournalRepository,mode:String,date:LocalDate,onClose:()->Unit,onMessage:(String)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var range by remember{mutableStateOf("All dates")};var from by remember{mutableStateOf(date.toString())};var to by remember{mutableStateOf(date.toString())}
    var format by remember{mutableStateOf("JSON")};var notes by remember{mutableStateOf(true)};var photos by remember{mutableStateOf(false)};var library by remember{mutableStateOf(false)};var recipes by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var prepared by remember{mutableStateOf<ByteArray?>(null)};var preparedName by remember{mutableStateOf("")};var mime by remember{mutableStateOf("application/json")}
    var preview by remember{mutableStateOf<RestorePreview?>(null)};var restoreMode by remember{mutableStateOf("Merge")};var resolutions by remember{mutableStateOf<Map<String,ConflictResolution>>(emptyMap())}
    val conflicts=remember(preview,journal){preview?.let { repository.restoreConflicts(it.journal) }.orEmpty()}
    val saver=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result->
        val uri=result.data?.data;val bytes=prepared
        if(result.resultCode==Activity.RESULT_OK && uri!=null && bytes!=null)scope.launch{busy=true;try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(bytes)} ?: throw IllegalStateException("Destination cannot be opened")};onMessage("${if(mode=="backup")"Backup" else "Export"} saved");onClose()}catch(e:Exception){error=e.message ?: "File could not be written"}finally{busy=false}}
        else onMessage("Save canceled")
    }
    val opener=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->uri?.let{scope.launch{busy=true;error=null;try{preview=withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(it)?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count:Int;var total=0L;while(input.read(buffer).also{count=it}!=-1){total+=count;require(total<=256L*1024*1024){"Backup exceeds the supported size"};out.write(buffer,0,count)};out.toByteArray()} ?: throw IllegalStateException("File cannot be opened");JournalExports.previewRestore(bytes)};resolutions=emptyMap()}catch(e:Exception){error=e.message ?: "This file could not be validated"}finally{busy=false}}} }
    fun prepare(share:Boolean) {
        if(busy)return;busy=true;error=null
        scope.launch{try{
            val opts=ExportOptions(if(range=="All dates")null else from,if(range=="All dates")null else if(range=="One day")from else to,notes,photos,library,recipes)
            val result=withContext(Dispatchers.IO){val reader=PhotoReader{path->File(path).readBytes()};when{mode=="backup"->Triple(JournalExports.backup(journal,reader),"newleaf-backup-${LocalDate.now()}.zip","application/zip");format=="CSV bundle" || photos->Triple(JournalExports.csvZip(journal,opts,reader),"newleaf-journal-${LocalDate.now()}.zip","application/zip");format=="Markdown"->Triple(JournalExports.markdown(journal,opts).toByteArray(Charsets.UTF_8),"newleaf-journal-${LocalDate.now()}.md","text/markdown");else->Triple(JournalExports.json(journal,opts).toByteArray(Charsets.UTF_8),"newleaf-journal-${LocalDate.now()}.json","application/json")}}
            prepared=result.first;preparedName=result.second;mime=result.third
            if(share){val file=withContext(Dispatchers.IO){File(context.cacheDir,"exports/$preparedName").also{it.parentFile?.mkdirs();it.writeBytes(result.first)}};val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share Newleaf export"));onMessage("Share sheet opened")}
            else saver.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=mime;putExtra(Intent.EXTRA_TITLE,preparedName)})
        }catch(e:Exception){error=e.message ?: "Export could not be prepared"}finally{busy=false}}
    }
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!busy)onClose()},containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight(.94f)){
        Column(Modifier.padding(horizontal=22.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(15.dp)){
            Section(when(mode){"backup"->"Keep a full copy";"restore"->"Restore your journal";else->"Take your journal with you"})
            if(mode=="restore") {
                Caption("Choose a Newleaf ZIP backup or structured JSON export. Files are validated before any records change.")
                Action(if(busy)"Reading…" else "Choose backup / JSON",Modifier.fillMaxWidth(),enabled=!busy){opener.launch(arrayOf("application/zip","application/json","application/octet-stream"))}
                preview?.let{p->
                    Panel{Section("File review");Caption("Schema ${JournalExports.SCHEMA_VERSION} · ${if(p.isFullBackup)"full backup" else "journal export"}");Caption("${p.journal.entries.size} records · ${p.attachments.size} photos\n${p.selectedCount} selected · ${p.contextCount} contextual\n${p.requiredStorageBytes/1024} KB storage required");p.warnings.forEach{Caption(it)}}
                    Choices(listOf("Merge","Replace"),restoreMode){restoreMode=it}
                    Caption(if(restoreMode=="Replace")"Replace installs this file's journal. A recovery backup of the current journal is created on this phone before replacing it. Current device credentials are retained." else "Merge keeps existing records and adds new ones with their original IDs. Decide how to handle each conflicting record.")
                    if(restoreMode=="Merge")conflicts.forEach{conflict->val incoming=p.journal.entries.first{it.id==conflict.id};Panel{Text(incoming.name,style=MaterialTheme.typography.titleMedium);Caption("On phone: revision ${conflict.existingVersion}\nIncoming: revision ${conflict.incomingVersion} · ${incoming.localDate}");Choices(listOf("Keep phone","Use incoming"),when(resolutions[conflict.id]){ConflictResolution.KEEP_EXISTING->"Keep phone";ConflictResolution.USE_INCOMING->"Use incoming";else->""}){resolutions=resolutions+(conflict.id to if(it=="Keep phone")ConflictResolution.KEEP_EXISTING else ConflictResolution.USE_INCOMING)}}}
                    Action(if(busy)"Restoring…" else "${restoreMode} journal",Modifier.fillMaxWidth(),primary=true,enabled=!busy && (restoreMode=="Replace" || conflicts.all{it.id in resolutions})){
                        scope.launch{busy=true;try{repository.restoreArchive(p,restoreMode=="Replace",resolutions);onMessage("Journal restored");onClose()}catch(e:Exception){error=e.message ?: "Restore failed; your current journal is intact"}finally{busy=false}}
                    }
                }
            } else {
                if(mode!="backup") {
                    Choices(listOf("All dates","One day","Date range"),range){range=it}
                    if(range!="All dates")Field("${if(range=="One day")"Date" else "From"} · YYYY-MM-DD",from,{from=it})
                    if(range=="Date range")Field("Through · YYYY-MM-DD",to,{to=it})
                    Choices(listOf("JSON","CSV bundle","Markdown"),format){format=it;if(it!="CSV bundle")photos=false}
                    Row{Checkbox(notes,{notes=it});Text("Include notes and original descriptions",Modifier.padding(top=12.dp))}
                    Row{Checkbox(library,{library=it});Text("Include library and favorites",Modifier.padding(top=12.dp))}
                    Row{Checkbox(recipes,{recipes=it});Text("Include all recipes",Modifier.padding(top=12.dp))}
                    Row{Checkbox(photos,{photos=it;if(it)format="CSV bundle"});Text("Include photos · ZIP bundle",Modifier.padding(top=12.dp))}
                }
                val selected=journal.entries.filter{entry->if(mode=="backup")true else if(entry.isLibrary)if(entry.kind==EntryKind.RECIPE)recipes else library else range=="All dates" || (entry.localDate>=from && entry.localDate<=if(range=="One day")from else to)}
                val included=selected.map{it.id}.toMutableSet()
                var changed:Boolean
                do {changed=false;included.toList().forEach{id->journal.entries.find{it.id==id}?.let{e->val links=e.componentIds+listOfNotNull(e.mealId)+if(e.kind==EntryKind.MEAL)journal.entries.filter{it.mealId==id}.map{it.id}else emptyList();links.filter{link->journal.entries.any{it.id==link}}.forEach{if(included.add(it))changed=true}}}}while(changed)
                val contextCount=included.size-selected.size
                val estimatedBytes=JournalCodec.gson.toJson(journal.entries.filter{it.id in included}).toByteArray().size.toLong()+if(mode=="backup"||photos)selected.flatMap{it.photoPaths}.distinct().sumOf{File(it).length()}else 0L
                Panel{Section("Review your file");Caption("${selected.size} selected records · ${selected.map{it.localDate}.distinct().size} dates");Caption("$contextCount contextual records · about ${estimatedBytes/1024+2} KB before ZIP compression");selected.groupingBy{kindName(it.kind)}.eachCount().forEach{(kind,count)->Caption("$kind · $count")};Caption("Linked context outside the date range is included and marked separately. Credentials and transient drafts are excluded.");if(mode=="backup")Caption("Full backup includes notes, library, recipes, and original photos.")else Caption("${if(notes)"Notes included" else "Notes excluded"} · ${if(photos)"Photos included" else "Photos excluded"}")}
                Caption("Saved files are readable by anyone who can access the destination. Nothing is sent to an AI automatically.")
                Action(if(busy)"Preparing…" else "Save ${if(mode=="backup")"full backup" else "export"}",Modifier.fillMaxWidth(),primary=true,enabled=!busy){prepare(false)}
                Action("Share using Android",Modifier.fillMaxWidth(),enabled=!busy){prepare(true)}
                prepared?.let{Caption("Prepared file: ${it.size/1024} KB")}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Caption(it)}
            Spacer(Modifier.height(24.dp))
        }
    }
}
