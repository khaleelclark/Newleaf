package com.khaleel.newleaf.nutrition

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.khaleel.newleaf.data.AppSettings
import com.khaleel.newleaf.data.EntryKind
import com.khaleel.newleaf.data.PortionDefinition
import com.khaleel.newleaf.data.SourceReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

class NutritionClientTest {
    private val settings = AppSettings(gatewayUrl = "https://fixture.invalid", gatewayToken = "private-device-fixture-token")
    private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject
    private class Fake(var response: NutritionResponse) : NutritionTransport {
        val requests = mutableListOf<NutritionRequest>()
        override suspend fun execute(request: NutritionRequest): NutritionResponse { requests.add(request); return response }
    }
    private fun food(branded: Boolean = false, unit: String = "g"): String = """{
      "fdcId":123,"description":"Cooked beans","dataType":"${if(branded) "Branded" else "Foundation"}","servingSize":150,"servingSizeUnit":"$unit",
      "foodNutrients":[{"nutrient":{"id":1008,"unitName":"kcal"},"amount":200},
      {"nutrient":{"id":1003,"unitName":"g"},"amount":10},
      {"nutrient":{"id":1093,"unitName":"mg"},"amount":0},
      {"nutrient":{"id":1087,"unitName":"mg"},"amount":20}],
      "foodPortions":[{"amount":1,"gramWeight":150,"modifier":"cup"}]}"""
    private fun off(per: String = "100ml", source: String = "packaging", extra: String = ""): String = """{
      "status":"success","product":{"code":"3017620422003","product_name":"Orange juice","brands":"Fixture",
      "countries_tags":["en:united-states"],"categories_tags":["en:beverages"],"nutrition":{
      "aggregated_set":{"per":"100ml","nutrients":{"energy-kcal":{"value":999,"unit":"kcal"},"fiber":{"value":9,"unit":"g"}}},
      "input_sets":[{"source":"$source","preparation":"as_sold","per":"$per",$extra
      "nutrients":{"energy-kcal":{"value":40,"unit":"kcal"},"proteins":{"value":0,"unit":"g"},"salt":{"value":0.1,"unit":"g"},"vitamin-c":{"value":3,"unit":"mg"}}}]}}}"""
    private fun nx(): String = """{"foods":[{"food_name":"milk","serving_qty":2,"serving_unit":"cup","serving_weight_grams":488,"nf_calories":240,"nf_total_fat":8,"nf_protein":16,
      "full_nutrients":[{"attr_id":203,"value":16},{"attr_id":301,"value":500}],
      "alt_measures":[{"measure":"tablespoon","qty":1,"serving_weight":15.25}]}]}"""
    private fun descriptionItem(input: String, quantity: Double?, unit: String, matched: Boolean = true,
        needsDetails: Boolean = false, recognized: Boolean = true): String = JsonObject().apply {
        addProperty("input_text", input); addProperty("query", input); addProperty("unit_hint", unit)
        addProperty("quantity_hint", quantity); addProperty("quantity_estimated", true)
        addProperty("unit_recognized", recognized); addProperty("match_estimated", true)
        addProperty("needs_details", needsDetails); addProperty("match_status", if (matched) "provisional" else "no_match")
        add("food", if (matched) json(food()) else com.google.gson.JsonNull.INSTANCE)
    }.toString()
    private fun described(original: String, vararg items: String): String = JsonObject().apply {
        addProperty("mode", "usda_assisted"); addProperty("review_required", true); addProperty("original_text", original)
        add("foods", com.google.gson.JsonArray().apply { items.forEach { add(json(it)) } })
    }.toString()

    @Test fun freeDescriptionMapsCommaMealFractionsWithReviewAndOriginalInput() = runBlocking<Unit> {
        val input = "  1/2 cup beans, 100g rice  "
        val fake = Fake(NutritionResponse(200, described(input,
            descriptionItem("1/2 cup beans", .5, "cup"), descriptionItem("100g rice", 100.0, "g"))))
        val client = NutritionClient(settings, fake)
        val candidates = client.describe(input)
        assertEquals(2, candidates.size)
        assertEquals("https://fixture.invalid/v1/describe", fake.requests.single().url)
        assertEquals("POST", fake.requests.single().method)
        assertEquals(input, json(fake.requests.single().body!!).get("text").asString)
        assertEquals(60000, fake.requests.single().readTimeoutMillis)
        val c = candidates.first()
        assertEquals("USDA FoodData Central", c.source)
        assertEquals("usda:123", c.sourceId)
        assertEquals(.5, c.quantity!!, 0.0)
        assertEquals("cup", c.unit)
        assertEquals(input, c.originalText)
        assertEquals("1/2 cup beans", c.metadata["description_item_input"])
        assertEquals("true", c.metadata["review_required"])
        assertTrue(c.estimated)
        assertEquals("unreviewed", c.matchStatus)
        assertEquals(150.0, c.toEntry().nutrients["energy_kcal"]!!, 1e-9)
        assertEquals("estimated", c.toEntry().nutrientEvidence["energy_kcal"]!!.status)
        assertSame(c, client.details(c))
        assertEquals(1, fake.requests.size)
        assertEquals(200.0, candidates[1].toEntry().nutrients["energy_kcal"]!!, 1e-9)
    }

