package com.khaleel.newleaf.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries", indices = [Index("localDate"), Index("kind"), Index("mealId"), Index("sourceId"), Index(value = ["dailyKey"], unique = true)])
internal data class EntryRow(@PrimaryKey val id: String, val kind: String, val localDate: String, val occurredAt: String,
    val mealId: String?, val sourceId: String?, val isLibrary: Boolean, val version: Int, val dailyKey: String?, val payload: String) {
    companion object {
        fun of(entry: Entry) = EntryRow(entry.id, entry.kind.name, entry.localDate, entry.occurredAt, entry.mealId, entry.sourceId,
            entry.isLibrary, entry.version, if (!entry.isLibrary && entry.kind in setOf(EntryKind.STEPS, EntryKind.NOTE)) "${entry.kind}:${entry.localDate}" else null,
            JournalCodec.toJson(entry))
    }
}
@Entity(tableName = "journal_meta")
internal data class MetaRow(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "drafts")
internal data class DraftRow(@PrimaryKey val key: String, val value: String)

@Dao
internal interface JournalDao {
    @Query("SELECT * FROM entries ORDER BY localDate, occurredAt, id") suspend fun entries(): List<EntryRow>
    @Query("SELECT * FROM journal_meta") suspend fun meta(): List<MetaRow>
    @Query("SELECT * FROM drafts") suspend fun drafts(): List<DraftRow>
    @Query("SELECT value FROM journal_meta WHERE `key`='revision'") fun changes(): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entries: List<EntryRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeta(meta: MetaRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(draft: DraftRow)
    @Query("DELETE FROM entries WHERE id IN (:ids)") suspend fun delete(ids: Set<String>)
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
    @Query("DELETE FROM drafts WHERE `key`=:key") suspend fun clearDraft(key: String)
}

@Database(entities = [EntryRow::class, MetaRow::class, DraftRow::class], version = 1, exportSchema = true)
internal abstract class JournalDatabase : RoomDatabase() { abstract fun journalDao(): JournalDao }
