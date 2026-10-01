package com.khaleel.newleaf.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun withJournal(test: suspend (JournalRepository) -> Unit) {
        val repository = JournalRepository(context)
        repository.awaitLoaded()
        val original = repository.state.value
        try { test(repository) } finally { repository.restoreJournal(original, replace = true); repository.close() }
    }
    @Test fun invalidBatchLeavesDurableStateUnchanged() = runBlocking {
        withJournal { repo ->
            val before = repo.state.value
            val good = Entry(kind = EntryKind.FOOD, name = "Valid fixture")
            val invalid = Entry(kind = EntryKind.FOOD, name = "Invalid fixture", mealId = "missing-${UUID.randomUUID()}")
            try { repo.upsertAll(listOf(good, invalid)); fail("Invalid transaction accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.state.value)
            val reopened = JournalRepository(context)
            try { reopened.awaitLoaded(); assertEquals(before.entries, reopened.state.value.entries) } finally { reopened.close() }
        }
    }
    @Test fun deleteUnlinksDependentsAndUndoRestoresAllLinks() = runBlocking {
        withJournal { repo ->
            val meal = Entry(kind = EntryKind.MEAL, name = "Delete fixture")
            val food = Entry(kind = EntryKind.FOOD, name = "Food fixture", mealId = meal.id)
            val feeling = Entry(kind = EntryKind.CHECKIN, name = "Feeling fixture", mealId = meal.id)
            repo.upsertAll(listOf(meal.copy(componentIds = listOf(food.id)), food, feeling))
            val undo = repo.deleteWithDependents(setOf(meal.id), cascade = false)
            assertNull(repo.state.value.entries.first { it.id == food.id }.mealId)
            assertNull(repo.state.value.entries.first { it.id == feeling.id }.mealId)
            repo.upsertAll(undo)
            assertEquals(meal.id, repo.state.value.entries.first { it.id == food.id }.mealId)
            val deleted = repo.deleteWithDependents(setOf(meal.id), cascade = true)
            assertEquals(setOf(meal.id, food.id, feeling.id), deleted.map { it.id }.toSet())
            assertFalse(repo.state.value.entries.any { it.id in deleted.map { e -> e.id } })
        }
    }
    @Test fun restoreMergeRequiresConflictChoiceAndSkipsIdenticalEntries() = runBlocking {
        withJournal { repo ->
            repo.saveSettings(repo.state.value.settings.copy(onboardingComplete = true))
            val existing = Entry(kind = EntryKind.FOOD, name = "Conflict fixture", notes = "Original")
            repo.upsert(existing)
            val duplicate = Journal(listOf(existing), repo.state.value.settings.copy(onboardingComplete = false))
            repo.restoreJournal(duplicate, replace = false)
            assertEquals(1, repo.state.value.entries.count { it.id == existing.id })
            assertTrue(repo.state.value.settings.onboardingComplete)
            // Reproduce importing an unonboarded sample through the actual backup preview path.
            repo.restoreArchive(JournalExports.previewRestore(JournalExports.backup(duplicate)), replace = false)
            assertTrue(repo.state.value.settings.onboardingComplete)
            val incoming = duplicate.copy(entries = listOf(existing.copy(notes = "Incoming", version = 2)))
            try { repo.restoreJournal(incoming, false); fail("Conflict should require choice") } catch (e: RestoreConflictException) { assertEquals(existing.id, e.conflicts.single().id) }
            assertEquals("Original", repo.state.value.entries.single { it.id == existing.id }.notes)
            repo.restoreJournal(incoming, false, mapOf(existing.id to ConflictResolution.KEEP_EXISTING))
            assertEquals("Original", repo.state.value.entries.single { it.id == existing.id }.notes)
            assertTrue(repo.state.value.settings.onboardingComplete)
            repo.restoreJournal(incoming, false, mapOf(existing.id to ConflictResolution.USE_INCOMING))
            assertEquals("Incoming", repo.state.value.entries.single { it.id == existing.id }.notes)
            assertTrue(repo.state.value.settings.onboardingComplete)
            val reopened = JournalRepository(context)
            try { assertTrue(reopened.awaitLoaded().settings.onboardingComplete) } finally { reopened.close() }
            // Merge also cannot mark an unonboarded installation as onboarded from foreign settings.
            repo.saveSettings(repo.state.value.settings.copy(onboardingComplete = false))
            val completedIncoming = repo.state.value.copy(settings = repo.state.value.settings.copy(onboardingComplete = true))
            repo.restoreJournal(completedIncoming, replace = false)
            assertFalse(repo.state.value.settings.onboardingComplete)
            // Replace intentionally honors the saved onboarding setting.
            repo.restoreJournal(completedIncoming, replace = true)
            assertTrue(repo.state.value.settings.onboardingComplete)
        }
    }
    @Test fun archiveReplaceCreatesRecoveryAndRestoresPhotoBytesInPrivateStorage() = runBlocking {
        withJournal { repo ->
            val original = Entry(kind = EntryKind.FOOD, name = "Pre-restore fixture")
            repo.upsert(original)
            val photo = byteArrayOf(10, 20, 30, 40)
            val incoming = Entry(kind = EntryKind.FOOD, name = "Photo restore fixture", photoPaths = listOf("fixture.jpg"))
            val preview = JournalExports.previewRestore(JournalExports.backup(Journal(listOf(incoming)), PhotoReader { photo }))
            repo.restoreArchive(preview, replace = true)
            val recovery = requireNotNull(repo.lastRecoveryBackup)
            assertTrue(JournalExports.previewRestore(recovery.readBytes()).journal.entries.any { it.id == original.id })
            val path = repo.state.value.entries.single().photoPaths.single()
            assertTrue(File(path).canonicalPath.startsWith(context.filesDir.canonicalPath))
            assertArrayEquals(photo, File(path).readBytes())
            val corrupted = preview.copy(attachments = listOf(preview.attachments.single().copy(bytes = byteArrayOf(99))))
            val before = repo.state.value
            try { repo.restoreArchive(corrupted, true); fail("Corrupt photo accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.state.value)
        }
    }
    @Test fun concurrentRepositoryHandlesShareConnectionAndClosingOneKeepsOthersUsable() = runBlocking {
        withJournal { primary ->
            val handles = List(4) { JournalRepository(context) }
            try {
                withTimeout(30000) { coroutineScope { handles.map { handle -> async { handle.awaitLoaded() } }.awaitAll() } }
                val entries = handles.mapIndexed { index, _ -> Entry(kind = EntryKind.FOOD, name = "Concurrent handle $index") }
                withTimeout(30000) { coroutineScope { handles.mapIndexed { index, handle -> async { handle.upsert(entries[index]); assertTrue(handle.state.value.entries.any { it.id == entries[index].id }) } }.awaitAll() } }
                handles.first().close()
                val survivor = handles.last()
                val draftKey = "concurrent-${UUID.randomUUID()}"
                survivor.saveDraft(draftKey, "A surviving handle")
                withTimeout(30000) { primary.state.first { journal -> entries.all { entry -> journal.entries.any { it.id == entry.id } } && journal.drafts[draftKey] == "A surviving handle" } }
                val reopened = JournalRepository(context)
                try { reopened.awaitLoaded(); assertTrue(entries.all { entry -> reopened.state.value.entries.any { it.id == entry.id } }) } finally { reopened.close() }
            } finally { handles.forEach { it.close() } }
        }
    }

    @Test fun photoCleanupProtectsDurableDraftsSnapshotsAndActiveDraftPaths() = runBlocking {
        withJournal { repo ->
            val directory = File(context.filesDir, "photos/cleanup-${UUID.randomUUID()}").apply { check(mkdirs()) }
            val draftPhoto = File(directory, "draft.jpg").apply { writeBytes(byteArrayOf(1)) }
            val snapshotPhoto = File(directory, "snapshot.jpg").apply { writeBytes(byteArrayOf(2)) }
            val activePhoto = File(directory, "active.jpg").apply { writeBytes(byteArrayOf(3)) }
            val orphan = File(directory, "orphan.jpg").apply { writeBytes(byteArrayOf(4)) }
            val key = "editor:${UUID.randomUUID()}"
            val ingredient = Entry(kind = EntryKind.FOOD, name = "Photo ingredient", photoPaths = listOf(snapshotPhoto.absolutePath))
            val recipe = Recipes.create("Cleanup snapshot", listOf(ingredient), 1.0)
            val logged = Recipes.log(recipe, 1.0, "serving")
            try {
                repo.saveDraft(key, JournalCodec.toJson(mapOf("photos" to listOf(draftPhoto.absolutePath))))
                repo.upsert(logged)
                repo.purgeUnreferencedPhotos(0, setOf(activePhoto.absolutePath))
                assertTrue(draftPhoto.exists()); assertTrue(snapshotPhoto.exists()); assertTrue(activePhoto.exists()); assertFalse(orphan.exists())
            } finally { repo.clearDraft(key); repo.delete(setOf(logged.id)); directory.deleteRecursively() }
        }
    }

    @Test fun atomicMealEditAndUndoPersistAcrossRepositoryHandles() = runBlocking {
        withJournal { repo ->
            val meal = Entry(kind = EntryKind.MEAL, name = "Atomic meal")
            val removed = Entry(kind = EntryKind.FOOD, name = "Old food", mealId = meal.id)
            val originalMeal = meal.copy(componentIds = listOf(removed.id))
            repo.upsertAll(listOf(originalMeal, removed))
            val added = Entry(kind = EntryKind.FOOD, name = "New food", mealId = meal.id)
            val undo = repo.applyEdits(listOf(originalMeal.copy(componentIds = listOf(added.id)), added), setOf(removed.id))
            assertEquals(setOf(meal.id, removed.id), undo.map { it.id }.toSet())
            assertFalse(repo.state.value.entries.any { it.id == removed.id })
            val reopened = JournalRepository(context)
            try {
                reopened.awaitLoaded()
                assertEquals(listOf(added.id), reopened.state.value.entries.single { it.id == meal.id }.componentIds)
                reopened.applyEdits(undo, setOf(added.id))
                assertEquals(listOf(removed.id), reopened.state.value.entries.single { it.id == meal.id }.componentIds)
                assertEquals(meal.id, reopened.state.value.entries.single { it.id == removed.id }.mealId)
                assertFalse(reopened.state.value.entries.any { it.id == added.id })
            } finally { reopened.close() }
        }
    }
    @Test fun invalidAtomicEditRollsBackDeletionAndSurvivesReopen() = runBlocking {
        withJournal { repo ->
            val original = Entry(kind = EntryKind.FOOD, name = "Atomic rollback original")
            repo.upsert(original)
            val before = repo.state.value
            val invalid = Entry(kind = EntryKind.FOOD, name = "Invalid atomic child", mealId = "missing-${UUID.randomUUID()}")
            try { repo.applyEdits(listOf(invalid), setOf(original.id)); fail("Invalid atomic edit accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.state.value)
            val reopened = JournalRepository(context)
            try { reopened.awaitLoaded(); assertEquals(before.entries, reopened.state.value.entries) } finally { reopened.close() }
        }
    }

    @Test fun independentDeviceCredentialsAreEncryptedAndSurviveReopenWithoutRoomPlaintext() = runBlocking {
        withJournal { repo ->
            val originalSettings = repo.state.value.settings
            val gateway = "device-fixture-gateway-${UUID.randomUUID()}"
            val usda = "device-fixture-usda-${UUID.randomUUID()}"
            suspend fun verify(expectedGateway: String, expectedUsda: String) {
                val reopened = JournalRepository(context)
                try {
                    val settings = reopened.awaitLoaded().settings
                    assertEquals(expectedGateway, settings.gatewayToken); assertEquals(expectedUsda, settings.usdaApiKey)
                    val stored = JournalStorage.get(context).database.journalDao().meta().joinToString { it.value }
                    assertFalse(stored.contains(gateway)); assertFalse(stored.contains(usda))
                    val persistedSettings = JournalCodec.settings(JournalStorage.get(context).database.journalDao().meta().first { it.key == "settings" }.value)
                    assertEquals("", persistedSettings.gatewayToken); assertEquals("", persistedSettings.usdaApiKey)
                } finally { reopened.close() }
            }
            try {
                repo.saveSettings(originalSettings.copy(gatewayToken = gateway, usdaApiKey = usda))
                verify(gateway, usda)
                val prefs = context.getSharedPreferences("newleaf_device_credentials", android.content.Context.MODE_PRIVATE)
                val gatewayCiphertext = requireNotNull(prefs.getString("token", null))
                val usdaCiphertext = requireNotNull(prefs.getString("usda_api_key", null))
                assertTrue(gatewayCiphertext.contains(":")); assertTrue(usdaCiphertext.contains(":"))
                prefs.all.values.forEach { value -> assertFalse(value.toString().contains(gateway)); assertFalse(value.toString().contains(usda)) }
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                assertTrue(keyStore.containsAlias("newleaf_gateway_token_v1"))
                assertTrue(keyStore.containsAlias("newleaf_usda_api_key_v1"))
                repo.saveSettings(repo.state.value.settings.copy(usdaApiKey = "$usda-updated"))
                verify(gateway, "$usda-updated")
                repo.saveSettings(repo.state.value.settings.copy(gatewayToken = ""))
                verify("", "$usda-updated"); assertFalse(prefs.contains("token"))
                repo.saveSettings(repo.state.value.settings.copy(gatewayToken = gateway, usdaApiKey = ""))
                verify(gateway, ""); assertFalse(prefs.contains("usda_api_key"))
            } finally { repo.saveSettings(originalSettings) }
        }
    }

    @Test fun mergeAndReplaceJournalAndArchiveKeepBothLocalCredentialsAndRecoveryExcludesThem() = runBlocking {
        withJournal { repo ->
            val originalSettings = repo.state.value.settings
            val gateway = "restore-local-gateway-${UUID.randomUUID()}"
            val usda = "restore-local-usda-${UUID.randomUUID()}"
            val localUrl = "https://local-fixture.invalid"
            fun assertLocal() {
                assertEquals(gateway, repo.state.value.settings.gatewayToken)
                assertEquals(usda, repo.state.value.settings.usdaApiKey)
                assertEquals(localUrl, repo.state.value.settings.gatewayUrl)
            }
            try {
                repo.saveSettings(originalSettings.copy(gatewayUrl = localUrl, gatewayToken = gateway, usdaApiKey = usda))
                val incoming = Journal(settings = AppSettings(gatewayUrl = "https://foreign-fixture.invalid", gatewayToken = "foreign-token", usdaApiKey = "foreign-key"))
                for (replace in listOf(false, true)) {
                    repo.restoreJournal(incoming, replace); assertLocal()
                    val preview = JournalExports.previewRestore(JournalExports.backup(incoming))
                    repo.restoreArchive(preview, replace); assertLocal()
                    // Defense in depth for an in-memory preview bypassing the import parser.
                    repo.restoreArchive(preview.copy(journal = incoming), replace); assertLocal()
                }
                val recovery = JournalExports.previewRestore(requireNotNull(repo.lastRecoveryBackup).readBytes())
                assertEquals("", recovery.journal.settings.gatewayToken); assertEquals("", recovery.journal.settings.usdaApiKey)
                val reopened = JournalRepository(context)
                try {
                    val settings = reopened.awaitLoaded().settings
                    assertEquals(gateway, settings.gatewayToken); assertEquals(usda, settings.usdaApiKey)
                } finally { reopened.close() }
                repo.saveSettings(repo.state.value.settings.copy(gatewayToken = "", usdaApiKey = ""))
                repo.restoreJournal(incoming, replace = true)
                assertEquals("", repo.state.value.settings.gatewayToken); assertEquals("", repo.state.value.settings.usdaApiKey)
            } finally { repo.saveSettings(originalSettings) }
        }
    }
}
