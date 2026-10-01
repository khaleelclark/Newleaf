package com.khaleel.newleaf

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.khaleel.newleaf.data.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YearJournalTest {
    @Test fun yearOfRecordsRemainsReadableAndExportable() = runBlocking {
        val repository = JournalRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = repository.awaitLoaded()
        val zone = ZoneId.of("UTC")
        val firstDay = LocalDate.of(2025, 1, 1)
        val entries = (0 until 365).flatMap { day ->
            val date = firstDay.plusDays(day.toLong())
            (0 until 6).map { item ->
                EventTimes.at(Entry(id="year-fixture-$day-$item", kind=if(item<4)EntryKind.FOOD else EntryKind.DRINK,
                    name="Fictional year item $item", nutrients=mapOf("energy_kcal" to 100.0, "protein_g" to null),
                    quantity=if(item<4)100.0 else 8.0, unit=if(item<4)"g" else "fl oz",
                    countsAsWater=item==5, volumeMl=if(item>=4)236.5882365 else null), date.atTime(8+item, 0).atZone(zone).toInstant(), zone)
            }
        }
        try {
            repository.restoreJournal(Journal(entries, before.settings), true)
            val start = SystemClock.elapsedRealtime()
            val reopened = JournalRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            val loaded = try { reopened.awaitLoaded() } finally { reopened.close() }
            val loadMs = SystemClock.elapsedRealtime()-start
            assertEquals(2190, loaded.entries.size)
            val summaries = Summaries.range(loaded.entries, "2025-01-01", "2025-12-31")
            assertEquals(365, summaries.size)
            assertTrue(summaries.all { it.nutrients["energy_kcal"]?.knownSum==600.0 })
            val singleSave = SystemClock.elapsedRealtime()
            repository.upsert(loaded.entries.first().copy(notes="Fictional correction"))
            val saveMs = SystemClock.elapsedRealtime()-singleSave
            assertEquals(2190, JournalExports.previewRestore(JournalExports.backup(repository.state.value)).journal.entries.size)
            Log.i("NewleafValidation", "2190-record warm repository load=${loadMs}ms correction=${saveMs}ms; emulator only, not cold-start/phone acceptance")
            Unit
        } finally { repository.restoreJournal(before, true); repository.close() }
    }
}
