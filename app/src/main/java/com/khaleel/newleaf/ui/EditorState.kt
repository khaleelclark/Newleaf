package com.khaleel.newleaf.ui

import com.khaleel.newleaf.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

internal val nutrientLabels = linkedMapOf("energy_kcal" to "Calories · kcal", "protein_g" to "Protein · g", "carbohydrate_g" to "Carbohydrates · g", "fat_g" to "Fat · g", "fiber_g" to "Fiber · g", "sugar_g" to "Sugar · g", "sodium_mg" to "Sodium · mg")
internal fun number(value: Double?): String = value?.let { if(it==it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(java.util.Locale.US,it) } ?: ""
internal fun decimal(value: Double?): String = value?.toString()?.removeSuffix(".0") ?: ""
internal fun entryTime(entry: Entry): String = runCatching { Instant.parse(entry.occurredAt).atZone(ZoneId.of(entry.zoneId)).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault("")
internal fun isJournal(entry: Entry)=!entry.isLibrary && entry.kind !in listOf(EntryKind.RECIPE,EntryKind.TEMPLATE)
internal fun volumeLabel(ml:Double?, settings:AppSettings):String = if(ml==null)"Unknown" else if(settings.volumeUnit=="ml") "${number(ml)} mL" else "${number(ml/29.5735295625)} US fl oz"
internal fun weightLabel(kg:Double?, settings:AppSettings):String = if(kg==null)"Unknown" else "${number(if(settings.weightUnit=="lb")kg/0.45359237 else kg)} ${settings.weightUnit}"
internal fun distanceLabel(m:Double?, settings:AppSettings):String = if(m==null)"" else "${number(if(settings.distanceUnit=="mi")m/1609.344 else m/1000)} ${settings.distanceUnit}"

internal data class EditorDraft(
    val id:String=UUID.randomUUID().toString(), val base:Entry?=null, val isNew:Boolean=true,
    val kind:EntryKind=EntryKind.MEAL, val name:String="", val date:String=LocalDate.now().toString(),
    val time:String=LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), val zone:String=ZoneId.systemDefault().id,
    val offset:String="", val notes:String="", val quantity:String="", val unit:String="serving",
    val nutrients:Map<String,String> = emptyMap(), val overrides:List<String> = emptyList(), val evidence:String="unspecified", val items:List<Entry> = emptyList(),
    val mealId:String?=null, val tags:List<String> = emptyList(), val ratings:Map<String,Int> = emptyMap(),
    val distance:String="", val duration:String="", val steps:String="", val weight:String="",
    val category:String="", val intensity:String="", val servings:String="", val yieldGrams:String="",
    val photos:List<String> = emptyList(), val originalText:String="", val favorite:Boolean=false,
    val water:Boolean=false, val completeness:String="unmarked", val saveAs:String="journal",
    val lookupBarcode:String?=null
) {
    companion object {
        fun new(kind:EntryKind,date:LocalDate,settings:AppSettings,mealId:String?=null):EditorDraft = EditorDraft(kind=kind,date=date.toString(),mealId=mealId,
            name=when(kind){EntryKind.DRINK->"Water";EntryKind.ACTIVITY->"Walk";EntryKind.STEPS->"Daily steps";EntryKind.CHECKIN->"How I felt";EntryKind.NOTE->"Daily note";else->""},
            unit=when(kind){EntryKind.DRINK->settings.volumeUnit;EntryKind.WEIGHT->settings.weightUnit;else->"serving"},water=kind==EntryKind.DRINK)
        fun edit(entry:Entry,all:List<Entry>,settings:AppSettings):EditorDraft {
            val z=Instant.parse(entry.occurredAt).atZone(ZoneId.of(entry.zoneId))
            return EditorDraft(id=entry.id,base=entry,isNew=false,kind=entry.kind,name=entry.name,date=entry.localDate,time=z.toLocalTime().toString(),zone=entry.zoneId,offset=z.offset.toString(),notes=entry.notes,quantity=decimal(entry.quantity),unit=entry.unit,
                nutrients=baseDrinkNutrition(entry).mapValues { decimal(it.value) },evidence=entry.portionEvidence,
                items=if(entry.kind in listOf(EntryKind.RECIPE,EntryKind.TEMPLATE))entry.ingredientSnapshots else if(entry.kind==EntryKind.DRINK)entry.composition else all.filter { it.mealId==entry.id && it.kind in listOf(EntryKind.FOOD,EntryKind.DRINK) },mealId=entry.mealId,tags=entry.tags,ratings=entry.ratings,
                distance=decimal(entry.distanceMeters?.div(if(settings.distanceUnit=="mi")1609.344 else 1000.0)),duration=entry.durationSeconds?.let { decimal(it/60.0) } ?: "",steps=entry.steps?.toString() ?: "",weight=decimal(entry.weightKg?.div(if(entry.unit=="lb")0.45359237 else 1.0)),category=entry.category,intensity=entry.intensity,servings=decimal(entry.yieldServings),yieldGrams=decimal(entry.yieldGrams),photos=entry.photoPaths,originalText=entry.originalText,favorite=entry.isFavorite,water=entry.countsAsWater,completeness=entry.loggingCompleteness,saveAs=if(entry.isLibrary)"library" else "journal")
        }
    }
}

internal fun EditorDraft.eventInstant():Instant {
    if(kind in listOf(EntryKind.STEPS,EntryKind.NOTE))return LocalDate.parse(date).atStartOfDay(ZoneId.of(zone)).toInstant()
    val local=LocalDateTime.of(LocalDate.parse(date),LocalTime.parse(time))
    return EventTimes.resolve(local,ZoneId.of(zone),offset.takeIf { it.isNotBlank() }?.let { ZoneOffset.of(it) })
}

internal fun EditorDraft.toEntries(settings:AppSettings):List<Entry> {
    require(name.isNotBlank()) { "Add a name before saving" }
    fun amount(value:String,label:String,positive:Boolean=true):Double? {
        if(value.isBlank())return null
        val n=value.toDoubleOrNull();require(n!=null && n.isFinite() && if(positive)n>0 else n>=0) { "$label must be ${if(positive)"positive" else "zero or greater"}" }; return n
    }
    val at=eventInstant()
    val q=amount(quantity,"Portion")
    val nutrition=nutrients.mapValues { (key,value)-> amount(value,nutrientLabels[key] ?: key,false) }
    val canonicalVolume=if(kind==EntryKind.DRINK)q?.let{canonicalPortion(base,it,unit,true)} else null
    val parent=EventTimes.at((base ?: Entry(id=id,kind=kind,name=name.trim())).copy(name=name.trim(),notes=notes,kind=kind,mealId=mealId,quantity=if(kind==EntryKind.WEIGHT)amount(weight,"Weight")else q,unit=unit,nutrients=nutrition,portionEvidence=evidence,tags=tags,ratings=ratings,photoPaths=photos,originalText=originalText,isFavorite=favorite,category=category,intensity=intensity,
        nutrientEvidence=(base?.let(::baseDrinkEvidence) ?: emptyMap()) + nutrition.filter{it.key in overrides || base==null}.mapValues { (code,value)->NutrientEvidence(status=if(value==null)"unknown"else"known",origin=if(base?.source!="manual" && base!=null)"user_override"else"user_entered",unit=Units.nutrientUnit(code),basis="consumed") },
        sourceReference=base?.sourceReference?.let{it.copy(overrides=it.overrides+overrides.associateWith{key->nutrition[key]?.toString() ?: "unknown"})},
        volumeMl=canonicalVolume,countsAsWater=kind==EntryKind.DRINK && water,
        metadata=if(kind in listOf(EntryKind.FOOD,EntryKind.DRINK))rescaledMetadata(base,q,unit) else base?.metadata.orEmpty(),
        weightKg=if(kind==EntryKind.WEIGHT)amount(weight,"Weight")?.times(if(unit=="lb")0.45359237 else 1.0) else base?.weightKg,
        distanceMeters=if(kind==EntryKind.ACTIVITY)amount(distance,"Distance",false)?.times(if(settings.distanceUnit=="mi")1609.344 else 1000.0) else base?.distanceMeters,
        massGrams=if(kind==EntryKind.FOOD)q?.let{canonicalPortion(base,it,unit,false)}else base?.massGrams,
        endAt=if(kind==EntryKind.ACTIVITY)amount(duration,"Duration",false)?.let{at.plusSeconds((it*60).toLong()).toString()}else base?.endAt,
        durationSeconds=if(kind==EntryKind.ACTIVITY)amount(duration,"Duration",false)?.times(60)?.toLong() else base?.durationSeconds,
        steps=if(kind==EntryKind.STEPS){ require(steps.toLongOrNull()!=null && steps.toLong()>=0){"Enter a whole step total, including zero"};steps.toLong() }else base?.steps,
        yieldServings=amount(servings,"Serving yield"),yieldGrams=amount(yieldGrams,"Cooked weight"),loggingCompleteness=completeness,
        isLibrary=kind in listOf(EntryKind.RECIPE,EntryKind.TEMPLATE) || saveAs=="library",version=if(base!=null && !isNew)base.version+1 else 1),at,ZoneId.of(zone))
    require(kind!=EntryKind.WEIGHT || parent.weightKg!=null) { "Enter your weight" }
    if(kind in listOf(EntryKind.RECIPE,EntryKind.TEMPLATE)) {
        require(items.isNotEmpty()) { "Add at least one ingredient or item" }
        require(kind!=EntryKind.RECIPE || parent.yieldServings!=null) { "Add the recipe's serving yield" }
        val snapshots=items.map { it.copy(mealId=null,isLibrary=true) }
        val recipe=if(kind==EntryKind.RECIPE)Recipes.create(parent.name,snapshots,parent.yieldServings!!,parent.yieldGrams,id,parent.version)else parent
        return listOf(parent.copy(ingredientSnapshots=snapshots,componentIds=emptyList(),nutrients=recipe.nutrients,nutrientEvidence=recipe.nutrientEvidence))
    }
    if(kind==EntryKind.MEAL) {
        val children=items.map { EventTimes.at(it.copy(mealId=id,isLibrary=false),at,ZoneId.of(zone)) }
        return listOf(parent.copy(componentIds=children.map{it.id},nutrients=emptyMap()))+children
    }
    if(kind==EntryKind.DRINK && items.isNotEmpty()) {
        return listOf(Nutrition.composeDrink(parent,items))
    }
    return listOf(if(kind==EntryKind.DRINK)parent.copy(composition=emptyList())else parent)
}

internal fun baseDrinkNutrition(entry:Entry):Map<String,Double?> {
    if(entry.kind!=EntryKind.DRINK || entry.composition.isEmpty())return entry.nutrients
    return runCatching { com.google.gson.Gson().fromJson<Map<String,Double?>>(entry.metadata["drink_base_nutrients"],object:com.google.gson.reflect.TypeToken<Map<String,Double?>>(){}.type) }.getOrNull() ?: entry.nutrients
}

internal fun baseDrinkEvidence(entry:Entry):Map<String,NutrientEvidence> {
    if(entry.kind!=EntryKind.DRINK || entry.composition.isEmpty())return entry.nutrientEvidence
    return runCatching { JournalCodec.gson.fromJson<Map<String,NutrientEvidence>>(entry.metadata["drink_base_evidence"],object:com.google.gson.reflect.TypeToken<Map<String,NutrientEvidence>>(){}.type) }.getOrNull()
        ?: entry.nutrientEvidence.filterValues { it.origin!="composition" }
}


internal fun selectStandaloneItem(draft:EditorDraft,item:Entry,settings:AppSettings):EditorDraft {
    val source=item.copy(id=draft.id,mealId=draft.mealId,notes=draft.notes,photoPaths=draft.photos,isLibrary=draft.saveAs=="library",createdAt=draft.base?.createdAt ?: item.createdAt,version=draft.base?.version ?: item.version)
    return EditorDraft.edit(source,emptyList(),settings).copy(id=draft.id,isNew=draft.isNew,date=draft.date,time=draft.time,zone=draft.zone,offset=draft.offset,
        favorite=draft.favorite,saveAs=draft.saveAs,lookupBarcode=draft.lookupBarcode,originalText=draft.originalText.ifBlank{item.originalText},base=source)
}

internal fun canonicalPortion(source:Entry?,quantity:Double,unit:String,volume:Boolean):Double? {
    val direct=if(volume)Units.volumeMl(quantity,unit)else Units.massGrams(quantity,unit)
    if(direct!=null)return direct
    val definition=source?.portionDefinitions?.firstOrNull{it.label.equals(unit,true)}
    val defined=if(volume)definition?.volumeMl else definition?.massGrams
    if(definition!=null && defined!=null && definition.quantity>0)return defined*quantity/definition.quantity
    val sourceAmount=source?.quantity
    val sourceCanonical=if(volume)source?.volumeMl else source?.massGrams
    return if(source!=null && unit.equals(source.unit,true) && sourceAmount!=null && sourceAmount>0 && sourceCanonical!=null)sourceCanonical*quantity/sourceAmount else null
}

/** Keep the source reference basis consistent when a historical consumed portion changes. */
internal fun rescaledMetadata(source:Entry?,quantity:Double?,unit:String):Map<String,String> {
    val metadata=source?.metadata.orEmpty().toMutableMap()
    val scale=metadata["nutrition_scale"]?.toDoubleOrNull() ?: return metadata
    val original=source?.quantity
    val factor=if(source!=null && quantity!=null && original!=null && original>0)portionFactor(source,original,source.unit,quantity,unit)else null
    val revised=factor?.times(scale)
    if(revised!=null && revised>0 && revised.isFinite())metadata["nutrition_scale"]=revised.toString()
    else metadata.remove("nutrition_scale")
    return metadata
}

internal fun portionFactor(source:Entry?,oldQuantity:Double,oldUnit:String,newQuantity:Double,newUnit:String):Double? {
    Units.convert(newQuantity,newUnit,oldUnit)?.let{return it/oldQuantity}
    for(volume in listOf(false,true)){
        val old=canonicalPortion(source,oldQuantity,oldUnit,volume)
        val updated=canonicalPortion(source,newQuantity,newUnit,volume)
        if(old!=null && old>0 && updated!=null)return updated/old
    }
    return null
}

internal fun convertEditorQuantity(draft:EditorDraft,newUnit:String):String {
    val q=draft.quantity.toDoubleOrNull() ?: return draft.quantity
    Units.convert(q,draft.unit,newUnit)?.let{return decimal(it)}
    val volume=draft.kind==EntryKind.DRINK
    val current=canonicalPortion(draft.base,q,draft.unit,volume)
    val target=canonicalPortion(draft.base,1.0,newUnit,volume)
    return if(current!=null && target!=null && target>0)decimal(current/target)else draft.quantity
}
