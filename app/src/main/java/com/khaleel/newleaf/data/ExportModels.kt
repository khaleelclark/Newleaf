package com.khaleel.newleaf.data

import java.time.LocalDate

/** Range boundaries are inclusive and apply to the preserved local event date. */
data class ExportOptions(
    val fromDate: String? = null, val toDate: String? = null,
    val includeNotes: Boolean = true, val includePhotos: Boolean = false,
    val includeLibrary: Boolean = false, val includeRecipes: Boolean = false
) {
    init {
        fromDate?.let(LocalDate::parse); toDate?.let(LocalDate::parse)
        require(fromDate == null || toDate == null || fromDate <= toDate) { "Export range is reversed" }
    }
    fun selects(entry: Entry): Boolean = !entry.isLibrary &&
        (fromDate == null || entry.localDate >= fromDate) && (toDate == null || entry.localDate <= toDate)
}
fun interface PhotoReader { fun read(path: String): ByteArray }

data class AttachmentManifest(val id: String, val fileName: String, val mimeType: String, val sha256: String?,
    val sizeBytes: Long?, val linkedRecordIds: List<String>, val included: Boolean)
data class RestoreAttachment(val manifest: AttachmentManifest, val bytes: ByteArray)
data class RestorePreview(val journal: Journal, val attachments: List<RestoreAttachment>, val isFullBackup: Boolean,
    val selectedCount: Int, val contextCount: Int, val requiredStorageBytes: Long,
    val warnings: List<String> = emptyList())
enum class ConflictResolution { KEEP_EXISTING, USE_INCOMING }
data class RestoreConflict(val id: String, val existingVersion: Int, val incomingVersion: Int)
class RestoreConflictException(val conflicts: List<RestoreConflict>) : IllegalArgumentException("Restore needs decisions for ${conflicts.size} conflicting records")
