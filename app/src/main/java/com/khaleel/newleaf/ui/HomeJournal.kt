package com.khaleel.newleaf.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khaleel.newleaf.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Each daily tracker owns its next action; no entry-kind menu is needed first. */
@Composable
internal fun JournalScreen(journal:Journal,date:LocalDate,history:Boolean,busy:Boolean,onDate:(LocalDate)->Unit,onWater:(Int)->Unit,onEntry:(Entry)->Unit,onSteps:()->Unit,onLog:(EntryKind)->Unit,onExport:()->Unit) {
    val context=LocalContext.current
    var query by remember { mutableStateOf("") };var filter by remember { mutableStateOf("All") }
    val dayEntries=journal.entries.filter{isJournal(it) && it.localDate==date.toString()}
    val summary=Summaries.day(journal.entries,date.toString());val calories=summary.nutrients["energy_kcal"]
    val rows=dayEntries.filter{!(it.kind==EntryKind.FOOD && it.mealId!=null && filter!="Food" && query.isBlank())}
        .filter{query.isBlank() || it.name.contains(query,true) || it.notes.contains(query,true)}
        .filter{filter=="All" || kindName(it.kind)==filter}.sortedBy{it.occurredAt}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text(if(history)"Your journal"else"Today",style=MaterialTheme.typography.headlineLarge);Caption(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")))}
            TextButton(onClick={DatePickerDialog(context,{_,year,month,day->onDate(LocalDate.of(year,month+1,day))},date.year,date.monthValue-1,date.dayOfMonth).show()}) {Text("Calendar")}
        }
        val monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            (0L..6L).forEach { offset->val day=monday.plusDays(offset);val active=day==date
                Column(Modifier.widthIn(min=48.dp).heightIn(min=64.dp)
                    .semantics{selected=active;contentDescription=day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))}
                    .background(if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,RoundedCornerShape(16.dp))
                    .clickable(role=Role.Button){onDate(day)}.padding(horizontal=8.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEE")),maxLines=1,softWrap=false,style=MaterialTheme.typography.bodyMedium,color=if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(day.dayOfMonth.toString(),style=MaterialTheme.typography.titleMedium,color=if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if(history) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("← Previous",Modifier.weight(1f)){onDate(date.minusDays(1))};Action("Next →",Modifier.weight(1f)){onDate(date.plusDays(1))}}
            Disclosure("Search & export") {
                Field("Search names and notes",query,{query=it})
                SelectField("Show",filter,listOf("All","Meal","Food","Drink","Activity","Weight","Steps","Check-in","Daily note")){filter=it}
                Action("Export journal",onClick=onExport)
            }
        }
        TrackerCard("Food",Icons.Outlined.Restaurant) {
            Caption(calories?.knownSum?.let{"${number(it)} kcal today"} ?: if(dayEntries.none{it.kind in listOf(EntryKind.FOOD,EntryKind.MEAL)})"What did you eat?"else"Food logged · calories unknown")
            if(calories?.knownSum!=null && calories.completeness==Completeness.PARTIAL)Caption("Some entries have no calorie information.")
            Action("Log food",Modifier.fillMaxWidth(),primary=true,enabled=!busy){onLog(EntryKind.FOOD)}
        }
        TrackerCard("Drinks",Icons.Outlined.WaterDrop) {
            Caption(summary.waterMl?.let{"${volumeLabel(it,journal.settings)} water"} ?: "No water logged yet")
            journal.settings.waterGoalMl?.let { goal->
                Caption("Water goal: ${volumeLabel(goal,journal.settings)}")
                LinearProgressIndicator(progress={(summary.waterMl ?: 0.0).div(goal).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Action("Log drink",Modifier.weight(1f),enabled=!busy){onLog(EntryKind.DRINK)}
                Action(if(journal.settings.volumeUnit=="ml")"+ ${number(16*29.5735295625)} mL water"else"+ 16 oz water",Modifier.weight(1f),enabled=!busy){onWater(16)}
            }
        }
        TrackerCard("Movement",Icons.Outlined.DirectionsWalk) {
            Caption(summary.steps?.let{"$it steps"} ?: "Steps not logged yet")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Action("Log activity",Modifier.weight(1f),enabled=!busy){onLog(EntryKind.ACTIVITY)}
                Action("Add steps",Modifier.weight(1f),enabled=!busy,onClick=onSteps)
            }
        }
        Section("Your day")
        if(rows.isEmpty())Caption(if(query.isNotBlank() || filter!="All")"No records match this view."else"Your entries will appear here.")
        rows.forEach {entry->TimelineCard(entry,journal,onEntry)}
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun TrackerCard(title:String,icon:ImageVector,content:@Composable ColumnScope.()->Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(icon,contentDescription=null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
            Text(title,style=MaterialTheme.typography.titleLarge)
        }
        content()
    }
}