    @Test fun freeDescriptionUnknownAndUnspecifiedPortionsStayUnknown() = runBlocking<Unit> {
        val input = "2 glugs juice, beans"
        val fake = Fake(NutritionResponse(200, described(input,
            descriptionItem("2 glugs juice", 2.0, "glugs", recognized = false), descriptionItem("beans", null, "serving"))))
        val c = NutritionClient(settings, fake).describe(input)
        assertEquals("glugs", c[0].unit)
        assertEquals("false", c[0].metadata["unit_recognized"])
        assertTrue(c[0].toEntry().nutrients.values.all { it == null })
        assertNull(c[0].toEntry().massGrams)
        assertNull(c[1].quantity)
        assertTrue(c[1].toEntry().nutrients.values.all { it == null })
    }

    @Test fun freeDescriptionPartialNoMatchRemainsEditableUnknownCandidate() = runBlocking<Unit> {
        val input = "beans, unmatched"
        val fake = Fake(NutritionResponse(200, described(input,
            descriptionItem("beans", 100.0, "g"), descriptionItem("unmatched", null, "serving", matched = false))))
        val client = NutritionClient(settings, fake)
        val c = client.describe(input)
        assertEquals(2, c.size)
        assertEquals("no_match", c[1].matchStatus)
        assertNull(c[1].sourceId)
        assertEquals(NutrientBasis.UNKNOWN, c[1].basis)
        assertEquals("unmatched", c[1].name)
        assertEquals(input, c[1].toEntry().originalText)
        assertTrue(c[1].toEntry(100.0, "g").nutrients.values.all { it == null })
        assertSame(c[1], client.details(c[1]))
        assertEquals(1, fake.requests.size)
    }

    @Test fun freeDescriptionFallbackDetailsPreserveHintsAndFullOriginal() = runBlocking<Unit> {
        val input = "1/2 cup beans"
        val fake = Fake(NutritionResponse(200, described(input, descriptionItem(input, .5, "cup", needsDetails = true))))
        val client = NutritionClient(settings, fake)
        val c = client.describe(input).single()
        fake.response = NutritionResponse(200, food())
        val d = client.details(c)
        assertFalse(d.needsDetails)
        assertEquals(.5, d.quantity!!, 0.0)
        assertEquals("cup", d.unit)
        assertEquals(input, d.originalText)
        assertTrue(d.estimated)
        assertEquals("true", d.metadata["review_required"])
        assertEquals(150.0, d.toEntry().nutrients["energy_kcal"]!!, 1e-9)
    }

