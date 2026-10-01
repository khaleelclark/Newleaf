package com.khaleel.newleaf.data

import com.google.gson.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object JournalExports {
    const val SCHEMA_VERSION = "1.0"
    private const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    private data class Prepared(val document: JsonObject, val records: List<Entry>, val selected: Set<String>,
        val context: Set<String>, val attachments: List<AttachmentManifest>, val files: Map<String, ByteArray>, val options: ExportOptions)

    fun json(journal: Journal, options: ExportOptions = ExportOptions()): String {
        require(!options.includePhotos) { "Use csvZip or backup to include photo bytes" }
        return JournalCodec.pretty.toJson(prepare(journal, options, null).document)
    }
    fun markdown(journal: Journal, options: ExportOptions = ExportOptions()): String {
        require(!options.includePhotos) { "Use csvZip or backup to include photo bytes" }
        return report(prepare(journal, options, null))
    }
    fun csvZip(journal: Journal, options: ExportOptions = ExportOptions(), photoReader: PhotoReader? = null): ByteArray =
        archive(prepare(journal, options, photoReader))
    fun backup(journal: Journal, photoReader: PhotoReader? = null): ByteArray =
        archive(prepare(journal, ExportOptions(includePhotos = true, includeLibrary = true, includeRecipes = true), photoReader, true))

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun prepare(journal: Journal, options: ExportOptions, reader: PhotoReader?, fullBackup: Boolean = false): Prepared {
        JournalValidation.journal(journal)
        val byId = journal.entries.associateBy { it.id }
        val selected = journal.entries.filter { options.selects(it) || it.isLibrary &&
            (if (it.kind == EntryKind.RECIPE) options.includeRecipes else options.includeLibrary) }.map { it.id }.toMutableSet()
        val included = selected.toMutableSet()
        var changed: Boolean
        do {
            changed = false
            for (id in included.toList()) {
                val entry = byId.getValue(id)
                val links = entry.componentIds + listOfNotNull(entry.mealId) +
                    (if (entry.kind == EntryKind.MEAL) journal.entries.filter { it.mealId == id }.map { it.id } else emptyList()) +
                    (if (entry.recipeSnapshot == null) listOfNotNull(entry.recipeId) else emptyList())
                for (link in links) if (link in byId && included.add(link)) changed = true
            }
        } while (changed)
        val records = journal.entries.filter { it.id in included }.sortedWith(compareBy<Entry> { it.localDate }.thenBy { it.occurredAt }.thenBy { it.id })
        val context = included - selected
        val attachmentMap = linkedMapOf<String, AttachmentManifest>()
        val pathNames = mutableMapOf<String, String>()
        val files = linkedMapOf<String, ByteArray>()
        fun attachments(entry: Entry) {
            entry.photoPaths.forEach { path ->
                val old = pathNames[path]
                if (old != null) {
                    attachmentMap[old] = attachmentMap.getValue(old).let { it.copy(linkedRecordIds = (it.linkedRecordIds + entry.id).distinct()) }
                } else {
                    val bytes = if (options.includePhotos) {
                        requireNotNull(reader) { "Photo reader required for complete attachment export" }.read(path).also {
                            require(it.isNotEmpty() && it.size <= MAX_FILE_BYTES) { "Empty or oversized attachment" }
                        }
                    } else null
                    val digest = bytes?.let(::sha256)
                    val extension = path.substringBefore('?').substringAfterLast('.', "bin").lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif") } ?: "bin"
                    val name = "attachments/${digest ?: UUID.nameUUIDFromBytes(path.toByteArray())}.$extension"
                    val mime = when(extension) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "heic" -> "image/heic"; "heif" -> "image/heif"; "gif" -> "image/gif"; else -> "application/octet-stream" }
                    pathNames[path] = name
                    val existing = attachmentMap[name]
                    attachmentMap[name] = AttachmentManifest(digest ?: name.substringAfter('/'), name, mime, digest, bytes?.size?.toLong(),
                        (existing?.linkedRecordIds.orEmpty() + entry.id).distinct(), options.includePhotos)
                    if (bytes != null) files[name] = bytes
                }
            }
            entry.recipeSnapshot?.let(::attachments)
            entry.ingredientSnapshots.forEach(::attachments); entry.composition.forEach(::attachments)
        }
        records.forEach(::attachments)
        fun sanitize(entry: Entry): Entry = entry.copy(notes = if (options.includeNotes) entry.notes else "",
            originalText = if (options.includeNotes) entry.originalText else "", photoPaths = entry.photoPaths.map { pathNames.getValue(it) },
            recipeSnapshot = entry.recipeSnapshot?.let(::sanitize), ingredientSnapshots = entry.ingredientSnapshots.map(::sanitize),
            composition = entry.composition.map(::sanitize))
        val sanitized = records.map(::sanitize)
        val offSubset = OpenFoodFactsExports.subset(sanitized)
        val settings = JournalCodec.gson.toJsonTree(journal.settings.copy(gatewayToken = "")).asJsonObject
        fun stripCredentials(objectValue: JsonObject) {
            objectValue.keySet().toList().forEach { key ->
                val normalized = key.lowercase().replace("_", "").replace("-", "")
                if (normalized.contains("token") || normalized.contains("secret") || normalized.contains("credential")) objectValue.remove(key)
                else objectValue[key].takeIf { it.isJsonObject }?.let { stripCredentials(it.asJsonObject) }
            }
        }
        stripCredentials(settings)
        val summaries = sanitized.filter { it.id in selected && !it.isLibrary }.map { it.localDate }.distinct().sorted().map { Summaries.day(sanitized.filter { it.id in selected }, it) }
        val manifest = JsonObject().apply {
            addProperty("export_id", UUID.randomUUID().toString()); addProperty("generated_at", Instant.now().toString())
            addProperty("app_version", "0.1"); addProperty("spec_version", "0.1"); addProperty("full_backup", fullBackup)
            add("options", JournalCodec.gson.toJsonTree(options)); addProperty("date_policy", "preserved local event date; inclusive")
            addProperty("selected_count", selected.size); addProperty("context_count", context.size)
            add("context_only_ids", JournalCodec.gson.toJsonTree(context.sorted()))
            add("selected_ids", JournalCodec.gson.toJsonTree(selected.sorted()))
            add("exclusions", JournalCodec.gson.toJsonTree(listOfNotNull("gateway credentials", "transient drafts/caches",
                if (!options.includeNotes) "notes and original descriptions" else null, if (!options.includePhotos) "photo bytes" else null,
                if (!options.includeLibrary) "unreferenced library" else null, if (!options.includeRecipes) "unreferenced recipes" else null)))
            add("source_attribution", JournalCodec.gson.toJsonTree(sanitized.flatMap { sources(it) }.distinct()))
        }
        require(sanitized.flatMap { sourceReferences(it) }.all { it.exportAllowed && it.snapshotAllowed }) { "A source restriction prevents full nutrition export; resolve provider access before exporting" }
        val root = JsonObject().apply {
            addProperty("schema_version", SCHEMA_VERSION); add("manifest", manifest); add("settings", settings)
            add("records", JournalCodec.gson.toJsonTree(sanitized)); add("extensions", journal.extensions.deepCopy())
            add("attachment_manifest", JournalCodec.gson.toJsonTree(attachmentMap.values.toList()))
            add("daily_summaries", JournalCodec.gson.toJsonTree(summaries))
            add("source_notices", JsonArray().apply { if (OpenFoodFactsExports.hasRecords(offSubset)) add(OpenFoodFactsExports.notice()) })
            add("open_food_facts_nutrition", offSubset)
        }
        val tables = linkedMapOf("meals" to EntryKind.MEAL, "food_entries" to EntryKind.FOOD, "drink_entries" to EntryKind.DRINK,
            "feeling_checkins" to EntryKind.CHECKIN, "activities" to EntryKind.ACTIVITY, "daily_steps" to EntryKind.STEPS,
            "weight_entries" to EntryKind.WEIGHT, "daily_context" to EntryKind.NOTE, "saved_meals" to EntryKind.TEMPLATE)
        tables.forEach { (table, kind) -> root.add(table, JsonArray().apply { sanitized.filter { it.kind == kind && (!it.isLibrary || kind == EntryKind.TEMPLATE) }.forEach { add(view(it, it.id in context)) } }) }
        root.add("custom_foods", JsonArray().apply { sanitized.filter { it.isLibrary && it.kind in setOf(EntryKind.FOOD, EntryKind.DRINK) }.forEach { add(view(it, it.id in context)) } })
        root.add("favorites", JournalCodec.gson.toJsonTree(sanitized.filter { it.isFavorite }.map { mapOf("id" to it.id, "quantity" to it.quantity, "unit" to it.unit, "version" to it.version) }))
        root.add("recipe_versions", JsonArray().apply { recipeVersions(sanitized).forEach { add(view(it, it.id in context || it.id !in selected)) } })
        manifest.add("independently_licensed_subsets", JournalCodec.gson.toJsonTree(if (OpenFoodFactsExports.hasRecords(offSubset)) listOf(OpenFoodFactsExports.SUBSET_FILE) else emptyList<String>()))
        manifest.add("table_counts", JsonObject().apply { root.entrySet().filter { it.value.isJsonArray }.forEach { addProperty(it.key, it.value.asJsonArray.size()) } })
        return Prepared(root, sanitized, selected, context, attachmentMap.values.toList(), files, options)
    }
    private fun sources(entry: Entry): List<String> = sourceReferences(entry).map { "${it.provider}: ${it.attribution} (${it.license})" }
    private fun sourceReferences(entry: Entry): List<SourceReference> = listOfNotNull(entry.sourceReference) +
        entry.ingredientSnapshots.flatMap(::sourceReferences) + entry.composition.flatMap(::sourceReferences) + entry.recipeSnapshot?.let(::sourceReferences).orEmpty()
    private fun recipeVersions(entries: List<Entry>): List<Entry> {
        fun recipes(e: Entry): List<Entry> = (if (e.kind == EntryKind.RECIPE) listOf(e) else emptyList()) +
            e.recipeSnapshot?.let(::recipes).orEmpty() + e.ingredientSnapshots.flatMap(::recipes) + e.composition.flatMap(::recipes)
        return entries.flatMap(::recipes).distinctBy { "${it.id}:${it.version}" }
    }
    private fun view(entry: Entry, context: Boolean): JsonObject = JournalCodec.gson.toJsonTree(entry).asJsonObject.apply {
        addProperty("context_only", context); addProperty("revision", entry.version)
        if (entry.dateOnly) { remove("occurredAt"); remove("localTimestamp") }
        add("nutrients", JsonArray().apply { entry.nutrients.forEach { (code, value) -> add(JsonObject().apply {
            addProperty("code", code); add("value", value?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            val evidence = entry.nutrientEvidence[code]
            addProperty("unit", evidence?.unit?.takeIf { it.isNotBlank() } ?: Units.nutrientUnit(code))
            addProperty("status", if (value == null) "unknown" else evidence?.status ?: "known")
            addProperty("origin", evidence?.origin ?: entry.source); addProperty("basis", evidence?.basis ?: "consumed"); addProperty("coverage_known", evidence?.coverageKnown); addProperty("coverage_total", evidence?.coverageTotal)
        }) } })
        add("amount", JsonObject().apply { addProperty("original_quantity", entry.quantity); addProperty("original_unit", entry.unit)
            addProperty("mass_g", entry.massGrams ?: entry.quantity?.let { Units.massGrams(it, entry.unit) })
            addProperty("volume_ml", entry.volumeMl ?: entry.quantity?.let { Units.volumeMl(it, entry.unit) }) })
    }
    private fun archive(prepared: Prepared): ByteArray {
        val files = linkedMapOf("journal.json" to JournalCodec.pretty.toJson(prepared.document).toByteArray(Charsets.UTF_8),
            "report.md" to report(prepared).toByteArray(Charsets.UTF_8), "DATA_DICTIONARY.md" to dictionary().toByteArray(Charsets.UTF_8))
        csvTables(prepared).forEach { (name, value) -> files[name] = value.toByteArray(Charsets.UTF_8) }
        val offSubset = prepared.document.getAsJsonObject("open_food_facts_nutrition")
        if (OpenFoodFactsExports.hasRecords(offSubset)) {
            files[OpenFoodFactsExports.SUBSET_FILE] = JournalCodec.pretty.toJson(offSubset).toByteArray(Charsets.UTF_8)
            files[OpenFoodFactsExports.NOTICE_FILE] = ("# Open Food Facts-derived nutrition license notice\n\n" + OpenFoodFactsExports.markdownNotice() + "\n").toByteArray(Charsets.UTF_8)
        }
        files.putAll(prepared.files)
        val integrity = JsonObject().apply { files.forEach { (name, bytes) -> addProperty(name, sha256(bytes)) } }
        files["integrity.json"] = JournalCodec.pretty.toJson(integrity).toByteArray(Charsets.UTF_8)
        require(files.values.sumOf { it.size.toLong() } <= MAX_ARCHIVE_BYTES) { "Archive exceeds size limit" }
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
            output.toByteArray()
        }
    }
    /** Reject malformed/future archives before anything is written to private storage. */
    fun previewRestore(bytes: ByteArray): RestorePreview {
        require(bytes.size.toLong() <= MAX_ARCHIVE_BYTES) { "Archive exceeds size limit" }
        val files = linkedMapOf<String, ByteArray>()
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            var total = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name.matches(Regex("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")) && entry.name.split('/').none { it == "." || it == ".." }) { "Unsafe archive path" }
                    require(entry.name !in files && files.size < 10000) { "Duplicate or excessive archive members" }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var fileSize = 0L
                    while (true) {
                        val read = zip.read(buffer); if (read < 0) break
                        fileSize += read; total += read
                        require(fileSize <= MAX_FILE_BYTES && total <= MAX_ARCHIVE_BYTES) { "Expanded archive exceeds size limit" }
                        output.write(buffer, 0, read)
                    }
                    files[entry.name] = output.toByteArray(); zip.closeEntry()
                }
            }
            val integrity = JsonParser.parseString(String(requireNotNull(files["integrity.json"]) { "Archive lacks integrity manifest" }, Charsets.UTF_8)).asJsonObject
            require(integrity.keySet() == files.keys - "integrity.json") { "Archive members do not match integrity manifest" }
            integrity.entrySet().forEach { (name, digest) -> require(sha256(files.getValue(name)) == digest.asString) { "File integrity failed: $name" } }
        } else files["journal.json"] = bytes
        val root = JsonParser.parseString(String(requireNotNull(files["journal.json"]) { "Archive lacks journal.json" }, Charsets.UTF_8)).asJsonObject
        require(root["schema_version"]?.asString == SCHEMA_VERSION) { "Unsupported schema version; upgrade Newleaf before restoring" }
        val manifest = requireNotNull(root.getAsJsonObject("manifest")) { "Missing manifest" }
        val entries = requireNotNull(root.getAsJsonArray("records")) { "Missing canonical records" }.map { JournalCodec.decodeEntry(it.asJsonObject) }
        val settings = JournalCodec.decodeSettings(root.getAsJsonObject("settings")).copy(gatewayToken = "")
        val knownKeys = setOf("schema_version", "manifest", "settings", "records", "extensions", "attachment_manifest", "daily_summaries", "meals", "food_entries", "drink_entries", "feeling_checkins", "activities", "daily_steps", "weight_entries", "daily_context", "custom_foods", "favorites", "saved_meals", "recipe_versions", "source_notices", "open_food_facts_nutrition")
        val extensions = root.getAsJsonObject("extensions")?.deepCopy() ?: JsonObject()
        root.entrySet().filter { it.key !in knownKeys }.forEach { extensions.add(it.key, it.value.deepCopy()) }
        val journal = Journal(entries, settings, extensions = extensions)
        JournalValidation.journal(journal)
        val contexts = manifest.getAsJsonArray("context_only_ids").map { it.asString }.toSet()
        val selected = manifest.getAsJsonArray("selected_ids").map { it.asString }.toSet()
        require(contexts.intersect(selected).isEmpty() && contexts + selected == entries.map { it.id }.toSet()) { "Export selection does not match records" }
        val exportedSummaries = root.getAsJsonArray("daily_summaries")
        val calculated = entries.filter { it.id in selected && !it.isLibrary }.map { it.localDate }.distinct().sorted().map { Summaries.day(entries.filter { it.id in selected }, it) }
        require(JournalCodec.gson.toJsonTree(calculated) == exportedSummaries) { "Derived summaries do not match canonical records" }
        val attachments = root.getAsJsonArray("attachment_manifest").map { JournalCodec.gson.fromJson(it, AttachmentManifest::class.java) }
        fun graph(e: Entry): List<Entry> = listOf(e) + e.recipeSnapshot?.let(::graph).orEmpty() + e.ingredientSnapshots.flatMap(::graph) + e.composition.flatMap(::graph)
        val recordGraph = entries.flatMap(::graph)
        attachments.forEach { attachment ->
            require(attachment.linkedRecordIds.isNotEmpty() && attachment.linkedRecordIds.all { id -> recordGraph.any { it.id == id && attachment.fileName in it.photoPaths } }) { "Invalid attachment association" }
            if (attachment.included) require(attachment.sha256?.matches(Regex("[0-9a-f]{64}")) == true && attachment.sizeBytes != null && attachment.sizeBytes > 0) { "Invalid attachment integrity metadata" }
        }
        require(files.keys.filter { it.startsWith("attachments/") }.toSet() == attachments.filter { it.included }.map { it.fileName }.toSet()) { "Archive photos do not match attachment manifest" }
        require(attachments.map { it.fileName }.distinct().size == attachments.size) { "Duplicate attachment manifest" }
        val restored = attachments.filter { it.included }.map { attachment ->
            require(attachment.fileName.startsWith("attachments/")) { "Invalid attachment name" }
            val content = requireNotNull(files[attachment.fileName]) { "Missing attachment ${attachment.fileName}" }
            require(content.size.toLong() == attachment.sizeBytes && sha256(content) == attachment.sha256) { "Attachment digest/size mismatch" }
            RestoreAttachment(attachment, content)
        }
        fun photoPaths(e: Entry): List<String> = e.photoPaths + e.recipeSnapshot?.let(::photoPaths).orEmpty() + e.ingredientSnapshots.flatMap(::photoPaths) + e.composition.flatMap(::photoPaths)
        require(entries.flatMap(::photoPaths).all { path -> attachments.any { it.fileName == path } }) { "Photo reference lacks a manifest record" }
        val includedPaths = restored.map { it.manifest.fileName }.toSet()
        fun available(e: Entry): Entry = e.copy(photoPaths = e.photoPaths.filter { it in includedPaths },
            recipeSnapshot = e.recipeSnapshot?.let(::available), ingredientSnapshots = e.ingredientSnapshots.map(::available), composition = e.composition.map(::available))
        val full = manifest["full_backup"]?.asBoolean ?: false
        if (full) require(attachments.all { it.included }) { "Full backup contains omitted photos" }
        return RestorePreview(journal.copy(entries = entries.map(::available)), restored, full, selected.size, contexts.size,
            restored.sumOf { it.bytes.size.toLong() } + files.getValue("journal.json").size, if (attachments.any { !it.included }) listOf("Photo bytes omitted from this export; unavailable references will not be restored") else emptyList())
    }
    /** Spreadsheet text uses a reversible apostrophe prefix for dangerous leading text. */
    fun csvCell(value: Any?): String {
        val raw = when (value) { null -> ""; is Double -> if (value == 0.0) "0" else value.toString(); else -> value.toString() }
        val protected = if (value is String && (raw.startsWith("'") || raw.firstOrNull() in listOf('\t', '\r', '\n') || raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@'))) "'$raw" else raw
        return "\"${protected.replace("\"", "\"\"")}\""
    }
    private fun csvTables(p: Prepared): Map<String, String> {
        val tables = linkedMapOf<String, String>()
        fun table(name: String, headers: List<String>, rows: List<List<Any?>>) {
            tables["$name.csv"] = (listOf(headers) + rows).joinToString("\r\n", postfix = "\r\n") { it.joinToString(",") { cell -> csvCell(cell) } }
        }
        val common = listOf("id", "event_utc", "event_local", "zone_id", "local_date", "name", "notes", "context_only", "revision")
        fun row(e: Entry): List<Any?> = listOf(e.id, if (e.dateOnly) null else e.occurredAt, if (e.dateOnly) null else e.localTimestamp, e.zoneId, e.localDate, e.name, e.notes, e.id in p.context, e.version)
        fun entries(kind: EntryKind) = p.records.filter { it.kind == kind && !it.isLibrary }
        table("meals", common + listOf("category", "original_text"), entries(EntryKind.MEAL).map { row(it) + listOf(it.category, it.originalText) })
        table("food_entries", common + listOf("meal_id", "brand", "preparation", "original_quantity", "original_unit", "mass_g", "portion_evidence", "source_reference", "recipe_id", "recipe_version"), entries(EntryKind.FOOD).map { row(it) + listOf(it.mealId, it.brand, it.preparation, it.quantity, it.unit, it.massGrams ?: it.quantity?.let { n -> Units.massGrams(n, it.unit) }, it.portionEvidence, "${it.id}:source", it.recipeId, it.recipeVersion) })
        table("drink_entries", common + listOf("meal_id", "drink_type", "original_volume", "original_unit", "volume_ml", "counts_as_water", "portion_evidence", "source_reference", "composition_json"), entries(EntryKind.DRINK).map { row(it) + listOf(it.mealId, it.category, it.quantity, it.unit, it.volumeMl ?: it.quantity?.let { n -> Units.volumeMl(n, it.unit) }, it.countsAsWater, it.portionEvidence, "${it.id}:source", JournalCodec.toJson(it.composition)) })
        val contributors = Nutrition.contributors(p.records)
        table("nutrients", listOf("contributor_type", "contributor_id", "nutrient_code", "value", "unit", "status", "origin", "basis", "source_reference", "context_only", "revision"), contributors.flatMap { e ->
            (Nutrition.coreCodes + e.nutrients.keys).distinct().map { code -> val evidence = e.nutrientEvidence[code]; listOf(e.kind.name, e.id, code, e.nutrients[code], evidence?.unit?.takeIf { it.isNotBlank() } ?: Units.nutrientUnit(code), if (e.nutrients[code] == null) "unknown" else evidence?.status ?: "known", evidence?.origin ?: e.source, evidence?.basis ?: "consumed", "${e.id}:source", e.id in p.context, e.version) }
        })
        table("feeling_checkins", common + listOf("meal_id", "observed_at", "entered_at", "elapsed_seconds", "ratings_json"), entries(EntryKind.CHECKIN).map { e -> row(e) + listOf(e.mealId, e.occurredAt, e.createdAt, p.records.firstOrNull { it.id == e.mealId }?.let { EventTimes.elapsedSeconds(e, it) }, JournalCodec.toJson(e.ratings)) })
        table("feeling_tags", listOf("checkin_id", "tag_code", "user_label", "context_only", "revision"), entries(EntryKind.CHECKIN).flatMap { e -> e.tags.map { listOf(e.id, it, it, e.id in p.context, e.version) } })
        table("activities", common + listOf("activity_type", "end_at", "distance_m", "duration_s", "intensity"), entries(EntryKind.ACTIVITY).map { row(it) + listOf(it.category, it.endAt, it.distanceMeters, it.durationSeconds, it.intensity) })
        table("daily_steps", common + listOf("steps", "source"), entries(EntryKind.STEPS).map { row(it) + listOf(it.steps, it.source) })
        table("weight_entries", common + listOf("original_weight", "original_unit", "weight_kg", "measurement_context"), entries(EntryKind.WEIGHT).map { row(it) + listOf(it.quantity, it.unit, it.weightKg, it.category) })
        table("daily_context", common + listOf("logging_completeness"), entries(EntryKind.NOTE).map { row(it) + it.loggingCompleteness })
        val sourceRows = p.records.flatMap { e -> sourceReferences(e).mapIndexed { index, source -> listOf("${e.id}:source${if (index == 0) "" else ":$index"}", e.id, source.provider, source.itemId, source.barcode, source.retrievedAt, e.basisQuantity, e.basisUnit, source.attribution, source.license, JournalCodec.toJson(source.overrides), e.id in p.context, e.version) }.ifEmpty { listOf(listOf("${e.id}:source", e.id, e.source, e.sourceId, null, null, e.basisQuantity, e.basisUnit, "", "", "{}", e.id in p.context, e.version)) } }
        table("source_references", listOf("id", "record_id", "provider", "food_id", "barcode", "retrieved_at", "basis_quantity", "basis_unit", "attribution", "license", "overrides_json", "context_only", "revision"), sourceRows)
        table("library", listOf("object_type", "id", "version", "name", "is_favorite", "default_quantity", "default_unit", "portion_definitions_json", "context_only", "revision"), p.records.filter { it.isLibrary || it.isFavorite }.map { listOf(it.kind.name, it.id, it.version, it.name, it.isFavorite, it.quantity, it.unit, JournalCodec.toJson(it.portionDefinitions), it.id in p.context, it.version) })
        val recipes = recipeVersions(p.records)
        table("recipe_ingredients", listOf("recipe_id", "recipe_version", "ingredient_id", "quantity", "unit", "source", "snapshot_json", "context_only", "revision"), recipes.flatMap { recipe -> recipe.ingredientSnapshots.map { listOf(recipe.id, recipe.version, it.id, it.quantity, it.unit, it.source, JournalCodec.toJson(it), recipe.id in p.context, recipe.version) } })
        table("recipe_nutrients", listOf("recipe_id", "recipe_version", "nutrient_code", "value", "unit", "status", "coverage", "context_only", "revision"), recipes.flatMap { recipe -> (Nutrition.coreCodes + recipe.nutrients.keys).distinct().map { code -> val evidence = recipe.nutrientEvidence[code]; listOf(recipe.id, recipe.version, code, recipe.nutrients[code], Units.nutrientUnit(code), if (recipe.nutrients[code] == null) "unknown" else evidence?.status ?: "known", evidence?.basis, recipe.id in p.context, recipe.version) } })
        table("recipe_yields", listOf("recipe_id", "recipe_version", "servings", "final_mass_g", "context_only", "revision"), recipes.map { listOf(it.id, it.version, it.yieldServings, it.yieldGrams, it.id in p.context, it.version) })
        table("template_components", listOf("saved_meal_id", "version", "component_type", "component_id", "default_quantity", "default_unit", "snapshot_json", "context_only", "revision"), p.records.filter { it.kind == EntryKind.TEMPLATE }.flatMap { template -> template.componentIds.map { id -> val component = p.records.firstOrNull { it.id == id }; listOf(template.id, template.version, component?.kind?.name, id, component?.quantity, component?.unit, component?.let { JournalCodec.toJson(it) }, template.id in p.context, template.version) } })
        val days = p.records.filter { it.id in p.selected && !it.isLibrary }.map { it.localDate }.distinct().sorted().map { Summaries.day(p.records.filter { it.id in p.selected }, it) }
        table("daily_summaries", listOf("date", "entry_count", "water_ml", "drink_volume_ml", "known_energy_kcal", "energy_known_count", "energy_unknown_count", "energy_estimated_count", "energy_completeness", "protein_g", "carbohydrate_g", "fat_g", "nutrition_coverage_json", "steps", "activity_distance_m", "activity_duration_s", "daily_weight_kg", "weight_measurement_count", "logging_completeness"), days.map { d -> val energy = d.nutrients.getValue("energy_kcal"); listOf(d.date, d.entryCount, d.waterMl, d.drinkVolumeMl, energy.knownSum, energy.knownContributors, energy.unknownContributors, energy.estimatedContributors, energy.completeness.name.lowercase(), d.nutrients["protein_g"]?.knownSum, d.nutrients["carbohydrate_g"]?.knownSum, d.nutrients["fat_g"]?.knownSum, JournalCodec.toJson(d.nutrients), d.steps, d.activityDistanceMeters, d.activityDurationSeconds, d.weightKg, d.weightMeasurements, d.loggingCompleteness) })
        table("attachments", listOf("attachment_id", "filename", "mime_type", "sha256", "size_bytes", "linked_record_id", "included"), p.attachments.flatMap { a -> a.linkedRecordIds.map { listOf(a.id, a.fileName, a.mimeType, a.sha256, a.sizeBytes, it, a.included) } })
        return tables
    }
    private fun dictionary(): String = """
        # Newleaf CSV data dictionary — schema 1.0

        journal.json is the canonical lossless representation. CSVs are spreadsheet views;
        nested JSON columns retain ingredient/composition/evidence data. Do not restore from CSV alone.

        Every CSV has a header, UTF-8 text, CRLF row separators and RFC 4180 double-quote escaping.
        Numeric fields use decimal values without thousands separators. Blank numeric cells mean null
        (unknown), never zero. Explicit zero is 0. Booleans are true/false. IDs are stable strings.
        event_utc is an ISO UTC instant; event_local includes the original offset; zone_id is IANA;
        local_date is the preserved ISO date. Date-only rows leave event time cells blank.
        context_only rows accompany selection for interpretation and are excluded from summaries.
        revision/version are positive integers. JSON maps retain extra nutrient/provider fields.

        Text safety: cells beginning with an apostrophe, tab, CR/LF, or whose first non-whitespace
        character is = + - @ are prefixed with one apostrophe. Remove exactly one prefix apostrophe
        from those protected text cells to reverse escaping. Numeric values are never escaped as text.
        Original text is always preserved exactly in journal.json. Units: g, mL, kg, m, seconds, kcal;
        sodium in mg. Original quantities and units are retained beside canonical quantities.
        Nutrient status is known/estimated/unknown; basis describes consumed vs recipe totals and scaling.
        Coverage reports known sums and contributor counts, not inferred actual daily intake.
        Daily weight is mean of that date's measurements; no-entry values remain blank.

        Table headers document fields. *_json columns are structured JSON. Foreign keys reference
        stable record IDs; recipe_version links to immutable ingredient snapshots, not current catalogs.
        attachment SHA-256/size verify original exported bytes. integrity.json verifies every archive
        member except itself. Export excludes gateway tokens, caches and private transient drafts.
    """.trimIndent() + "\n"

    private fun report(p: Prepared): String = buildString {
        fun line(text: String = "") { append(text).append('\n') }
        fun escaped(text: String) = text.replace("\\", "\\\\").replace("`", "\\`").replace("*", "\\*").replace("_", "\\_").replace("<", "&lt;").replace(">", "&gt;").replace("[", "\\[").replace("]", "\\]")
        line("# Newleaf journal report"); line()
        line("Generated: ${p.document["manifest"].asJsonObject["generated_at"].asString}. Schema $SCHEMA_VERSION.")
        line("Range: ${p.options.fromDate ?: "all earlier dates"} through ${p.options.toDate ?: "all later dates"}, inclusive, preserved local dates.")
        line("Selected records: ${p.selected.size}; context-only records: ${p.context.size}.")
        line("Notes: ${p.options.includeNotes}; photo bytes: ${p.options.includePhotos}; library: ${p.options.includeLibrary}; recipes: ${p.options.includeRecipes}.")
        line("Values describe logged observations. Unknown values are not zero; complete nutrition does not imply complete daily logging.")
        val selectedEntries = p.records.filter { it.id in p.selected && !it.isLibrary }
        for (date in selectedEntries.map { it.localDate }.distinct().sorted()) {
            val summary = Summaries.day(selectedEntries, date)
            line(); line("## $date"); line()
            val energy = summary.nutrients.getValue("energy_kcal")
            line("Known logged calories: ${energy.knownSum ?: "unknown"} kcal; ${energy.completeness.name.lowercase()} nutrition (${energy.knownContributors} known, ${energy.unknownContributors} unknown, ${energy.estimatedContributors} estimated contributors).")
            line("Water: ${summary.waterMl ?: "unknown"} mL; total drinks: ${summary.drinkVolumeMl ?: "unknown"} mL; steps: ${summary.steps ?: "unknown"}.")
            line("Activity: ${summary.activityDistanceMeters ?: "unknown"} m, ${summary.activityDurationSeconds ?: "unknown"} s; daily weight: ${summary.weightKg ?: "unknown"} kg (${summary.weightMeasurements} measurements). Logging completeness: ${summary.loggingCompleteness}.")
            summary.nutrients.filterKeys { it != "energy_kcal" }.forEach { (code, coverage) -> line("- $code: ${coverage.knownSum ?: "unknown"} ${Units.nutrientUnit(code)}; ${coverage.completeness.name.lowercase()}, ${coverage.unknownContributors} unknown contributors.") }
            line()
            for (e in selectedEntries.filter { it.localDate == date }.sortedBy { it.occurredAt }) {
                line("### ${if (e.dateOnly) "Date-only" else e.localTimestamp} — ${escaped(e.name)} (${e.kind.name.lowercase()})")
                line("ID: ${e.id}; revision ${e.version}; zone ${e.zoneId}; source ${escaped(e.source)}.")
                e.quantity?.let { line("Portion: $it ${escaped(e.unit)}; ${e.portionEvidence}.") }
                if (e.kind in setOf(EntryKind.FOOD, EntryKind.DRINK)) {
                    val c = Nutrition.all(listOf(e))["energy_kcal"]!!
                    line("Known calories: ${c.knownSum ?: "unknown"} kcal; nutrition ${c.completeness.name.lowercase()}.")
                }
                e.volumeMl?.let { line("Drink volume: $it mL; counts as water: ${e.countsAsWater}.") }
                e.distanceMeters?.let { line("Distance: $it m.") }; e.durationSeconds?.let { line("Duration: $it s.") }
                e.steps?.let { line("Daily steps: $it.") }; e.weightKg?.let { line("Weight: $it kg.") }
                e.mealId?.let { meal -> line("Linked meal: $meal."); p.records.firstOrNull { it.id == meal }?.let { if (e.kind == EntryKind.CHECKIN) line("Observed ${EventTimes.elapsedSeconds(e, it)} seconds after meal; entered ${e.createdAt}.") } }
                if (e.tags.isNotEmpty()) line("Tags: ${e.tags.joinToString { escaped(it) }}.")
                if (e.ratings.isNotEmpty()) line("Ratings (1–5): ${e.ratings}.")
                if (e.notes.isNotBlank()) { line("Notes:"); line(escaped(e.notes)) }
                if (e.originalText.isNotBlank()) { line("Original description:"); line(escaped(e.originalText)) }
                e.recipeId?.let { line("Recipe snapshot: $it / version ${e.recipeVersion}.") }
                if (e.photoPaths.isNotEmpty()) line("Attachments: ${e.photoPaths.joinToString()}.")
                line()
            }
        }
        if (p.context.isNotEmpty()) {
            line("## Context outside selection"); line()
            p.records.filter { it.id in p.context }.forEach { line("- ${it.id}: ${escaped(it.name)}; ${it.kind}; ${it.localDate}; context only, excluded from summaries."); if (it.notes.isNotBlank()) line("  ${escaped(it.notes)}") }
        }
        val library = p.records.filter { it.isLibrary }
        if (library.isNotEmpty()) { line(); line("## Library"); line(); library.forEach { line("- ${escaped(it.name)} (${it.kind}, version ${it.version}), ID ${it.id}.") } }
        line(); line("## Sources, units, and exclusions"); line()
        p.records.flatMap(::sources).distinct().forEach { line("- ${escaped(it)}") }
        if (OpenFoodFactsExports.hasRecords(p.document.getAsJsonObject("open_food_facts_nutrition"))) {
            line(); line(OpenFoodFactsExports.markdownNotice()); line()
            line("OFF-derived nutrition subset (structured JSON, independently extractable):")
            line("```json"); line(JournalCodec.pretty.toJson(p.document["open_food_facts_nutrition"])); line("```")
        }
        line("Canonical units: grams, mL, kg, meters, seconds, kcal, sodium mg. US fl oz = 29.5735295625 mL; weight oz = 28.349523125 g.")
        line("Gateway credentials and transient drafts/caches are excluded. Other exclusions: ${p.document["manifest"].asJsonObject["exclusions"]}.")
    }
}
