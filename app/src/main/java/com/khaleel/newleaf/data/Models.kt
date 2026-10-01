package com.khaleel.newleaf.data

import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class EntryKind { MEAL, FOOD, DRINK, ACTIVITY, WEIGHT, STEPS, CHECKIN, NOTE, RECIPE, TEMPLATE }

data class SourceReference(
    val provider: String = "manual", val itemId: String? = null, val barcode: String? = null,
    val retrievedAt: String? = null, val attribution: String = "", val license: String = "",
    val exportAllowed: Boolean = true, val snapshotAllowed: Boolean = true,
    val overrides: Map<String, String> = emptyMap(), val extensions: JsonObject = JsonObject()
)

data class NutrientEvidence(
    val status: String = "known", val origin: String = "user_entered",
    val unit: String = "", val basis: String = "consumed", val providerCode: String? = null,
    val coverageKnown: Int? = null, val coverageTotal: Int? = null, val extensions: JsonObject = JsonObject()
)

data class PortionDefinition(val label: String, val quantity: Double = 1.0, val massGrams: Double? = null, val volumeMl: Double? = null, val extensions: JsonObject = JsonObject())

/** Nutrients on a journal entry always describe the consumed portion, not a catalog basis. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val kind: EntryKind,
    val name: String,
    val occurredAt: String = Instant.now().toString(),
    val zoneId: String = ZoneId.systemDefault().id,
    val localDate: String = EventTimes.localDate(occurredAt, zoneId),
    val notes: String = "",
    val mealId: String? = null,
    val quantity: Double? = null,
    val unit: String = "",
    val nutrients: Map<String, Double?> = emptyMap(),
    val portionEvidence: String = "unspecified",
    val source: String = "manual",
    val sourceId: String? = null,
    val basisQuantity: Double? = null,
    val basisUnit: String = "",
    val isFavorite: Boolean = false,
    val distanceMeters: Double? = null,
    val durationSeconds: Long? = null,
    val weightKg: Double? = null,
    val steps: Long? = null,
    val tags: List<String> = emptyList(),
    val ratings: Map<String, Int> = emptyMap(),
    val componentIds: List<String> = emptyList(),
    val yieldServings: Double? = null,
    val yieldGrams: Double? = null,
    val version: Int = 1,
    val photoPaths: List<String> = emptyList(),
    val originalText: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = createdAt,
    val localTimestamp: String = EventTimes.localTimestamp(occurredAt, zoneId),
    val dateOnly: Boolean = kind == EntryKind.STEPS || kind == EntryKind.NOTE,
    val countsAsWater: Boolean = false,
    val volumeMl: Double? = null,
    val massGrams: Double? = null,
    val brand: String = "",
    val preparation: String = "",
    val category: String = "",
    val intensity: String = "",
    val endAt: String? = null,
    val loggingCompleteness: String = "unmarked",
    val matchStatus: String = "confirmed",
    val nutrientEvidence: Map<String, NutrientEvidence> = emptyMap(),
    val sourceReference: SourceReference? = null,
    val portionDefinitions: List<PortionDefinition> = emptyList(),
    val isLibrary: Boolean = kind == EntryKind.RECIPE || kind == EntryKind.TEMPLATE,
    val recipeId: String? = null,
    val recipeVersion: Int? = null,
    val recipeSnapshot: Entry? = null,
    val ingredientSnapshots: List<Entry> = emptyList(),
    val composition: List<Entry> = emptyList(),
    val missingContextIds: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val extensions: JsonObject = JsonObject()
)

data class AppSettings(
    val weightUnit: String = "lb", val volumeUnit: String = "fl oz", val distanceUnit: String = "mi",
    val theme: String = "system", val waterGoalMl: Double? = null,
    val gatewayUrl: String = "", val gatewayToken: String = "", val onboardingComplete: Boolean = false,
    val locale: String = "en-US", val region: String = "US", val remindersEnabled: Boolean = false,
    val extensions: JsonObject = JsonObject(),
    val usdaApiKey: String = ""
) {
    override fun toString(): String = "AppSettings(weightUnit=$weightUnit, volumeUnit=$volumeUnit, distanceUnit=$distanceUnit, theme=$theme, onboardingComplete=$onboardingComplete)"
}

data class Journal(val entries: List<Entry> = emptyList(), val settings: AppSettings = AppSettings(),
                   val drafts: Map<String, String> = emptyMap(), val extensions: JsonObject = JsonObject())
