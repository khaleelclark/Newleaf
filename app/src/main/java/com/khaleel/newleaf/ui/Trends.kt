package com.khaleel.newleaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khaleel.newleaf.data.*
import java.time.LocalDate

@Composable
internal fun TrendsScreen(journal:Journal) {
    var range by remember{mutableStateOf("7 days")};var metric by remember{mutableStateOf("Calories")}
    var from by remember{mutableStateOf(LocalDate.now().minusDays(6).toString())};var to by remember{mutableStateOf(LocalDate.now().toString())}
    val days=runCatching{val end=if(range=="Custom")LocalDate.parse(to)else LocalDate.now();val start=if(range=="Custom")LocalDate.parse(from)else end.minusDays(if(range=="30 days")29 else 6);require(!start.isAfter(end) && java.time.temporal.ChronoUnit.DAYS.between(start,end)<=730){"Choose a range of up to two years"};Summaries.range(journal.entries,start.toString(),end.toString())}.getOrDefault(emptyList())
    val values=days.map{day->when(metric){"Calories"->day.nutrients["energy_kcal"]?.knownSum;"Protein"->day.nutrients["protein_g"]?.knownSum;"Carbs"->day.nutrients["carbohydrate_g"]?.knownSum;"Fat"->day.nutrients["fat_g"]?.knownSum;"Water"->day.waterMl?.let{Units.displayVolume(it,journal.settings.volumeUnit)};"All drinks"->day.drinkVolumeMl?.let{Units.displayVolume(it,journal.settings.volumeUnit)};"Steps"->day.steps?.toDouble();"Distance"->day.activityDistanceMeters?.let{Units.displayDistance(it,journal.settings.distanceUnit)};"Activity minutes"->day.activityDurationSeconds?.div(60.0);"Weight"->day.weightKg?.let{Units.displayWeight(it,journal.settings.weightUnit)};else->null}}
    val unit=when(metric){"Calories"->"kcal";"Water","All drinks"->if(journal.settings.volumeUnit.equals("ml",true))"mL" else "US fl oz";"Weight"->journal.settings.weightUnit;"Distance"->journal.settings.distanceUnit;"Protein","Carbs","Fat"->"g";"Activity minutes"->"min";else->"steps"}
    val known=values.filterNotNull();val partial=days.count{it.nutrients[when(metric){"Protein"->"protein_g";"Carbs"->"carbohydrate_g";"Fat"->"fat_g";else->"energy_kcal"}]?.completeness==Completeness.PARTIAL}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Section("Small patterns", "A view of what you've recorded, with the gaps left visible.")
        Choices(listOf("7 days","30 days","Custom"),range){range=it}
        if(range=="Custom"){Field("From · YYYY-MM-DD",from,{from=it});Field("Through · YYYY-MM-DD",to,{to=it});if(days.isEmpty())Caption("Enter a valid inclusive date range, up to two years.")}
        Choices(listOf("Calories","Protein","Carbs","Fat","Water","All drinks","Steps","Distance","Activity minutes","Weight","Feelings"),metric){metric=it}
        if(metric=="Feelings") {
            val observations=journal.entries.filter{it.kind==EntryKind.CHECKIN && days.any{d->d.date==it.localDate}}
            val counts=observations.flatMap{it.tags}.groupingBy{it}.eachCount().toList().sortedByDescending{it.second}
            Panel {Section("Feeling observations", "${observations.size} check-ins · ${observations.map{it.localDate}.distinct().size} observed days");if(counts.isEmpty())Caption("No tagged observations in this range.");counts.forEach{(tag,count)->Caption("$tag · $count observations")};Caption("These are personal observations; this view does not imply a food caused a feeling.")}
            observations.takeLast(20).forEach{checkin->Panel{Caption("${checkin.localDate} · ${entryTime(checkin)}");Text(checkin.tags.joinToString(" · ").ifBlank{checkin.name});checkin.mealId?.let{id->journal.entries.find{it.id==id}?.let{meal->Caption("${meal.name} · ${number(EventTimes.elapsedSeconds(checkin,meal)/60.0)} min later")}}}}
        } else {
            Panel {Caption(if(metric=="Weight")"MEAN OF OBSERVED DAILY MEANS" else "MEAN OF OBSERVED DAYS");Text(if(known.isEmpty())"—" else "${number(known.average())} $unit",style=MaterialTheme.typography.headlineMedium);Caption("${known.size}/${days.size} days observed · ${days.size-known.size} missing");if(metric in listOf("Calories","Protein","Carbs","Fat"))Caption("$partial days have partial nutrition. Totals include known values only.");TrendBars(values,metric,unit);Caption("Bars show daily ${if(metric=="Weight")"mean measurements" else "recorded totals"}. Gaps are unknown. Explicit zero has a baseline marker.")}
            if(metric=="Weight"){val rolling=Summaries.weightTrend(journal.entries,days.lastOrNull()?.date ?: LocalDate.now().toString());Panel{Section("Seven-day moving average");Caption(if(rolling.meanKg==null)"Needs at least 3 measured days · ${rolling.measuredDays}/7 available" else "${weightLabel(rolling.meanKg,journal.settings)} · ${rolling.measuredDays}/7 measured days")}}
            Section("Daily values", "Accessible table of the same chart data.")
            days.forEachIndexed{index,day->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Caption(day.date);Caption(values[index]?.let{"${number(it)} $unit${if(metric=="Weight")" · ${day.weightMeasurements} measurements" else ""}"} ?: "Not logged")}}
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TrendBars(values:List<Double?>,label:String,unit:String) {
    val color=MaterialTheme.colorScheme.primary;val line=MaterialTheme.colorScheme.outline.copy(alpha=.3f)
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics{contentDescription="$label chart in $unit. ${values.count{it!=null}} observed days. Daily values appear below."}) {
        drawLine(line,Offset(0f,size.height-3),Offset(size.width,size.height-3),1.dp.toPx())
        val max=(values.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0);val cell=size.width/(values.size.coerceAtLeast(1));val bar=(cell*.65f).coerceAtLeast(1f)
        values.forEachIndexed{index,v->if(v!=null){val h=((v/max)*(size.height-10)).toFloat().coerceAtLeast(3f);drawRoundRect(color,Offset(index*cell+(cell-bar)/2,size.height-h-3),Size(bar,h),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3f,3f))}}
    }
}
