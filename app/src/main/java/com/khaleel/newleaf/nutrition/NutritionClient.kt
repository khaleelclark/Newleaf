package com.khaleel.newleaf.nutrition

import com.google.gson.JsonObject
import com.khaleel.newleaf.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.net.URLEncoder

/** Public status is safe to display and serialize; no keys/tokens appear here. */
data class ProviderStatus(val configured: Boolean, val enabled: Boolean, val reason: String?)
data class GatewayStatus(val usda: ProviderStatus, val nutritionix: ProviderStatus,
    val openFoodFacts: ProviderStatus = ProviderStatus(true, true, null))

class NutritionClient(
    private val settings: AppSettings,
    private val transport: NutritionTransport = UrlConnectionTransport(),
    private val openFoodFactsUserAgent: String = "Newleaf/0.1.0 (personal Android food journal)"
) {
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()
    private val off = "https://world.openfoodfacts.org/api/v3.6/product/"
    private val userAgent = "Newleaf/0.1.0 (personal Android food journal)"
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun gateway(): String {
        if (settings.gatewayUrl.isBlank() || settings.gatewayToken.isBlank()) throw NutritionException(NutritionError.UNCONFIGURED)
        val uri = try { URI(settings.gatewayUrl.trim()) } catch (_: Exception) { throw NutritionException(NutritionError.INVALID_REQUEST) }
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/") || settings.gatewayToken.any { it.isWhitespace() }) {
            throw NutritionException(NutritionError.INVALID_REQUEST)
        }
        return uri.toASCIIString().trimEnd('/')
    }
    private fun request(path: String, body: JsonObject? = null): NutritionRequest = NutritionRequest(
        if (body == null) "GET" else "POST", gateway() + path,
        mapOf("Authorization" to "Bearer ${settings.gatewayToken}", "Accept" to "application/json", "Content-Type" to "application/json", "User-Agent" to userAgent), body?.toString())

    private suspend fun <T> unique(key: String, block: suspend () -> T): T {
        mutex.withLock { if (!inFlight.add(key)) throw NutritionException(NutritionError.UNAVAILABLE) }
        try { return block()
        } catch (error: CancellationException) { throw error
        } catch (error: NutritionException) { throw error
        } catch (_: Exception) { throw NutritionException(NutritionError.MALFORMED_RESPONSE)
        } finally {
            // Cancellation must not leave an in-flight marker behind.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { mutex.withLock { inFlight.remove(key) } }
        }
    }

    suspend fun status(): GatewayStatus {
        if (settings.gatewayUrl.isBlank() || settings.gatewayToken.isBlank()) return GatewayStatus(
            ProviderStatus(false, false, "unconfigured"), ProviderStatus(false, false, "unconfigured"))
        return unique("status") {
            val root = transport.execute(request("/v1/status")).json()
            fun provider(key: String): ProviderStatus {
                val p = root.obj(key) ?: throw NutritionException(NutritionError.MALFORMED_RESPONSE)
                fun flag(field: String): Boolean = p.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
                    ?: throw NutritionException(NutritionError.MALFORMED_RESPONSE)
                return ProviderStatus(flag("configured"), flag("enabled"), p.text("reason"))
            }
            GatewayStatus(provider("usda"), provider("nutritionix"))
        }
    }

    suspend fun search(query: String): List<FoodCandidate> {
        val text = query.trim()
        if (text.length !in 2..200 || text.any { it.code < 32 }) throw NutritionException(NutritionError.INVALID_REQUEST)
        return unique("search:$text") {
            val root = transport.execute(request("/v1/usda/search?query=${encode(text)}")).json()
            val foods = root.get("foods")?.takeIf { it.isJsonArray }?.asJsonArray ?: throw NutritionException(NutritionError.MALFORMED_RESPONSE)
            if (foods.size() > 20) throw NutritionException(NutritionError.MALFORMED_RESPONSE)
            foods.map { ProviderMappers.usda(it.asJsonObject, summary = true) }
        }
    }

    suspend fun details(candidate: FoodCandidate): FoodCandidate {
        if (!candidate.sourceId.orEmpty().startsWith("usda:")) return candidate
        return details(candidate.sourceId!!.removePrefix("usda:"))
    }

    suspend fun details(fdcId: String): FoodCandidate {
        if (!Regex("[1-9][0-9]{0,9}").matches(fdcId)) throw NutritionException(NutritionError.INVALID_REQUEST)
        return unique("detail:$fdcId") {
            val result = ProviderMappers.usda(transport.execute(request("/v1/usda/foods/$fdcId")).json())
            if (result.sourceId != "usda:$fdcId") throw NutritionException(NutritionError.MALFORMED_RESPONSE)
            result
        }
    }

    suspend fun describe(text: String): List<FoodCandidate> {
        val description = text.trim()
        if (description.length !in 2..2000 || description.any { it.code < 32 && it != '\n' && it != '\t' }) throw NutritionException(NutritionError.INVALID_REQUEST)
        return unique("describe:$description") {
            val body = JsonObject().apply { addProperty("text", description) }
            // Exactly one POST. No retry on timeout, cancellation, provider error or response parsing.
            val root = transport.execute(request("/v1/nutritionix/describe", body)).json()
            val foods = root.get("foods")?.takeIf { it.isJsonArray }?.asJsonArray ?: throw NutritionException(NutritionError.MALFORMED_RESPONSE)
            if (foods.size() > 100) throw NutritionException(NutritionError.MALFORMED_RESPONSE)
            foods.map { ProviderMappers.nutritionix(it.asJsonObject, text) }
        }
    }

    suspend fun barcode(code: String): FoodCandidate? {
        val barcode = code.trim()
        if (openFoodFactsUserAgent.length !in 10..200 || openFoodFactsUserAgent.any { it.code < 32 || it.code > 126 } || !openFoodFactsUserAgent.startsWith("Newleaf/")) throw NutritionException(NutritionError.INVALID_REQUEST)
        if (!validBarcode(barcode)) throw NutritionException(NutritionError.INVALID_REQUEST)
        return unique("barcode:$barcode") {
            val fields = "code,product_name,product_name_en,generic_name,brands,categories_tags,countries_tags,nutrition"
            val response = transport.execute(NutritionRequest("GET", "$off$barcode.json?fields=$fields",
                mapOf("User-Agent" to openFoodFactsUserAgent, "Accept" to "application/json")))
            if (response.status == 404) null else ProviderMappers.openFoodFacts(response.json(), barcode)
        }
    }

    companion object {
        fun validBarcode(code: String): Boolean {
            if (code.length !in setOf(8, 12, 13, 14) || !code.all { it in '0'..'9' }) return false
            val sum = code.dropLast(1).reversed().mapIndexed { index, digit -> (digit - '0') * if (index % 2 == 0) 3 else 1 }.sum()
            return (10 - sum % 10) % 10 == code.last() - '0'
        }
    }
}
