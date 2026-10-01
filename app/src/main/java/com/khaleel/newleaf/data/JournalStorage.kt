package com.khaleel.newleaf.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.Executors

/**
 * One Room connection owner per database path for this application process.
 * UI, reminders and repository handles share its executor and write mutex.
 * Handles can close independently; Android owns the process-lifetime database.
 */
internal class JournalStorage private constructor(context: Context) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NewleafJournalDatabase").apply { isDaemon = true }
    }
    val database: JournalDatabase = Room.databaseBuilder(context.applicationContext,
        JournalDatabase::class.java, "newleaf-journal.db")
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryExecutor(executor)
        .setTransactionExecutor(executor)
        .build()
    val mutex = Mutex()

    companion object {
        private val stores = mutableMapOf<String, JournalStorage>()
        @Synchronized fun get(context: Context): JournalStorage {
            val app = context.applicationContext
            val path = app.getDatabasePath("newleaf-journal.db").canonicalPath
            return stores.getOrPut(path) { JournalStorage(app) }
        }
    }
}
