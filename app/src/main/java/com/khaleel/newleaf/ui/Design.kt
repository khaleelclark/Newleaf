package com.khaleel.newleaf.ui

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun NewleafTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme.equals("dark",true) || (theme.equals("system",true) && isSystemInDarkTheme())
    val view=LocalView.current
    SideEffect { (view.context as? Activity)?.window?.let { window->WindowCompat.getInsetsController(window,view).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark } } }
    val colors = if (dark) darkColorScheme(primary=Color(0xFF9CCCAF), onPrimary=Color(0xFF183524), background=Color(0xFF171C19), surface=Color(0xFF222A25), onSurface=Color(0xFFF2F5F0), onBackground=Color(0xFFF2F5F0), secondary=Color(0xFFD1A38C), surfaceVariant=Color(0xFF303B33), onSurfaceVariant=Color(0xFFB9C5BC), outline=Color(0xFF748A7B))
    else lightColorScheme(primary=Color(0xFF356449), onPrimary=Color.White, background=Color(0xFFF7F5EF), surface=Color(0xFFFFFFFF), onSurface=Color(0xFF202B24), onBackground=Color(0xFF202B24), secondary=Color(0xFF986449), surfaceVariant=Color(0xFFE8EEE5), onSurfaceVariant=Color(0xFF536157), outline=Color(0xFF859287))
    MaterialTheme(colorScheme=colors, typography=Typography(
        headlineLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=39.sp),
        headlineMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=28.sp,lineHeight=34.sp),
        titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=23.sp,lineHeight=29.sp),
        titleMedium=androidx.compose.ui.text.TextStyle(fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp),
        bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=23.sp),
        bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=21.sp),
        labelLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    ), content=content)
}

@Composable
fun LeafMark(modifier: Modifier = Modifier) {
    val color=MaterialTheme.colorScheme.primary
    Canvas(modifier.size(32.dp).semantics { contentDescription="Newleaf" }) {
        val p=Path().apply { moveTo(size.width*.18f,size.height*.83f); cubicTo(size.width*.04f,size.height*.25f,size.width*.55f,size.height*.04f,size.width*.87f,size.height*.1f); cubicTo(size.width*.88f,size.height*.62f,size.width*.61f,size.height*.87f,size.width*.18f,size.height*.83f) }
        drawPath(p,color,style=Stroke(2.dp.toPx())); drawLine(color,androidx.compose.ui.geometry.Offset(size.width*.14f,size.height*.91f),androidx.compose.ui.geometry.Offset(size.width*.68f,size.height*.29f),2.dp.toPx())
    }
}

@Composable
fun Panel(modifier: Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(22.dp)).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.16f),RoundedCornerShape(22.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

@Composable
fun Action(label: String, modifier: Modifier=Modifier, primary: Boolean=false, enabled: Boolean=true, onClick: () -> Unit) {
    val colors=MaterialTheme.colorScheme
    Box(modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{role=Role.Button}.background(if(primary) colors.primary.copy(alpha=if(enabled)1f else .35f) else colors.surfaceVariant.copy(alpha=.6f),RoundedCornerShape(14.dp)).clickable(enabled=enabled,onClick=onClick).padding(horizontal=16.dp,vertical=13.dp),contentAlignment=Alignment.Center) {
        Text(label,color=if(primary)colors.onPrimary else colors.onSurface,style=MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val c=MaterialTheme.colorScheme
    Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{this.selected=selected;role=Role.Button}.background(if(selected)c.primary else c.surfaceVariant.copy(alpha=.55f),RoundedCornerShape(13.dp)).clickable(onClick=onClick).padding(horizontal=14.dp,vertical=13.dp)) {
        Text(label,color=if(selected)c.onPrimary else c.onSurface,style=MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Choices(labels: List<String>, selected: String, onSelect: (String)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { labels.forEach { Choice(it,it.equals(selected,true)){onSelect(it)} } }
}

@Composable
fun Field(label: String, value: String, onChange:(String)->Unit, modifier:Modifier=Modifier, numeric:Boolean=false, lines:Int=1) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),singleLine=lines==1,minLines=lines,keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
fun Caption(text:String,modifier:Modifier=Modifier) { Text(text,modifier,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium) }

@Composable
fun Section(title:String,detail:String?=null) { Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(title,style=MaterialTheme.typography.titleLarge); detail?.let { Caption(it) } } }
