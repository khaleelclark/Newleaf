package com.khaleel.newleaf.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

/** One durable journal; transactions are serialized and state changes only after a successful commit. */
class JournalRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val storage = JournalStorage.get(appContext)
    private val database = storage.database
    private val dao = database.journalDao()
    private val credentials = DeviceCredentials(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = storage.mutex
    private val loaded = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(Journal())
    val state: StateFlow<Journal> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = mutableError.asStateFlow()
    @Volatile private var closed = false
    var lastRecoveryBackup: File? = null
        private set

    init {
        scope.launch {
            try {
                dao.changes().collect {
                    mutex.withLock { mutableState.value = database.withTransaction { readJournal() } }
                    if (!loaded.isCompleted) loaded.complete(Unit)
                }
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                mutableError.value = failure
                if (!loaded.isCompleted) loaded.completeExceptionally(failure)
            }
        }
    }
    suspend fun awaitLoaded(): Journal { check(!closed) { "Repository is closed" }; loaded.await(); return state.value }
    private suspend fun readJournal(): Journal {
        val meta = dao.meta().associate { it.key to it.value }
        val settings = meta["settings"]?.let(JournalCodec::settings) ?: AppSettings()
        val journal = Journal(dao.entries().map { JournalCodec.entry(it.payload) }, settings.copy(gatewayToken = credentials.read()),
            dao.drafts().associate { it.key to it.value }, meta["extensions"]?.let { com.google.gson.JsonParser.parseString(it).asJsonObject } ?: com.google.gson.JsonObject())
        JournalValidation.journal(journal)
        return journal
    }
    private suspend fun persist(previous: Journal, next: Journal) {
        JournalValidation.journal(next)
        val ids = next.entries.map { it.id }.toSet()
        val old = previous.entries.associateBy { it.id }
        dao.delete(old.keys - ids)
        // Delete daily-key collisions before INSERT to preserve a stable replacement ID.
        val rows = next.entries.filter { old[it.id]?.let { oldEntry -> JournalCodec.same(oldEntry, it) } != true }.map(EntryRow::of)
        dao.put(rows)
        dao.putMeta(MetaRow("settings", JournalCodec.toJson(next.settings.copy(gatewayToken = ""))))
        dao.putMeta(MetaRow("extensions", JournalCodec.toJson(next.extensions)))
        if (previous.drafts != next.drafts) { dao.clearDrafts(); next.drafts.forEach { (key, value) -> dao.putDraft(DraftRow(key, value)) } }
        dao.putMeta(MetaRow("revision", UUID.randomUUID().toString()))
    }
    private suspend fun mutate(transform: (Journal) -> Journal) {
        loaded.await()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!closed) { "Repository is closed" }
                val next = database.withTransaction { val previous = readJournal(); transform(previous).also { persist(previous, it) } }
                mutableState.value = next
            }
        }
    }
    suspend fun upsert(entry: Entry) = upsertAll(listOf(entry))
    suspend fun upsertAll(entries: List<Entry>) = mutate { previous ->
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate upsert IDs" }
        entries.forEach(JournalValidation::entry)
        val old = previous.entries.associateBy { it.id }
        val revised = entries.map { entry -> old[entry.id]?.let { existing ->
            if (JournalCodec.same(existing, entry)) existing else entry.copy(version = maxOf(entry.version, existing.version + 1),
                createdAt = existing.createdAt, updatedAt = Instant.now().toString())
        } ?: entry }
        val incoming = revised.associateBy { it.id }
        val incomingDaily = entries.filter { !it.isLibrary && it.kind in setOf(EntryKind.STEPS, EntryKind.NOTE) }.map { "${it.kind}:${it.localDate}" }.toSet()
        val retained = previous.entries.filter { it.id !in incoming && (it.isLibrary || "${it.kind}:${it.localDate}" !in incomingDaily) }
        previous.copy(entries = retained + revised)
    }
    suspend fun delete(ids: Set<String>) { deleteWithDependents(ids, false) }

    /** Returns removed AND pre-edit dependent snapshots so upsertAll can Undo the whole transaction. */
    suspend fun deleteWithDependents(ids: Set<String>, cascade: Boolean): List<Entry> {
        var undo: List<Entry> = emptyList()
        mutate { previous ->
            val deleting = ids.toMutableSet()
            if (cascade) {
                var changed: Boolean
                do {
                    changed = false
                    previous.entries.forEach { e -> if ((e.mealId in deleting || previous.entries.any { it.id in deleting && it.kind == EntryKind.MEAL && e.id in it.componentIds }) && deleting.add(e.id)) changed = true }
                } while (changed)
            }
            val touched = previous.entries.filter { it.id in deleting || it.mealId in deleting || it.componentIds.any { id -> id in deleting } || it.recipeId in deleting }
            undo = touched
            previous.copy(entries = previous.entries.filter { it.id !in deleting }.map { e ->
                if (e in touched) e.copy(mealId = e.mealId?.takeIf { it !in deleting }, componentIds = e.componentIds.filter { id -> id !in deleting || e.ingredientSnapshots.any { it.id == id } },
                    recipeId = if (e.recipeSnapshot == null) e.recipeId?.takeIf { it !in deleting } else e.recipeId,
                    version = e.version + 1, updatedAt = Instant.now().toString()) else e
            })
        }
        return undo
    }
    suspend fun saveSettings(settings: AppSettings) {
        JournalValidation.settings(settings)
        loaded.await()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!closed)
                val oldToken = credentials.read()
                credentials.save(settings.gatewayToken)
                try {
                    val next = database.withTransaction { val old = readJournal(); old.copy(settings = settings).also { persist(old, it) } }
                    mutableState.value = next
                } catch (failure: Throwable) { credentials.save(oldToken); throw failure }
            }
        }
    }
    suspend fun saveDraft(key: String, value: String) = mutate { require(key.isNotBlank()); it.copy(drafts = it.drafts + (key to value)) }
    suspend fun clearDraft(key: String) = mutate { it.copy(drafts = it.drafts - key) }

    fun restoreConflicts(incoming: Journal): List<RestoreConflict> = conflicts(state.value, incoming)
    private fun conflicts(current: Journal, incoming: Journal): List<RestoreConflict> {
        val byId = current.entries.associateBy { it.id }
        return incoming.entries.mapNotNull { entry ->
            val existing = byId[entry.id] ?: if (!entry.isLibrary && entry.kind in setOf(EntryKind.STEPS, EntryKind.NOTE)) current.entries.firstOrNull { it.kind == entry.kind && it.localDate == entry.localDate && !it.isLibrary } else null
            if (existing != null && !JournalCodec.same(existing, entry)) RestoreConflict(entry.id, existing.version, entry.version) else null
        }
    }
    private fun merged(current: Journal, incoming: Journal, replace: Boolean, resolutions: Map<String, ConflictResolution>): Journal {
        JournalValidation.journal(incoming)
        val settings = incoming.settings.copy(gatewayToken = current.settings.gatewayToken,
            gatewayUrl = if (current.settings.gatewayToken.isNotEmpty()) current.settings.gatewayUrl else incoming.settings.gatewayUrl)
        if (replace) return incoming.copy(settings = settings)
        val conflicts = conflicts(current, incoming)
        val unresolved = conflicts.filter { it.id !in resolutions }
        if (unresolved.isNotEmpty()) throw RestoreConflictException(unresolved)
        val entries = current.entries.associateBy { it.id }.toMutableMap()
        incoming.entries.forEach { entry ->
            if (resolutions[entry.id] != ConflictResolution.KEEP_EXISTING) {
                if (!entry.isLibrary && entry.kind in setOf(EntryKind.STEPS, EntryKind.NOTE)) entries.values.filter { it.kind == entry.kind && it.localDate == entry.localDate && !it.isLibrary }.map { it.id }.forEach(entries::remove)
                entries[entry.id] = entry
            }
        }
        val extensions = current.extensions.deepCopy().apply { incoming.extensions.entrySet().forEach { add(it.key, it.value.deepCopy()) } }
        return Journal(entries.values.toList(), settings, current.drafts + incoming.drafts, extensions)
    }
    private fun photoReader(): PhotoReader = PhotoReader { path ->
        if (path.startsWith("content://")) appContext.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() } ?: error("Photo is unavailable")
        else {
            val file = File(path).canonicalFile
            require(file.path.startsWith(appContext.filesDir.canonicalPath + File.separator)) { "Photo is outside private journal storage" }
            file.readBytes()
        }
    }
    private fun recovery(current: Journal) {
        val bytes = JournalExports.backup(current, photoReader())
        val directory = File(appContext.filesDir, "recovery").apply { check(isDirectory || mkdirs()) }
        val temporary = File(directory, "${UUID.randomUUID()}.tmp")
        val destination = File(directory, "before-restore-${Instant.now().toEpochMilli()}.zip")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            check(temporary.renameTo(destination)) { "Recovery backup could not be saved" }
            lastRecoveryBackup = destination
            directory.listFiles()?.filter { it.extension == "zip" }?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
        } finally { temporary.delete() }
    }
    suspend fun restoreJournal(journal: Journal, replace: Boolean) = restoreJournal(journal, replace, emptyMap())
    suspend fun restoreJournal(journal: Journal, replace: Boolean, resolutions: Map<String, ConflictResolution>) {
        mutate { current -> merged(current, journal, replace, resolutions).also { if (replace) recovery(current) } }
    }
    suspend fun restoreArchive(preview: RestorePreview, replace: Boolean, resolutions: Map<String, ConflictResolution> = emptyMap()) {
        loaded.await()
        withContext(Dispatchers.IO) {
            val directory = File(appContext.filesDir, "photos/restore-${UUID.randomUUID()}")
            var committed = false
            try {
                require(preview.attachments.map { it.manifest.fileName }.distinct().size == preview.attachments.size)
                val paths = mutableMapOf<String, String>()
                preview.attachments.forEach { attachment ->
                    require(attachment.bytes.size.toLong() == attachment.manifest.sizeBytes && JournalExports.sha256(attachment.bytes) == attachment.manifest.sha256)
                    check(directory.isDirectory || directory.mkdirs()) { "Unable to prepare photo storage" }
                    val extension = attachment.manifest.fileName.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
                    val file = File(directory, "${attachment.manifest.sha256}.$extension")
                    FileOutputStream(file).use { it.write(attachment.bytes); it.fd.sync() }
                    paths[attachment.manifest.fileName] = file.absolutePath
                }
                fun rewrite(e: Entry): Entry = e.copy(photoPaths = e.photoPaths.map { requireNotNull(paths[it]) { "Photo reference is missing" } },
                    recipeSnapshot = e.recipeSnapshot?.let(::rewrite), ingredientSnapshots = e.ingredientSnapshots.map(::rewrite), composition = e.composition.map(::rewrite))
                restoreJournal(preview.journal.copy(entries = preview.journal.entries.map(::rewrite)), replace, resolutions)
                committed = true
            } finally { if (!committed) directory.deleteRecursively() }
        }
    }
    /** Call after the UI undo window expires. Recovery backups retain their own photo bytes. */
    suspend fun purgeUnreferencedPhotos(minimumAgeMillis: Long = 24 * 60 * 60 * 1000L, protectedPaths: Set<String> = emptySet()): Int {
        require(minimumAgeMillis >= 0)
        awaitLoaded()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = database.withTransaction { readJournal() }
                val references = PhotoReferences.collect(current)
                if (references == null) return@withLock 0
                val referenced = (references + protectedPaths).map { File(it).canonicalPath }.toSet()
                val cutoff = System.currentTimeMillis() - minimumAgeMillis
                File(appContext.filesDir, "photos").walkTopDown().filter { it.isFile && it.lastModified() <= cutoff && it.canonicalPath !in referenced }.count { it.delete() }
            }
        }
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        if (!loaded.isCompleted) loaded.completeExceptionally(CancellationException("Repository closed before loading"))
        // Other UI/reminder handles may still be using the process-owned Room database.
    }
}
