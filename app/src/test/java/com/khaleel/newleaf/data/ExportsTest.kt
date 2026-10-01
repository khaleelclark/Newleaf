package com.khaleel.newleaf.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ExportsTest {
    private val fixtureTime = Instant.parse("2026-10-01T12:00:00Z")
    private fun at(entry: Entry, time: Instant = fixtureTime) = EventTimes.at(entry, time, ZoneId.of("UTC"))
    private fun journal(): Journal {
        val meal = at(Entry(id = "meal", kind = EntryKind.MEAL, name = "Fictional lunch", componentIds = listOf("food", "drink")))
        val food = at(Entry(id = "food", kind = EntryKind.FOOD, name = "Fictional food", mealId = "meal", notes = "=SUM(1,2)\nUnicode 🥗 and \"quotes\"", quantity = 150.0, unit = "g", nutrients = mapOf("energy_kcal" to 300.0, "sodium_mg" to null), tags = listOf("example"), sourceReference = SourceReference(provider = "fixture", itemId = "fixture:1", attribution = "Fictional test data", overrides = mapOf("energy_kcal" to "user"))))
        val drink = at(Entry(id = "drink", kind = EntryKind.DRINK, name = "Water", mealId = "meal", quantity = 16.0, unit = "fl oz", countsAsWater = true, nutrients = mapOf("energy_kcal" to 0.0)))
        return Journal(listOf(meal, food, drink), AppSettings(gatewayToken = "NEVER-EXPORT-THIS-TOKEN"), mapOf("private" to "DO-NOT-EXPORT-DRAFT"))
    }
    private fun unzip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val e = zip.nextEntry ?: break; files[e.name] = zip.readBytes(); zip.closeEntry() } }
        return files
    }
    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { out -> ZipOutputStream(out).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }; out.toByteArray() }
    @Test fun fullBackupRoundTripsExactNotesUnknownsAndStableIDs() {
        val original = journal()
        val backup = JournalExports.backup(original)
        val preview = JournalExports.previewRestore(backup)
        assertTrue(preview.isFullBackup)
        val restored = preview.journal.entries.associateBy { it.id }
        original.entries.forEach { entry -> assertTrue(JournalCodec.same(entry, restored.getValue(entry.id))) }
        assertEquals("", preview.journal.settings.gatewayToken); assertTrue(preview.journal.drafts.isEmpty())
        val files = unzip(backup)
        assertEquals(18, files.keys.count { it.endsWith(".csv") })
        files.values.forEach { bytes -> val text = String(bytes); assertFalse(text.contains("NEVER-EXPORT-THIS-TOKEN")); assertFalse(text.contains("DO-NOT-EXPORT-DRAFT")) }
        assertTrue(String(files.getValue("food_entries.csv")).contains("'=SUM"))
        assertTrue(String(files.getValue("journal.json")).contains("=SUM"))
    }
    @Test fun jsonTablesArePresentEvenWhenEmptyAndNutrientsHaveUnitsStatuses() {
        val root = JsonParser.parseString(JournalExports.json(journal())).asJsonObject
        listOf("meals", "food_entries", "drink_entries", "feeling_checkins", "activities", "daily_steps", "weight_entries", "daily_context", "custom_foods", "favorites", "saved_meals", "recipe_versions", "attachment_manifest", "daily_summaries").forEach { assertTrue(root[it].isJsonArray) }
        val sodium = root["food_entries"].asJsonArray[0].asJsonObject["nutrients"].asJsonArray.first { it.asJsonObject["code"].asString == "sodium_mg" }.asJsonObject
        assertTrue(sodium["value"].isJsonNull); assertEquals("mg", sodium["unit"].asString); assertEquals("unknown", sodium["status"].asString)
        assertFalse(root["settings"].asJsonObject.has("gatewayToken"))
        assertEquals("2026-10-01", root["daily_summaries"].asJsonArray[0].asJsonObject["date"].asString)
    }
    @Test fun rangeIncludesYesterdayMealContextButExcludesItFromTotals() {
        val meal = at(Entry(id = "yesterday", kind = EntryKind.MEAL, name = "Dinner"), Instant.parse("2026-09-30T23:00:00Z"))
        val food = at(Entry(id = "old-food", kind = EntryKind.FOOD, name = "Dinner food", mealId = meal.id, nutrients = mapOf("energy_kcal" to 500.0)), Instant.parse("2026-09-30T23:00:00Z"))
        val feeling = at(Entry(id = "feeling", kind = EntryKind.CHECKIN, name = "Morning feeling", mealId = meal.id, tags = listOf("satisfied")))
        val today = at(Entry(id = "today", kind = EntryKind.FOOD, name = "Breakfast", nutrients = mapOf("energy_kcal" to 300.0)))
        val exported = JournalExports.json(Journal(listOf(meal.copy(componentIds = listOf(food.id)), food, feeling, today)), ExportOptions("2026-10-01", "2026-10-01"))
        val preview = JournalExports.previewRestore(exported.toByteArray())
        assertEquals(2, preview.selectedCount); assertEquals(2, preview.contextCount)
        val root = JsonParser.parseString(exported).asJsonObject
        assertEquals(300.0, root["daily_summaries"].asJsonArray[0].asJsonObject["nutrients"].asJsonObject["energy_kcal"].asJsonObject["knownSum"].asDouble, 0.0)
    }
    @Test fun selectedMealIncludesLaterCheckInAsContext() {
        val meal = at(Entry(id = "meal", kind = EntryKind.MEAL, name = "Meal"))
        val later = at(Entry(id = "later", kind = EntryKind.CHECKIN, name = "Later", mealId = meal.id), Instant.parse("2026-10-02T08:00:00Z"))
        val preview = JournalExports.previewRestore(JournalExports.json(Journal(listOf(meal, later)), ExportOptions("2026-10-01", "2026-10-01")).toByteArray())
        assertEquals(1, preview.selectedCount); assertEquals(1, preview.contextCount)
    }
    @Test fun notesExclusionAlsoRemovesNestedDescriptions() {
        val ingredient = at(Entry(kind = EntryKind.FOOD, name = "Ingredient", notes = "PRIVATE-NOTE", originalText = "PRIVATE-DESCRIPTION"))
        val recipe = Recipes.create("Recipe", listOf(ingredient), 2.0)
        val logged = Recipes.log(recipe, 1.0, "serving", fixtureTime, ZoneId.of("UTC"))
        val json = JournalExports.json(Journal(listOf(logged)), ExportOptions(includeNotes = false))
        assertFalse(json.contains("PRIVATE-NOTE")); assertFalse(json.contains("PRIVATE-DESCRIPTION"))
        assertTrue(JsonParser.parseString(json).asJsonObject["recipe_versions"].asJsonArray.size() > 0)
    }
    @Test fun photoBackupRoundTripsBytesAndDigest() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val entry = at(Entry(kind = EntryKind.FOOD, name = "Photographed fixture", photoPaths = listOf("/private/photo.jpg")))
        val backup = JournalExports.backup(Journal(listOf(entry)), PhotoReader { bytes })
        val restored = JournalExports.previewRestore(backup)
        assertEquals(1, restored.attachments.size)
        assertArrayEquals(bytes, restored.attachments.single().bytes)
        assertEquals(JournalExports.sha256(bytes), restored.attachments.single().manifest.sha256)
        assertFalse(String(unzip(backup).getValue("journal.json")).contains("/private/photo.jpg"))
        val again = JournalExports.backup(restored.journal, PhotoReader { restored.attachments.single().bytes })
        assertArrayEquals(bytes, JournalExports.previewRestore(again).attachments.single().bytes)
    }
    @Test fun missingPhotosFailCompleteBackupAndPlainExportWarns() {
        val entry = at(Entry(kind = EntryKind.FOOD, name = "Photo", photoPaths = listOf("/private/photo.jpg")))
        assertThrows(IllegalArgumentException::class.java) { JournalExports.backup(Journal(listOf(entry))) }
        val partial = JournalExports.previewRestore(JournalExports.json(Journal(listOf(entry))).toByteArray())
        assertTrue(partial.warnings.isNotEmpty()); assertTrue(partial.journal.entries.single().photoPaths.isEmpty())
    }
    @Test fun tamperedZipMissingMembersAndTraversalAreRejected() {
        val backup = JournalExports.backup(journal())
        val files = unzip(backup)
        files["journal.json"] = String(files.getValue("journal.json")).replace("Fictional lunch", "Changed lunch").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { JournalExports.previewRestore(zip(files)) }
        assertThrows(IllegalArgumentException::class.java) { JournalExports.previewRestore(zip(mapOf("../escape" to byteArrayOf(1)))) }
        files.remove("journal.json")
        assertThrows(IllegalArgumentException::class.java) { JournalExports.previewRestore(zip(files)) }
    }
    @Test fun futureSchemaAndMissingReferencesAndFalseSummariesAreRejected() {
        val root = JsonParser.parseString(JournalExports.json(journal())).asJsonObject
        root.addProperty("schema_version", "2.0")
        assertThrows(IllegalArgumentException::class.java) { JournalExports.previewRestore(root.toString().toByteArray()) }
        root.addProperty("schema_version", "1.0")
        root["records"].asJsonArray[1].asJsonObject.addProperty("mealId", "missing")
        assertThrows(IllegalArgumentException::class.java) { JournalExports.previewRestore(root.toString().toByteArray()) }
        val summary = JsonParser.parseString(JournalExports.json(journal())).asJsonObject
        summary["daily_summaries"].asJsonArray[0].asJsonObject.addProperty("steps", 999)
        assertThrows(IllegalArgumentException::class.java) { JournalExports.previewRestore(summary.toString().toByteArray()) }
    }
    @Test fun unknownRecordAndDocumentFieldsSurviveRestoreReexport() {
        val root = JsonParser.parseString(JournalExports.json(journal())).asJsonObject
        root.addProperty("future_document_field", "retain-me")
        root["records"].asJsonArray[0].asJsonObject.addProperty("future_record_field", "retain-record")
        val preview = JournalExports.previewRestore(root.toString().toByteArray())
        val again = JsonParser.parseString(JournalExports.json(preview.journal)).asJsonObject
        assertEquals("retain-me", again["extensions"].asJsonObject["future_document_field"].asString)
        assertEquals("retain-record", again["records"].asJsonArray[0].asJsonObject["extensions"].asJsonObject["future_record_field"].asString)
    }
    @Test fun spreadsheetEscapingIsReversibleAndNumericZeroIsNumeric() {
        assertEquals("\"'=1+1\"", JournalExports.csvCell("=1+1"))
        assertEquals("\"'  @cmd\"", JournalExports.csvCell("  @cmd"))
        assertEquals("\"''literal\"", JournalExports.csvCell("'literal"))
        assertEquals("\"0\"", JournalExports.csvCell(0.0)); assertEquals("\"\"", JournalExports.csvCell(null))
        assertEquals("\"comma,\n\"\"quotes\"\"\"", JournalExports.csvCell("comma,\n\"quotes\""))
    }
    @Test fun sourceRestrictionsBlockExportsRatherThanSilentlyOmitNutrition() {
        val entry = at(Entry(kind = EntryKind.FOOD, name = "Restricted", sourceReference = SourceReference(exportAllowed = false)))
        assertThrows(IllegalArgumentException::class.java) { JournalExports.json(Journal(listOf(entry))) }
    }
    @Test fun dateOnlyStepDoesNotInventExportEventTime() {
        val entry = at(Entry(kind = EntryKind.STEPS, name = "Steps", steps = 0))
        val root = JsonParser.parseString(JournalExports.json(Journal(listOf(entry)))).asJsonObject
        assertFalse(root["daily_steps"].asJsonArray[0].asJsonObject.has("occurredAt"))
        val csv = String(unzip(JournalExports.csvZip(Journal(listOf(entry)))).getValue("daily_steps.csv"))
        assertTrue(csv.contains("\"${entry.id}\",\"\",\"\""))
    }
    @Test fun unknownSourceAndEvidenceFieldsAreRetainedAndDefaultsRestored() {
        val root = JsonParser.parseString(JournalExports.json(journal())).asJsonObject
        val food = root["records"].asJsonArray.first { it.asJsonObject["id"].asString == "food" }.asJsonObject
        food["sourceReference"].asJsonObject.addProperty("future_source", "source-extra")
        food.add("nutrientEvidence", JsonParser.parseString("{\"energy_kcal\":{\"future_evidence\":\"evidence-extra\"}}"))
        val decoded = JournalCodec.decodeEntry(food)
        assertEquals("source-extra", decoded.sourceReference!!.extensions["future_source"].asString)
        assertEquals("evidence-extra", decoded.nutrientEvidence["energy_kcal"]!!.extensions["future_evidence"].asString)
        assertEquals("known", decoded.nutrientEvidence["energy_kcal"]!!.status)
    }
    @Test fun settingsStringAndExtensionTokensCannotExposeDeviceCredentials() {
        val settings = AppSettings(gatewayToken = "TEST-DEVICE-SECRET", extensions = JsonParser.parseString("{\"gateway_token\":\"TEST-DEVICE-SECRET\"}").asJsonObject)
        assertFalse(settings.toString().contains("TEST-DEVICE-SECRET"))
        assertFalse(JournalExports.json(Journal(settings = settings)).contains("TEST-DEVICE-SECRET"))
    }

    @Test fun bothDeviceCredentialsAreExcludedFromEveryExportAndPersistableSettings() {
        val settings = AppSettings(gatewayToken = "FIXTURE-GATEWAY-SECRET", usdaApiKey = "FIXTURE-USDA-SECRET",
            extensions = JsonParser.parseString("""{"gateway_token":"FIXTURE-GATEWAY-SECRET","nested":[{"usda_api_key":"FIXTURE-USDA-SECRET"}],"secretRecipe":"keep-recipe","tokenCount":4,"publicApiKeyLabel":"keep-label"}""").asJsonObject)
        val journal = journal().copy(settings = settings,
            extensions = JsonParser.parseString("""{"usdaApiKey":"FIXTURE-USDA-SECRET","future_document":"retain"}""").asJsonObject)
        val texts = listOf(JournalExports.json(journal), JournalExports.markdown(journal),
            JournalCodec.toJson(CredentialSettings.withoutCredentials(settings)), settings.toString()) +
            listOf(JournalExports.csvZip(journal), JournalExports.backup(journal)).flatMap { archive -> unzip(archive).values.map { String(it, Charsets.UTF_8) } }
        texts.forEach { text ->
            assertFalse(text.contains("FIXTURE-GATEWAY-SECRET")); assertFalse(text.contains("FIXTURE-USDA-SECRET"))
        }
        val exportedSettings = JsonParser.parseString(JournalExports.json(journal)).asJsonObject["settings"].asJsonObject
        assertFalse(exportedSettings.has("gatewayToken")); assertFalse(exportedSettings.has("usdaApiKey"))
        val extensions = exportedSettings["extensions"].asJsonObject
        assertFalse(extensions.has("gateway_token"))
        assertFalse(extensions["nested"].asJsonArray.single().asJsonObject.has("usda_api_key"))
        assertEquals("keep-recipe", extensions["secretRecipe"].asString)
        assertEquals(4, extensions["tokenCount"].asInt)
        assertEquals("keep-label", extensions["publicApiKeyLabel"].asString)
        // Sanitizing must not clear the live credential or mutate caller-owned extensions.
        assertEquals("FIXTURE-USDA-SECRET", settings.usdaApiKey)
        assertTrue(settings.extensions.has("gateway_token"))
    }

    @Test fun importedJsonAndZipCredentialFieldsAreDiscardedBeforeDecoding() {
        val files = unzip(JournalExports.backup(journal()))
        val root = JsonParser.parseString(String(files.getValue("journal.json"), Charsets.UTF_8)).asJsonObject
        root["settings"].asJsonObject.apply {
            addProperty("gatewayToken", "FOREIGN-GATEWAY-SECRET")
            addProperty("usdaApiKey", "FOREIGN-USDA-SECRET")
            add("extensions", JsonParser.parseString("""{"usda_api_key":"FOREIGN-USDA-SECRET","nested":[{"gateway-token":"FOREIGN-GATEWAY-SECRET"}],"future_setting":"retain"}"""))
        }
        root.addProperty("usdaApiKey", "FOREIGN-USDA-SECRET")
        root["extensions"].asJsonObject.addProperty("gateway_token", "FOREIGN-GATEWAY-SECRET")
        val json = root.toString().toByteArray(Charsets.UTF_8)
        files["journal.json"] = json
        files["integrity.json"] = com.google.gson.JsonObject().apply {
            files.filterKeys { it != "integrity.json" }.forEach { (name, bytes) -> addProperty(name, JournalExports.sha256(bytes)) }
        }.toString().toByteArray(Charsets.UTF_8)
        listOf(json, zip(files)).forEach { input ->
            val preview = JournalExports.previewRestore(input)
            assertEquals("", preview.journal.settings.gatewayToken)
            assertEquals("", preview.journal.settings.usdaApiKey)
            val sanitized = JournalCodec.toJson(preview.journal)
            assertFalse(sanitized.contains("FOREIGN-GATEWAY-SECRET")); assertFalse(sanitized.contains("FOREIGN-USDA-SECRET"))
            assertEquals("retain", preview.journal.settings.extensions["future_setting"].asString)
        }
    }

    @Test fun legacySettingsDefaultUsdaKeyToBlankAndRestoreKeepsOnlyLocalCredentials() {
        val legacy = JournalCodec.settings("""{"theme":"dark","gatewayToken":"legacy-device-token"}""")
        assertEquals("", legacy.usdaApiKey)
        assertEquals("legacy-device-token", legacy.gatewayToken)
        val incoming = AppSettings(theme = "dark", gatewayUrl = "https://foreign.invalid", gatewayToken = "foreign-token", usdaApiKey = "foreign-key")
        val local = AppSettings(gatewayUrl = "https://local.invalid", gatewayToken = "local-token", usdaApiKey = "local-key")
        val restored = CredentialSettings.restore(incoming, local)
        assertEquals("dark", restored.theme)
        assertEquals("local-token", restored.gatewayToken); assertEquals("local-key", restored.usdaApiKey)
        assertEquals("https://local.invalid", restored.gatewayUrl)
        val blank = CredentialSettings.restore(incoming, AppSettings())
        assertEquals("", blank.gatewayToken); assertEquals("", blank.usdaApiKey)
        assertEquals(incoming.gatewayUrl, blank.gatewayUrl)
        assertEquals("local-key", CredentialSettings.restore(incoming, local.copy(gatewayToken = "")).usdaApiKey)
    }

    @Test fun partialCompositionCoverageIsExportedAndRoundTripsSeparatelyFromEstimation() {
        val base = at(Entry(kind = EntryKind.DRINK, name = "Coffee", nutrients = mapOf("energy_kcal" to 0.0)))
        val drink = Nutrition.composeDrink(base, listOf(Entry(kind = EntryKind.FOOD, name = "Milk", nutrients = mapOf("energy_kcal" to 50.0)), Entry(kind = EntryKind.FOOD, name = "Unknown addition")))
        val journal = Journal(listOf(drink))
        val root = JsonParser.parseString(JournalExports.json(journal)).asJsonObject
        val coverage = root["daily_summaries"].asJsonArray.single().asJsonObject["nutrients"].asJsonObject["energy_kcal"].asJsonObject
        assertEquals("PARTIAL", coverage["completeness"].asString)
        assertEquals(50.0, coverage["knownSum"].asDouble, 0.0)
        assertEquals(1, coverage["partialContributors"].asInt)
        assertEquals(0, coverage["estimatedContributors"].asInt)
        val restored = JournalExports.previewRestore(JournalExports.backup(journal)).journal
        assertEquals(Completeness.PARTIAL, Summaries.day(restored.entries, drink.localDate).nutrients["energy_kcal"]!!.completeness)
        assertTrue(String(unzip(JournalExports.csvZip(journal)).getValue("daily_summaries.csv")).contains("partial"))
    }
    @Test fun nestedRecipeAndCompositionSourcesEnforceExportAndSnapshotRights() {
        val restricted = at(Entry(kind = EntryKind.FOOD, name = "Restricted ingredient", sourceReference = SourceReference(exportAllowed = false)))
        val recipe = Recipes.create("Nested source", listOf(restricted), 1.0)
        val logged = Recipes.log(recipe, 1.0, "serving", fixtureTime, ZoneId.of("UTC"))
        assertThrows(IllegalArgumentException::class.java) { JournalExports.json(Journal(listOf(logged))) }
        val drink = Nutrition.composeDrink(at(Entry(kind = EntryKind.DRINK, name = "Drink")), listOf(restricted))
        assertThrows(IllegalArgumentException::class.java) { JournalExports.json(Journal(listOf(drink))) }
        val prohibited = restricted.copy(sourceReference = SourceReference(snapshotAllowed = false))
        assertThrows(IllegalArgumentException::class.java) { JournalValidation.entry(recipe.copy(ingredientSnapshots = listOf(prohibited))) }
    }

    @Test fun offNutritionSubsetIsIndependentLicensedAndExcludesPrivateJournalContent() {
        val source = SourceReference(provider = "Open Food Facts", itemId = "off:12345678", barcode = "12345678", attribution = "Open Food Facts contributors", license = "ODbL-1.0 ${OpenFoodFactsExports.DATABASE_LICENSE_URL}; DbCL-1.0 ${OpenFoodFactsExports.CONTENTS_LICENSE_URL}", overrides = mapOf("protein_g" to "PRIVATE-OVERRIDE"))
        val entry = at(Entry(kind = EntryKind.FOOD, name = "PRIVATE-PERSONAL-LABEL", sourceReference = source,
            notes = "PRIVATE-NOTES", originalText = "PRIVATE-DESCRIPTION", quantity = 150.0, unit = "g",
            nutrients = mapOf("energy_kcal" to 300.0, "protein_g" to 10.0), basisQuantity = 100.0, basisUnit = "g", metadata = mapOf("nutrition_scale" to "1.5")))
        val weight = at(Entry(kind = EntryKind.WEIGHT, name = "PRIVATE-WEIGHT", weightKg = 68.0))
        val journal = Journal(listOf(entry, weight), drafts = mapOf("editor" to "PRIVATE-DRAFT"))
        JournalValidation.journal(journal)
        val archive = unzip(JournalExports.csvZip(journal))
        val subset = String(archive.getValue(OpenFoodFactsExports.SUBSET_FILE))
        val notice = String(archive.getValue(OpenFoodFactsExports.NOTICE_FILE))
        assertFalse(subset.contains("PRIVATE-")); assertFalse(subset.contains(entry.id)); assertFalse(subset.contains("weightKg")); assertFalse(subset.contains("photoPaths"))
        assertTrue(notice.contains(OpenFoodFactsExports.DATABASE_LICENSE_URL)); assertTrue(notice.contains(OpenFoodFactsExports.CONTENTS_LICENSE_URL))
        val record = JsonParser.parseString(subset).asJsonObject["nutrition_snapshots"].asJsonArray.single().asJsonObject
        assertEquals(200.0, record["nutrients"].asJsonArray.single().asJsonObject["value"].asDouble, 0.0)
        assertEquals("source_reference", record["basis_type"].asString)
        val root = JsonParser.parseString(JournalExports.json(journal)).asJsonObject
        assertTrue(root["source_notices"].asJsonArray.single().asJsonObject["database_license_url"].asString.contains("odbl"))
        assertTrue(JournalExports.markdown(journal).contains(OpenFoodFactsExports.CONTENTS_LICENSE_URL))
        assertEquals(entry.notes, JournalExports.previewRestore(JournalExports.csvZip(journal)).journal.entries.first { it.id == entry.id }.notes)
    }
    @Test fun offSubsetExcludesManualDrinkAdditionsAndRetainsNestedOffIngredientSources() {
        val source = SourceReference(provider = "Open Food Facts", itemId = "off:12345678", barcode = "12345678")
        val base = at(Entry(kind = EntryKind.DRINK, name = "Catalog drink", sourceReference = source,
            nutrients = mapOf("energy_kcal" to 300.0, "sodium_mg" to null, "protein_g" to 10.0),
            nutrientEvidence = mapOf("energy_kcal" to NutrientEvidence(status = "estimated", origin = "Open Food Facts", providerCode = "energy-kcal_100g", unit = "kcal"),
                "sodium_mg" to NutrientEvidence(status = "unknown", origin = "Open Food Facts", providerCode = "sodium_100g", unit = "mg"),
                "protein_g" to NutrientEvidence(origin = "user_override")),
            basisQuantity = 100.0, basisUnit = "ml", metadata = mapOf("nutrition_scale" to "1.5")))
        val addition = Entry(kind = EntryKind.FOOD, name = "PRIVATE manual addition", notes = "PRIVATE addition note",
            nutrients = mapOf("energy_kcal" to 50.0, "sodium_mg" to 20.0, "fat_g" to 9.0))
        val composed = Nutrition.composeDrink(base, listOf(addition))
        assertEquals(350.0, composed.nutrients.getValue("energy_kcal")!!, 0.0)
        assertEquals("composition", composed.nutrientEvidence.getValue("energy_kcal").origin)
        val journal = Journal(listOf(composed))
        val subsets = listOf(
            JsonParser.parseString(JournalExports.json(journal)).asJsonObject["open_food_facts_nutrition"].asJsonObject,
            JsonParser.parseString(String(unzip(JournalExports.csvZip(journal)).getValue(OpenFoodFactsExports.SUBSET_FILE))).asJsonObject,
            JsonParser.parseString(String(unzip(JournalExports.backup(journal)).getValue(OpenFoodFactsExports.SUBSET_FILE))).asJsonObject)
        subsets.forEach { subset ->
            val record = subset["nutrition_snapshots"].asJsonArray.single().asJsonObject
            val nutrients = record["nutrients"].asJsonArray.associate { it.asJsonObject["code"].asString to it.asJsonObject }
            assertEquals(setOf("energy_kcal", "sodium_mg"), nutrients.keys)
            assertEquals(200.0, nutrients.getValue("energy_kcal")["value"].asDouble, 0.0)
            assertEquals("estimated", nutrients.getValue("energy_kcal")["status"].asString)
            assertEquals("energy-kcal_100g", nutrients.getValue("energy_kcal")["provider_code"].asString)
            assertTrue(nutrients.getValue("sodium_mg")["value"].isJsonNull)
            assertEquals("unknown", nutrients.getValue("sodium_mg")["status"].asString)
            assertEquals("sodium_100g", nutrients.getValue("sodium_mg")["provider_code"].asString)
            assertFalse(subset.toString().contains("PRIVATE")); assertFalse(subset.toString().contains(addition.id))
        }
        val restored = JournalExports.previewRestore(JournalExports.backup(journal)).journal.entries.single()
        assertEquals(composed.metadata["drink_base_evidence"], restored.metadata["drink_base_evidence"])
        assertEquals(350.0, restored.nutrients.getValue("energy_kcal")!!, 0.0)
        assertEquals(addition.notes, restored.composition.single().notes)
        val nested = Recipes.log(Recipes.create("PRIVATE recipe", listOf(base.copy(kind = EntryKind.FOOD)), 1.0), 1.0, "serving", fixtureTime, ZoneId.of("UTC"))
        assertTrue(OpenFoodFactsExports.hasRecords(OpenFoodFactsExports.subset(listOf(nested))))
    }

    @Test fun offSubsetUsesUpdatedPortionScaleAndDoesNotInventLegacyCompositionEvidence() {
        val reference = at(Entry(kind = EntryKind.DRINK, name = "Catalog drink", sourceReference = SourceReference(provider = "off", itemId = "off:12345678"),
            nutrients = mapOf("energy_kcal" to 100.0), basisQuantity = 100.0, basisUnit = "ml"))
        val consumed = Nutrition.scalePortion(reference, 150.0, "ml")
        val edited = consumed.copy(quantity = 300.0, nutrients = mapOf("energy_kcal" to 300.0), metadata = consumed.metadata + ("nutrition_scale" to "3.0"))
        val snapshots = OpenFoodFactsExports.subset(listOf(consumed, edited))["nutrition_snapshots"].asJsonArray
        assertEquals(1, snapshots.size())
        assertEquals(100.0, snapshots.single().asJsonObject["nutrients"].asJsonArray.single().asJsonObject["value"].asDouble, 0.0)
        val composed = Nutrition.composeDrink(consumed, listOf(Entry(kind = EntryKind.FOOD, name = "Manual addition", nutrients = mapOf("energy_kcal" to 50.0))))
        val legacy = composed.copy(metadata = composed.metadata - "drink_base_evidence")
        val nutrient = OpenFoodFactsExports.subset(listOf(legacy))["nutrition_snapshots"].asJsonArray.single().asJsonObject["nutrients"].asJsonArray.single().asJsonObject
        assertEquals(100.0, nutrient["value"].asDouble, 0.0)
        assertTrue(nutrient["provider_code"].isJsonNull)
    }

    @Test fun heifAttachmentPreservesOriginalFormatBytesAndIntegrity() {
        val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 104, 101, 105, 102)
        val entry = at(Entry(kind = EntryKind.FOOD, name = "HEIF fixture", photoPaths = listOf("/private/original.HEIF")))
        val restored = JournalExports.previewRestore(JournalExports.backup(Journal(listOf(entry)), PhotoReader { bytes }))
        val attachment = restored.attachments.single()
        assertTrue(attachment.manifest.fileName.endsWith(".heif"))
        assertEquals("image/heif", attachment.manifest.mimeType)
        assertEquals(JournalExports.sha256(bytes), attachment.manifest.sha256)
        assertArrayEquals(bytes, attachment.bytes)
    }

}
