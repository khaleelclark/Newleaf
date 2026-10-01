package com.khaleel.newleaf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khaleel.newleaf.data.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryDetail(entry:Entry,journal:Journal,onClose:()->Unit,onEdit:(Entry)->Unit,onDelete:()->Unit,onCheckin:()->Unit,onRepeat:()->Unit,onFavorite:()->Unit,onTemplate:()->Unit) {
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight(.94f)) {
        Column(Modifier.padding(horizontal=22.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Caption("${kindName(entry.kind).uppercase()} · ${entry.localDate} · ${entryTime(entry)}")
            Text(entry.name,style=MaterialTheme.typography.headlineLarge)
            if(entry.category.isNotBlank())Caption(entry.category)
            if(entry.notes.isNotBlank())Panel{Text(entry.notes)}
            if(entry.kind==EntryKind.MEAL){
                val components=journal.entries.filter{it.mealId==entry.id && it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)}
                Section("Meal items", "Foods and drinks are counted once in the day's totals.")
                components.forEach { item->Panel {Text(item.name,style=MaterialTheme.typography.titleMedium);Caption("${number(item.quantity)} ${item.unit} · ${item.nutrients["energy_kcal"]?.let{"${number(it)} kcal"} ?: "Calories unknown"}");Action("Edit item"){onEdit(item)}} }
                Action("How did you feel?",Modifier.fillMaxWidth(),primary=true,onClick=onCheckin)
                val checkins=journal.entries.filter{it.mealId==entry.id && it.kind==EntryKind.CHECKIN}.sortedBy{it.occurredAt}
                checkins.forEach { checkin->Panel {Text(checkin.tags.joinToString(" · ").ifBlank{checkin.name});Caption("${checkin.localDate} ${entryTime(checkin)} · ${number(EventTimes.elapsedSeconds(checkin,entry)/60.0)} min after meal");if(checkin.notes.isNotBlank())Caption(checkin.notes);Action("Edit check-in"){onEdit(checkin)}} }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Repeat meal",Modifier.weight(1f),onClick=onRepeat);Action("Save template",Modifier.weight(1f),onClick=onTemplate)}
            }
            if(entry.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)) {
                Panel {Section("Portion & nutrition");Caption("${number(entry.quantity)} ${entry.unit} · ${entry.portionEvidence}");nutrientLabels.forEach{(key,label)->Caption("$label: ${entry.nutrients[key]?.let{number(it)} ?: "Unknown"}")};Caption("Source: ${entry.source}${entry.sourceId?.let{" · $it"} ?: ""}");entry.sourceReference?.let{source->if(source.attribution.isNotBlank())Caption(source.attribution);if(source.license.isNotBlank())Caption(source.license);if(!source.exportAllowed || !source.snapshotAllowed)Caption("Storage/export permission pending; full exports containing this source are blocked.")};Caption("Match: ${entry.matchStatus}");entry.nutrientEvidence["energy_kcal"]?.let{Caption("${it.status} · ${it.basis}")}}
                Action(if(entry.isFavorite)"Remove favorite" else "Save as favorite",Modifier.fillMaxWidth(),onClick=onFavorite)
            }
            if(entry.kind==EntryKind.WEIGHT)Caption(weightLabel(entry.weightKg,journal.settings))
            if(entry.kind==EntryKind.ACTIVITY)Caption("${distanceLabel(entry.distanceMeters,journal.settings)} · ${entry.durationSeconds?.let{"${number(it/60.0)} min"} ?: "Duration unknown"}")
            if(entry.kind==EntryKind.STEPS)Caption("${entry.steps} steps · manual total")
            if(entry.kind==EntryKind.CHECKIN){Caption(entry.tags.joinToString(" · "));entry.ratings.forEach{(name,value)->Caption("$name: $value / 5")};entry.mealId?.let{id->journal.entries.find{it.id==id}?.let{meal->Caption("Linked to ${meal.name} · ${number(EventTimes.elapsedSeconds(entry,meal)/60.0)} min after meal")}}}
            if(entry.originalText.isNotBlank())Panel{Section("Original description");Text(entry.originalText)}
            if(entry.photoPaths.isNotEmpty()){Section("Attached photos");PhotoGallery(entry.photoPaths)}
            Caption("Time zone: ${entry.zoneId}\nEntered: ${entry.createdAt}\nRevision: ${entry.version}")
            Action("Edit entry",Modifier.fillMaxWidth(),primary=true){onEdit(entry)}
            Action("Delete entry",Modifier.fillMaxWidth(),onClick=onDelete)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySheet(journal:Journal,onClose:()->Unit,onEdit:(Entry)->Unit,onDelete:(Entry)->Unit,onNew:(EntryKind)->Unit,onSelect:(Entry)->Unit) {
    var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("Favorites")}
    val entries=journal.entries.filter { entry->when(filter){"Favorites"->entry.isFavorite;"Recipes"->entry.kind==EntryKind.RECIPE;"Saved meals"->entry.kind==EntryKind.TEMPLATE;else->entry.kind in listOf(EntryKind.FOOD,EntryKind.DRINK)} }.filter{query.isBlank() || it.name.contains(query,true)}.sortedByDescending{it.updatedAt}.distinctBy{if(it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK))"${it.name}:${it.quantity}:${it.unit}" else it.id}
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxHeight(.94f)) {
        Column(Modifier.padding(horizontal=22.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Section("Your library", "Familiar foods, meals, and recipes. Yours to reuse.")
            Choices(listOf("Favorites","Recent","Saved meals","Recipes"),filter){filter=it};Field("Find in your library",query,{query=it})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("New recipe",Modifier.weight(1f)){onNew(EntryKind.RECIPE)};Action("New saved meal",Modifier.weight(1f)){onNew(EntryKind.TEMPLATE)}}
            if(entries.isEmpty())Panel{Text("Make it your own",style=MaterialTheme.typography.titleLarge);Caption("Favorite a logged food or drink, or save a meal you’ll want again.")}
            entries.forEach{entry->Panel{Text(entry.name,style=MaterialTheme.typography.titleLarge);Caption(when(entry.kind){EntryKind.RECIPE->"${number(entry.yieldServings)} servings · version ${entry.version}";EntryKind.TEMPLATE->"${entry.ingredientSnapshots.size} saved items · version ${entry.version}";else->"${number(entry.quantity)} ${entry.unit} · ${entry.source}"});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Review & log",Modifier.weight(1f),primary=true){onSelect(entry)};Action("Edit",Modifier.weight(1f)){onEdit(entry)}};if(entry.isLibrary)TextButton(onClick={onDelete(entry)}){Text("Delete from library")}}}
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipePortionSheet(recipe:Entry,date:LocalDate,settings:AppSettings,onClose:()->Unit,onConfirm:(Entry)->Unit) {
    var quantity by remember{mutableStateOf("1")};var unit by remember{mutableStateOf("serving")};var error by remember{mutableStateOf<String?>(null)}
    val entry=runCatching{Recipes.log(recipe,quantity.toDouble(),unit,date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant())}.getOrNull()
    ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background){Column(Modifier.padding(22.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Section(recipe.name,"Recipe version ${recipe.version} · ${number(recipe.yieldServings)} servings")
        Field("Consumed quantity",quantity,{quantity=it},numeric=true);Choices(listOf("serving","g","oz"),unit){unit=it}
        if(unit!="serving" && recipe.yieldGrams==null)Caption("No final cooked yield is available. Nutrition for gram/ounce portions remains unknown.")
        Caption(entry?.nutrients?.get("energy_kcal")?.let{"${number(it)} known kcal"} ?: "Calories unknown")
        Caption("Logging keeps this recipe's ingredient and nutrient snapshot. Future recipe changes won't alter it.")
        error?.let{Caption(it)};Action("Review entry",Modifier.fillMaxWidth(),primary=true){if(entry!=null)onConfirm(entry)else error="Enter a positive portion"};Spacer(Modifier.height(24.dp))
    }}
}