    @Test fun freeDescriptionRejectsExcessItemsAndLegacyPaidResponses() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, nx()))
        val client = NutritionClient(settings, fake)
        expect(NutritionError.INVALID_REQUEST) { client.describe(List(9) { "rice" }.joinToString(",")) }
        assertTrue(fake.requests.isEmpty())
        expect(NutritionError.MALFORMED_RESPONSE) { client.describe("rice") }
        assertTrue(fake.requests.all { it.url.endsWith("/v1/describe") })
        fake.response = NutritionResponse(403, """{"error":{"code":"free_only"}}""")
        expect(NutritionError.FREE_ONLY) { client.describe("rice") }
        fake.response = NutritionResponse(200, described("rice", *Array(9) { descriptionItem("rice", null, "serving") }))
        expect(NutritionError.MALFORMED_RESPONSE) { client.describe("rice") }
    }

    @Test fun surveyQuantityPrefixedPortionLabelUsesExplicitGramWeight() {
        val root = json(food()).apply {
            add("foodPortions", JsonParser.parseString("""[{"amount":1,"gramWeight":150,"portionDescription":"1 cup","measureUnit":{"name":"undetermined"}}]"""))
        }
        val c = ProviderMappers.usda(root)
        assertEquals("cup", c.portions.single { it.label == "cup" }.label)
        assertEquals(150.0, c.toEntry(.5, "cup").nutrients["energy_kcal"]!!, 1e-9)
    }

    @Test fun directPersonalUsdaSearchDetailsAndStatusUseOnlyHeaderKey() = runBlocking<Unit> {
        val key = "personal-usda-fixture-key"
        val personal = settings.copy(usdaApiKey = key, gatewayUrl = "http://invalid-unused", gatewayToken = "unused-token")
        val fake = Fake(NutritionResponse(200, """{"foods":[${food()}]}"""))
        val client = NutritionClient(personal, fake)
        assertTrue(client.status().usda.enabled)
        assertFalse(client.status().nutritionix.enabled)
        assertTrue(fake.requests.isEmpty()) // status means configured; it does not validate account credentials
        val c = client.search("black beans").single()
        assertEquals("https://api.nal.usda.gov/fdc/v1/foods/search?query=black+beans&pageSize=20", fake.requests.single().url)
        fake.response = NutritionResponse(200, food())
        val d = client.details(c)
        assertFalse(d.needsDetails)
        assertEquals("https://api.nal.usda.gov/fdc/v1/food/123", fake.requests.last().url)
        for (request in fake.requests) {
            assertEquals(key, request.headers["X-Api-Key"])
            assertFalse(request.headers.containsKey("Authorization"))
            assertFalse(request.url.contains(key))
            assertFalse(request.url.contains("api_key"))
            assertEquals("GET", request.method)
            assertNull(request.body)
            assertFalse(request.toString().contains(key))
        }
        assertFalse(Gson().toJson(d.toEntry()).contains(key))
        fake.response = NutritionResponse(200, off())
        client.barcode("3017620422003")
        assertFalse(fake.requests.last().headers.containsKey("X-Api-Key"))
        assertFalse(fake.requests.last().headers.containsKey("Authorization"))
        assertTrue(fake.requests.last().url.startsWith("https://world.openfoodfacts.org/"))
    }

    @Test fun directPersonalDescriptionPreservesFractionsUnknownsOrderAndReview() = runBlocking<Unit> {
        val requests = mutableListOf<NutritionRequest>()
        val input = "  1 1/2 cups beans, 2 glugs juice\nmissing  "
        val client = NutritionClient(AppSettings(usdaApiKey = "personal-fixture-key"), NutritionTransport { request ->
            requests.add(request)
            when {
                request.url.contains("query=missing") -> NutritionResponse(200, """{"foods":[]}""")
                request.url.contains("foods/search") -> NutritionResponse(200, """{"foods":[${food()}]}""")
                else -> NutritionResponse(200, food())
            }
        })
        val c = client.describe(input)
        assertEquals(3, c.size)
        assertEquals(1.5, c[0].quantity!!, 0.0)
        assertEquals("cup", c[0].unit)
        assertEquals(450.0, c[0].toEntry().nutrients["energy_kcal"]!!, 1e-9)
        assertEquals("glugs", c[1].unit)
        assertEquals("glugs juice", c[1].metadata["description_query"])
        assertTrue(c[1].toEntry().nutrients.values.all { it == null })
        assertEquals("no_match", c[2].matchStatus)
        assertNull(c[2].quantity)
        assertTrue(c.all { it.originalText == input && it.estimated && it.metadata["review_required"] == "true" })
        assertEquals(5, requests.size)
        assertTrue(requests.all { it.method == "GET" && it.url.startsWith("https://api.nal.usda.gov/fdc/v1/") && it.headers["X-Api-Key"] == "personal-fixture-key" })
        assertTrue(requests.none { it.url.contains("api_key") || it.headers.containsKey("Authorization") })
        assertSame(c[0], client.details(c[0]))
        assertEquals(5, requests.size)
    }

    @Test fun localDescriptionParserValidatesEveryItemBeforeAnyDirectCall() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, "{}"))
        val client = NutritionClient(AppSettings(usdaApiKey = "fixture-key"), fake)
        for (input in listOf("1/0 cup rice", "0 g rice", "-1 g rice", "1000001 g rice", "rice, 1 g " + "x".repeat(201), List(9) { "rice" }.joinToString(","))) {
            expect(NutritionError.INVALID_REQUEST) { client.describe(input) }
        }
        assertTrue(fake.requests.isEmpty())
        val parsed = MealDescriptionParser.parse("2 eggs, ½ cup milk, two tablespoons oil, 100g rice")
        assertEquals(listOf(2.0, .5, 2.0, 100.0), parsed.map { it.quantity })
        assertEquals(listOf("item", "cup", "tablespoon", "g"), parsed.map { it.unit })
        assertEquals("eggs", parsed.first().query)
        assertFalse(parsed.first().recognized)
        assertEquals(8, MealDescriptionParser.parse(List(8) { "rice" }.joinToString(",")).size)
    }

    @Test fun directDescriptionDetailFallbackRetainsHintsAndRejectsIdentityMismatch() = runBlocking<Unit> {
        val requests = mutableListOf<NutritionRequest>()
        var detail = NutritionResponse(404, "{}")
        val client = NutritionClient(AppSettings(usdaApiKey = "fixture-key"), NutritionTransport { request ->
            requests.add(request)
            if (request.url.contains("foods/search")) NutritionResponse(200, """{"foods":[${food()}]}""") else detail
        })
        val c = client.describe("1/2 cup beans").single()
        assertTrue(c.needsDetails)
        detail = NutritionResponse(200, food())
        val d = client.details(c)
        assertEquals(.5, d.quantity!!, 0.0)
        assertEquals("cup", d.unit)
        assertEquals("1/2 cup beans", d.originalText)
        assertEquals("true", d.metadata["quantity_estimated"])
        assertEquals(3, requests.size)
        detail = NutritionResponse(200, food().replace("\"fdcId\":123", "\"fdcId\":124"))
        expect(NutritionError.MALFORMED_RESPONSE) { client.describe("rice") }
        assertEquals(5, requests.size)
    }

    @Test fun directKeyValidationAndFailureNeverFallBackOrRetry() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(503, "{}"))
        for (key in listOf("bad\nkey", "bad key", "x".repeat(257))) {
            val client = NutritionClient(settings.copy(usdaApiKey = key), fake)
            expect(NutritionError.INVALID_REQUEST) { client.search("rice") }
        }
        assertTrue(fake.requests.isEmpty())
        expect(NutritionError.UNAVAILABLE) { NutritionClient(settings.copy(usdaApiKey = "fixture-key"), fake).describe("rice") }
        assertEquals(1, fake.requests.size)
        assertTrue(fake.requests.single().url.startsWith("https://api.nal.usda.gov/"))
        assertFalse(fake.requests.single().headers.containsKey("Authorization"))
        fake.response = NutritionResponse(401, """{"error":{"message":"fake-upstream-secret"}}""")
        val error = expect(NutritionError.AUTHENTICATION) { NutritionClient(settings.copy(usdaApiKey = "fixture-key"), fake).search("rice") }
        assertEquals("Nutrition authentication failed. Check your USDA key or gateway token.", error.message)
        assertFalse(error.toString().contains("fixture-key"))
        assertFalse(error.toString().contains("fake-upstream-secret"))
    }

    @Test fun directPolicyBudgetsAndProviderCooldownAreSharedAndDoNotRetry() = runBlocking<Unit> {
        var now = 0L
        val policy = UsdaDirectPolicy { now }
        var calls = 0
        repeat(100) { policy.call { calls++ } }
        expect(NutritionError.BUDGET_EXCEEDED) { policy.call { calls++ } }
        assertEquals(100, calls)
        now = 3600000L
        policy.call { calls++ }
        assertEquals(101, calls)
        repeat(30) { policy.action() }
        expect(NutritionError.RATE_LIMITED) { policy.action() }
        now += 60000L
        policy.action()
        expect(NutritionError.RATE_LIMITED) { policy.call { calls++; throw NutritionException(NutritionError.RATE_LIMITED, 120) } }
        expect(NutritionError.RATE_LIMITED) { policy.call { calls++ } }
        assertEquals(102, calls)
        now += 120000L
        policy.call { calls++ }
        assertEquals(103, calls)
    }

    @Test fun directPolicyConcurrencyAndCancelledCallsReleaseSharedSlots() = runBlocking<Unit> {
        val policy = UsdaDirectPolicy { 0L }
        val fourStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val jobs = List(8) { async {
            policy.call {
                active++
                maximum = maxOf(maximum, active)
                if (active == 4) fourStarted.complete(Unit)
                try { release.await() } finally { active-- }
            }
        } }
        fourStarted.await()
        assertEquals(4, maximum)
        release.complete(Unit)
        jobs.awaitAll()
        assertEquals(0, active)
        assertEquals(4, maximum)
        val entered = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val cancelled = async { policy.call { entered.complete(Unit); hold.await() } }
        entered.await()
        cancelled.cancelAndJoin()
        assertEquals("available", policy.call { "available" })
    }

    private fun liveUsdaFixture(kind: String): String = javaClass.getResourceAsStream(
        "/nutrition/usda-live-$kind-2026-10-01.json")!!.bufferedReader().use { it.readText() }

    @Test fun actualUsdaSearchDetailsReplayKeepsReferenceBasisPortionsAndUnknowns() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, liveUsdaFixture("search")))
        val client = NutritionClient(AppSettings(usdaApiKey = "public-response-replay-fixture"), fake)
        val candidates = client.search("apples")
        assertEquals(2, candidates.size)
        assertEquals("usda:1750340", candidates.first().sourceId)
        assertEquals("Apples, fuji, with skin, raw", candidates.first().name)
        assertTrue(candidates.first().needsDetails)
        fake.response = NutritionResponse(200, liveUsdaFixture("detail"))
        val c = client.details(candidates.first())
        assertEquals(NutrientBasis.PER_100_G, c.basis)
        assertEquals(100.0, c.basisQuantity!!, 0.0)
        assertEquals("g", c.basisUnit)
        assertNull(c.servingMassG) // RACC is the named source portion, not an implied serving/apple
        val portion = c.portions.single()
        assertEquals("RACC", portion.label)
        assertEquals(1.0, portion.quantity, 0.0)
        assertEquals(140.0, portion.massGrams!!, 0.0)
        assertEquals(58.20306, c.nutrients["energy_kcal"]!!, 1e-9)
        assertEquals("usda:2048", c.nutrientProviderCodes["energy_kcal"])
        assertTrue("energy_kcal" in c.estimatedNutrients)
        assertNull(c.nutrients["saturated_fat_g"])
        assertNull(c.nutrients["usda_2045_g"])
        assertEquals(0.0, c.nutrients["usda_1013_g"]!!, 0.0)
        val e = c.toEntry(1.0, "RACC")
        val grams = c.toEntry(140.0, "g")
        assertEquals(grams.nutrients, e.nutrients)
        assertEquals(140.0, e.massGrams!!, 0.0)
        assertEquals(81.484284, e.nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(.2078125, e.nutrients["protein_g"]!!, 1e-9)
        assertEquals(1.4182, e.nutrients["sodium_mg"]!!, 1e-9)
        assertEquals(2.905, e.nutrients["fiber_g"]!!, 1e-9)
        assertNull(e.nutrients["saturated_fat_g"])
        assertEquals("estimated", e.nutrientEvidence["energy_kcal"]!!.status)
        assertEquals("CC0-1.0", e.sourceReference!!.license)
        assertTrue(c.toEntry(1.0, "apple").nutrients.values.all { it == null })
        assertTrue(c.toEntry(1.0, "serving").nutrients.values.all { it == null })
        assertTrue(c.toEntry(1.0, "mL").nutrients.values.all { it == null })
        assertEquals(2, fake.requests.size)
        assertTrue(fake.requests.all { it.url.startsWith("https://api.nal.usda.gov/") && !it.url.contains("api_key") })
    }

    @Test fun actualUsdaDescriptionReplayPreservesKnownRaccUnknownAppleAndUnspecifiedAmount() = runBlocking<Unit> {
        val requests = mutableListOf<NutritionRequest>()
        val input = "1 RACC apples, 1 apple, apples"
        val search = liveUsdaFixture("search")
        val detail = liveUsdaFixture("detail")
        val client = NutritionClient(AppSettings(usdaApiKey = "public-response-replay-fixture"), NutritionTransport { request ->
            requests.add(request)
            NutritionResponse(200, if (request.url.contains("foods/search")) search else detail)
        })
        val c = client.describe(input)
        assertEquals(3, c.size)
        assertEquals(81.484284, c[0].toEntry().nutrients["energy_kcal"]!!, 1e-9)
        assertEquals("RACC", c[0].unit)
        assertEquals("false", c[0].metadata["unit_recognized"])
        assertEquals("item", c[1].unit)
        assertTrue(c[1].toEntry().nutrients.values.all { it == null })
        assertNull(c[2].quantity)
        assertTrue(c[2].toEntry().nutrients.values.all { it == null })
        assertTrue(c.all { it.originalText == input && it.sourceId == "usda:1750340" && it.matchStatus == "unreviewed" })
        assertEquals(6, requests.size)
        assertSame(c[0], client.details(c[0]))
        assertEquals(6, requests.size)
    }

    private suspend fun expect(category: NutritionError, block: suspend () -> Unit): NutritionException {
        try { block(); fail("Expected $category") } catch (error: NutritionException) { assertEquals(category, error.category); return error }
        throw AssertionError()
    }

    @Test fun usdaPer100gNullsZerosAndAdditionalNutrients() {
        val candidate = ProviderMappers.usda(json(food()))
        val entry = candidate.toEntry(150.0, "g")
        assertEquals(300.0, entry.nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(15.0, entry.nutrients["protein_g"]!!, 1e-9)
        assertEquals(0.0, entry.nutrients["sodium_mg"]!!, 0.0)
        assertNull(entry.nutrients["fiber_g"])
        assertEquals(30.0, entry.nutrients["usda_1087_mg"]!!, 1e-9)
        assertEquals("usda:123", entry.sourceReference!!.itemId)
        assertEquals(100.0, entry.basisQuantity!!, 0.0)
        assertEquals("consumed; reference=100.0 g; scale=1.5", entry.nutrientEvidence["energy_kcal"]!!.basis)
    }

    @Test fun gramsOuncesAndNamedPortionsScaleWithoutDensity() {
        val c = ProviderMappers.usda(json(food()))
        assertEquals(300.0, c.toEntry(1.0, "cup").nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(56.69904625, c.toEntry(1.0, "oz").nutrients["energy_kcal"]!!, 1e-9)
        assertNull(c.toEntry(1.0, "mL").nutrients["energy_kcal"])
        assertNull(c.toEntry(null, "g").nutrients["energy_kcal"])
    }

    @Test fun brandedUsdaCanBePer100ml() {
        val c = ProviderMappers.usda(json(food(true, "ml")))
        assertEquals(NutrientBasis.PER_100_ML, c.basis)
        assertEquals(EntryKind.DRINK, c.kind)
        assertEquals(400.0, c.toEntry(200.0, "mL").nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(300.0, c.toEntry(1.0, "serving").nutrients["energy_kcal"]!!, 1e-9)
        assertNull(c.toEntry(200.0, "g").nutrients["energy_kcal"])
    }

    @Test fun unknownBrandedBasisRequiresDetailsInsteadOfGuessing() = runBlocking<Unit> {
        val root = json(food(true)).apply { remove("servingSizeUnit") }
        val c = ProviderMappers.usda(root, summary = true)
        assertTrue(c.needsDetails)
        assertEquals(NutrientBasis.UNKNOWN, c.basis)
        assertTrue(c.toEntry(100.0, "g").nutrients.values.all { it == null })
        expect(NutritionError.MALFORMED_RESPONSE) { ProviderMappers.usda(root) }
    }

    @Test fun usdaSearchAndSelectedDetailsUseDistinctEndpoints() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, """{"foods":[${food()}]}"""))
        val client = NutritionClient(settings, fake)
        val c = client.search("black beans").single()
        assertTrue(c.needsDetails)
        assertTrue(fake.requests[0].url.endsWith("/v1/usda/search?query=black+beans"))
        fake.response = NutritionResponse(200, food())
        assertFalse(client.details(c).needsDetails)
        assertTrue(fake.requests[1].url.endsWith("/v1/usda/foods/123"))
        fake.response = NutritionResponse(200, food().replace("\"fdcId\":123", "\"fdcId\":124"))
        expect(NutritionError.MALFORMED_RESPONSE) { client.details("123") }
    }

    @Test fun historicalNutritionixConsumedBasisIsNotScaledTwice() {
        val c = ProviderMappers.nutritionix(json(nx()).array("foods").first().asJsonObject, "two cups milk")
        val original = c.toEntry()
        assertEquals(240.0, original.nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(488.0, original.massGrams!!, 1e-9)
        assertEquals(120.0, c.toEntry(1.0, "cup").nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(120.0, c.toEntry(244.0, "g").nutrients["energy_kcal"]!!, 1e-9)
        assertNull(c.toEntry(200.0, "mL").nutrients["energy_kcal"])
        assertEquals("estimated", original.portionEvidence)
        assertEquals("unreviewed", original.matchStatus)
        assertNull(c.confidence)
        assertEquals("two cups milk", original.originalText)
        assertEquals(EntryKind.DRINK, c.kind)
        assertEquals(500.0, original.nutrients["nutritionix_attr_301"]!!, 1e-9)
    }

    @Test fun describeTimeoutAndMalformedResponseAreNeverRetried() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(503, "{\"error\":{\"code\":\"unavailable\",\"message\":\"secret-token\"}}"))
        expect(NutritionError.UNAVAILABLE) { NutritionClient(settings, fake).describe("coffee") }
        assertEquals(1, fake.requests.size)
        fake.requests.clear(); fake.response = NutritionResponse(200, "not json")
        expect(NutritionError.MALFORMED_RESPONSE) { NutritionClient(settings, fake).describe("coffee") }
        assertEquals(1, fake.requests.size)
    }

    @Test fun offUsesOneSourceAndPer100mlNoAggregateFallback() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, off()))
        val c = NutritionClient(AppSettings(), fake).barcode("3017620422003")!!
        assertEquals(40.0, c.nutrients["energy_kcal"]!!, 1e-9)
        assertNull(c.nutrients["fiber_g"])
        val e = c.toEntry(16.0, "US fl oz")
        assertEquals(473.176473, e.volumeMl!!, 1e-6)
        assertEquals(189.2705892, e.nutrients["energy_kcal"]!!, 1e-6)
        assertEquals(40.0, c.nutrients["sodium_mg"]!!, 1e-9)
        assertTrue("sodium_mg" in c.estimatedNutrients)
        assertEquals(EntryKind.DRINK, e.kind)
        assertEquals("en:united-states", c.region)
        assertEquals("3017620422003", e.sourceReference!!.barcode)
        assertTrue(e.sourceReference!!.exportAllowed)
        assertTrue(e.sourceReference!!.snapshotAllowed)
        assertTrue(e.sourceReference!!.license.contains("https://opendatacommons.org/licenses/odbl/1-0/"))
        assertTrue(e.sourceReference!!.license.contains("https://opendatacommons.org/licenses/dbcl/1-0/"))
        assertTrue(fake.requests.single().url.startsWith("https://world.openfoodfacts.org/api/v3.6/product/3017620422003.json"))
        assertTrue(fake.requests.single().headers["User-Agent"]!!.startsWith("Newleaf/"))
        assertFalse(fake.requests.single().headers.containsKey("Authorization"))
    }

    @Test fun offPerServingCanScaleOnlyDeclaredDefinition() {
        val c = ProviderMappers.openFoodFacts(json(off("serving", extra = "\"per_quantity\":250,\"per_unit\":\"ml\",")), "3017620422003")!!
        assertEquals(40.0, c.toEntry().nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(80.0, c.toEntry(500.0, "mL").nutrients["energy_kcal"]!!, 1e-9)
        assertNull(c.toEntry(500.0, "g").nutrients["energy_kcal"])
    }

    @Test fun offCompanionServingScalesSelectedSetWithoutStitching() = runBlocking<Unit> {
        val root = json(off("100g", "manufacturer"))
        val sets = root.obj("product")!!.obj("nutrition")!!.getAsJsonArray("input_sets")
        fun serving(source: String, quantity: Double) = json("""{
          "source":"$source","preparation":"as_sold","per":"serving",
          "per_quantity":$quantity,"per_unit":"g",
          "nutrients":{"energy-kcal":{"value":999,"unit":"kcal"},"fiber":{"value":9,"unit":"g"}}}""")
        sets.add(serving("packaging", 80.0))
        sets.add(serving("manufacturer", 20.0))
        val candidate = NutritionClient(AppSettings(), Fake(NutritionResponse(200, root.toString())))
            .barcode("3017620422003")!!
        assertEquals(20.0, candidate.servingMassG!!, 0.0)
        assertEquals(1, candidate.portions.size)
        val entry = candidate.toEntry(2.0, "serving")
        assertEquals(40.0, entry.massGrams!!, 0.0)
        assertEquals(16.0, entry.nutrients["energy_kcal"]!!, 1e-9)
        assertNull(entry.nutrients["fiber_g"])
        assertEquals(8.0, candidate.toEntry(20.0, "g").nutrients["energy_kcal"]!!, 1e-9)

        sets.add(serving("manufacturer", 30.0))
        val ambiguous = ProviderMappers.openFoodFacts(root, "3017620422003")!!
        assertNull(ambiguous.servingMassG)
        assertTrue(ambiguous.portions.isEmpty())
        assertNull(ambiguous.toEntry(1.0, "serving").nutrients["energy_kcal"])
        assertEquals(8.0, ambiguous.toEntry(20.0, "g").nutrients["energy_kcal"]!!, 1e-9)
    }

    @Test fun offRejectsUnknownBasisAndUnapprovedSets() = runBlocking<Unit> {
        for (fixture in listOf(off("mystery"), off(source = "estimate"), off().replace("as_sold", "prepared"))) {
            expect(NutritionError.MALFORMED_RESPONSE) { ProviderMappers.openFoodFacts(json(fixture), "3017620422003") }
        }
    }

    @Test fun offMissingRecordDiffersFromMalformedFailure() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(404, ""))
        val client = NutritionClient(AppSettings(), fake)
        assertNull(client.barcode("3017620422003"))
        fake.response = NutritionResponse(200, """{"status":"failure","result":{"id":"product_not_found"}}""")
        assertNull(client.barcode("3017620422003"))
        fake.response = NutritionResponse(200, """{"status":"failure","result":{"id":"database_error"}}""")
        expect(NutritionError.MALFORMED_RESPONSE) { client.barcode("3017620422003") }
    }

    @Test fun barcodeExactIdentityAndChecksumRequired() = runBlocking<Unit> {
        assertTrue(NutritionClient.validBarcode("3017620422003"))
        assertTrue(NutritionClient.validBarcode("036000291452"))
        val fake = Fake(NutritionResponse(200, off()))
        val client = NutritionClient(AppSettings(), fake)
        for (code in listOf("../3017620422003", "3017620422004", "3e12", "hello", "3017620422003?url=x")) {
            expect(NutritionError.INVALID_REQUEST) { client.barcode(code) }
        }
        assertTrue(fake.requests.isEmpty())
        fake.response = NutritionResponse(200, off().replace("3017620422003", "036000291452"))
        expect(NutritionError.MALFORMED_RESPONSE) { client.barcode("3017620422003") }
        assertEquals(1, fake.requests.size)
    }

    @Test fun keyedSourcesAreUnconfiguredAndStatusDoesNotFakeLookup() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, "{}"))
        val client = NutritionClient(AppSettings(), fake)
        assertFalse(client.status().usda.configured)
        assertFalse(client.status().nutritionix.enabled)
        assertTrue(client.status().openFoodFacts.enabled)
        expect(NutritionError.UNCONFIGURED) { client.search("beans") }
        expect(NutritionError.UNCONFIGURED) { client.describe("beans") }
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun gatewayStatusAndSafeErrorsExposeNoCredentials() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, """{"usda":{"configured":true,"enabled":true,"reason":null},"nutritionix":{"configured":true,"enabled":true,"reason":null}}"""))
        val client = NutritionClient(settings, fake)
        assertEquals("free_only", client.status().nutritionix.reason)
        assertFalse(client.status().nutritionix.enabled)
        fake.response = NutritionResponse(429, """{"error":{"code":"budget_exceeded","message":"private-device-fixture-token upstream key"}}""", 30)
        val error = expect(NutritionError.BUDGET_EXCEEDED) { client.describe("beans") }
        assertEquals(30L, error.retryAfterSeconds)
        assertNull(error.cause)
        assertFalse(error.toString().contains(settings.gatewayToken))
        assertFalse(fake.requests.last().toString().contains(settings.gatewayToken))
        val serialized = Gson().toJson(ProviderMappers.usda(json(food())).toEntry(150.0, "g"))
        assertFalse(serialized.contains(settings.gatewayToken))
    }

    @Test fun onlyHttpsRootGatewayMayReceiveDeviceToken() = runBlocking<Unit> {
        val fake = Fake(NutritionResponse(200, "{}"))
        for (url in listOf("http://localhost:8787", "https://user:pass@fixture.invalid", "https://fixture.invalid?api_key=x", "https://fixture.invalid/evil", "https://fixture.invalid#fragment")) {
            expect(NutritionError.INVALID_REQUEST) { NutritionClient(settings.copy(gatewayUrl = url), fake).search("beans") }
        }
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun abandonedRequestAndDuplicateInFlightAreHandled() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val wait = CompletableDeferred<NutritionResponse>()
        var attempts = 0
        val client = NutritionClient(settings, NutritionTransport { attempts++; entered.complete(Unit); wait.await() })
        val first = async { client.describe("coffee") }
        entered.await()
        expect(NutritionError.UNAVAILABLE) { client.describe("coffee") }
        assertEquals(1, attempts)
        first.cancelAndJoin()
        wait.complete(NutritionResponse(200, described("coffee", descriptionItem("coffee", null, "serving", matched = false))))
        assertEquals("no_match", client.describe("coffee").single().matchStatus)
        assertEquals(2, attempts)
    }

    @Test fun drinkClassificationDoesNotTreatMilkChocolateAsBeverage() {
        val root = json(nx()).array("foods").first().asJsonObject
        assertEquals(EntryKind.DRINK, ProviderMappers.nutritionix(root, "milk").kind)
        root.addProperty("food_name", "milk chocolate")
        assertEquals(EntryKind.FOOD, ProviderMappers.nutritionix(root, "milk chocolate").kind)
        assertEquals(EntryKind.DRINK, ProviderMappers.nutritionix(root, "milk chocolate").toEntry(kind = EntryKind.DRINK).kind)
    }

    @Test fun realHttpTransportDoesNotFollowRedirectOrRetryPost() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val server = ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 2000
                    val input = socket.getInputStream().bufferedReader()
                    var length = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    repeat(length) { input.read() }
                    val count = calls.incrementAndGet()
                    val response = if (count == 1) "HTTP/1.1 302 Found\r\nLocation: /other\r\n" else "HTTP/1.1 200 OK\r\n"
                    socket.getOutputStream().write((response + "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                }
            } catch (_: SocketException) { /* closing test fixture listener */ }
        }.apply { isDaemon = true; start() }
        try {
            val result = UrlConnectionTransport().execute(NutritionRequest("POST", "http://127.0.0.1:${server.localPort}/fixture", mapOf("Content-Type" to "application/json"), "{\"text\":\"beans\"}"))
            assertEquals(302, result.status)
            assertEquals(1, calls.get())
        } finally { server.close(); worker.join(1000) }
    }

    @Test fun offKjUnitsAndBoundsArePreserved() {
        val root = json(off())
        val nutrients = root.obj("product")!!.obj("nutrition")!!.array("input_sets").first().asJsonObject.obj("nutrients")!!
        nutrients.remove("energy-kcal")
        nutrients.add("energy-kj", json("""{"value":418.4,"unit":"kJ"}"""))
        nutrients.add("proteins", json("""{"value":2000,"unit":"mg"}"""))
        nutrients.add("sugars", json("""{"value":0.5,"unit":"g","modifier":"<"}"""))
        val c = ProviderMappers.openFoodFacts(root, "3017620422003")!!
        assertEquals(100.0, c.nutrients["energy_kcal"]!!, 1e-9)
        assertEquals(2.0, c.nutrients["protein_g"]!!, 1e-9)
        assertNull(c.nutrients["sugar_g"])
        assertEquals("<0.5 g", c.toEntry().metadata["provider_nutrient_off_sugars"])
        assertEquals("2000.0 mg", c.metadata["provider_nutrient_off_proteins"])
        nutrients.add("energy-kj", json("""{"value":500,"unit":"unknown"}"""))
        assertNull(ProviderMappers.openFoodFacts(root, "3017620422003")!!.nutrients["energy_kcal"])
    }

    @Test fun offAvailableCarbsAreDistinctFromTotalCarbs() {
        val root = json(off())
        val nutrients = root.obj("product")!!.obj("nutrition")!!.array("input_sets").first().asJsonObject.obj("nutrients")!!
        nutrients.add("carbohydrates", json("""{"value":10,"unit":"g"}"""))
        assertNull(ProviderMappers.openFoodFacts(root, "3017620422003")!!.nutrients["carbohydrate_g"])
        nutrients.add("fiber", json("""{"value":2,"unit":"g"}"""))
        val c = ProviderMappers.openFoodFacts(root, "3017620422003")!!
        assertEquals(12.0, c.nutrients["carbohydrate_g"]!!, 1e-9)
        assertTrue("carbohydrate_g" in c.estimatedNutrients)
        assertEquals(10.0, c.nutrients["off_carbohydrates"]!!, 1e-9)
    }

    @Test fun offExactProductWithNoNutritionRetainsMissingness() {
        val root = json("""{"status":"success","product":{"code":"3017620422003","product_name":"Unknown packaged item"}}""")
        val c = ProviderMappers.openFoodFacts(root, "3017620422003")!!
        assertEquals(NutrientBasis.UNKNOWN, c.basis)
        assertTrue(c.nutrients.values.all { it == null })
        assertTrue(c.toEntry(100.0,"g").nutrients.values.all { it == null })
        assertEquals("3017620422003", c.barcode)
    }

    @Test fun usdaDerivationAndRawKjArePreserved() {
        val root = json(food())
        root.array("foodNutrients").first().asJsonObject.apply {
            add("nutrient", json("""{"id":1062,"unitName":"kJ"}"""))
            addProperty("amount", 418.4)
        }
        root.array("foodNutrients")[1].asJsonObject.add("foodNutrientDerivation", json("""{"description":"Calculated or imputed"}"""))
        val c = ProviderMappers.usda(root)
        assertEquals(100.0,c.nutrients["energy_kcal"]!!,1e-9)
        assertEquals("418.4 kJ",c.metadata["provider_nutrient_usda_1062"])
        assertEquals("estimated",c.toEntry().nutrientEvidence["protein_g"]!!.status)
    }

    @Test fun nutritionixDictionaryUnitsAndNestedIdentityArePreserved() {
        val root = json(nx()).array("foods").first().asJsonObject
        root.add("tags",json("""{"tag_id":123}"""))
        root.array("full_nutrients").add(json("""{"attr_id":999999,"value":1.2}"""))
        val c = ProviderMappers.nutritionix(root,"milk")
        assertEquals("nutritionix:tag:123",c.sourceId)
        assertEquals("mg",c.toEntry().nutrientEvidence["nutritionix_attr_301"]!!.unit)
        assertEquals("provider-defined",c.toEntry().nutrientEvidence["nutritionix_attr_999999"]!!.unit)
    }

    @Test fun retryAfterHandlesSecondsAndHttpDates() {
        assertEquals(120L,retryAfter("120"))
        val future = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(2).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        assertTrue(retryAfter(future)!! in 115L..120L)
        assertNull(retryAfter("invalid"))
    }

    @Test fun explicitFoodSpecificMassAndVolumePermitCrossConversion() {
        val c = FoodCandidate(name = "Fixture oil", nutrients = mapOf("energy_kcal" to 200.0),
            basis = NutrientBasis.PER_100_G, servingMassG = 30.0, servingVolumeMl = 20.0,
            source = "fixture", sourceReference = SourceReference())
        assertEquals(120.0,c.toEntry(40.0,"mL").nutrients["energy_kcal"]!!,1e-9)
        assertEquals(60.0,c.toEntry(40.0,"mL").massGrams!!,1e-9)
        assertEquals(60.0,c.toEntry(1.0,"serving").nutrients["energy_kcal"]!!,1e-9)
        val unknown = c.copy(servingVolumeMl=null)
        assertNull(unknown.toEntry(40.0,"mL").nutrients["energy_kcal"])
    }

    @Test fun offSnapshotCanSaveExportAndRestoreWithLicenseNotices() {
        val entry = ProviderMappers.openFoodFacts(json(off()), "3017620422003")!!.toEntry(250.0, "mL")
        val journal = com.khaleel.newleaf.data.Journal(entries = listOf(entry))
        val exported = com.khaleel.newleaf.data.JournalExports.json(journal)
        val markdown = com.khaleel.newleaf.data.JournalExports.markdown(journal)
        val restored = com.khaleel.newleaf.data.JournalExports.previewRestore(
            com.khaleel.newleaf.data.JournalExports.backup(journal)).journal.entries.single()
        for (output in listOf(exported, markdown)) {
            assertTrue(output.contains("Open Food Facts contributors"))
            assertTrue(output.contains("https://world.openfoodfacts.org/"))
            assertTrue(output.contains("https://opendatacommons.org/licenses/odbl/1-0/"))
            assertTrue(output.contains("https://opendatacommons.org/licenses/dbcl/1-0/"))
        }
        assertEquals(entry.nutrients, restored.nutrients)
        assertEquals(entry.sourceReference, restored.sourceReference)
        assertEquals(100.0, restored.nutrients["energy_kcal"]!!, 1e-9)
    }

}
