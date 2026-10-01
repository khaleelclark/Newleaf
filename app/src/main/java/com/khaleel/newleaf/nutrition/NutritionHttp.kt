package com.khaleel.newleaf.nutrition

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/** Values are deliberately not included in toString; Authorization never enters diagnostics. */
class NutritionRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String? = null) {
    override fun toString() = "NutritionRequest($method)"
}
data class NutritionResponse(val status: Int, val body: String, val retryAfterSeconds: Long? = null)
fun interface NutritionTransport { suspend fun execute(request: NutritionRequest): NutritionResponse }

enum class NutritionError { UNCONFIGURED, AUTHENTICATION, ACCESS_ENTITLEMENT, RATE_LIMITED, BUDGET_EXCEEDED, INVALID_REQUEST, NOT_FOUND, UNAVAILABLE, MALFORMED_RESPONSE }
class NutritionException(val category: NutritionError, val retryAfterSeconds: Long? = null) : Exception(when(category) {
    NutritionError.UNCONFIGURED -> "Online nutrition is not configured. Add your gateway address and device token in settings."
    NutritionError.AUTHENTICATION -> "Gateway authentication failed. Check your device token."
    NutritionError.ACCESS_ENTITLEMENT -> "This nutrition source is not enabled for this account."
    NutritionError.RATE_LIMITED -> "Nutrition source limit reached. Try later."
    NutritionError.BUDGET_EXCEEDED -> "Your configured nutrition request budget has been reached."
    NutritionError.INVALID_REQUEST -> "Check your food query, barcode, or portion."
    NutritionError.NOT_FOUND -> "No matching food was found."
    NutritionError.UNAVAILABLE -> "Nutrition source is temporarily unavailable. Your draft is preserved."
    NutritionError.MALFORMED_RESPONSE -> "Nutrition source returned unsupported data. Try manual entry."
})

class UrlConnectionTransport : NutritionTransport {
    override suspend fun execute(request: NutritionRequest): NutritionResponse {
        val parentJob = currentCoroutineContext()[Job]
        val active = AtomicReference<HttpURLConnection?>()
        // Job callback runs on cancellation and closes blocking I/O immediately.
        @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
        val cancellation = parentJob?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
            if (cause != null) active.get()?.disconnect()
        }
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val connection = URL(request.url).openConnection() as HttpURLConnection
                active.set(connection)
                try {
                    currentCoroutineContext().ensureActive()
                    connection.connectTimeout = 8000
                    connection.readTimeout = 12000
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = request.method
                    request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                    request.body?.let { body ->
                        connection.doOutput = true
                        val bytes = body.toByteArray(StandardCharsets.UTF_8)
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                    }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val out = ByteArrayOutputStream()
                    stream?.use { input ->
                        val chunk = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(chunk)
                            if (count < 0) break
                            if (out.size() + count > 2 * 1024 * 1024) throw NutritionException(NutritionError.MALFORMED_RESPONSE)
                            out.write(chunk, 0, count)
                        }
                    }
                    NutritionResponse(status, out.toString("UTF-8"), retryAfter(connection.getHeaderField("Retry-After")))
                } catch (error: NutritionException) { throw error
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    // Never attach URL/headers/body or the raw upstream exception.
                    throw NutritionException(NutritionError.UNAVAILABLE)
                } finally { active.set(null); connection.disconnect() }
            }
        } finally { cancellation?.dispose() }
    }
}

internal fun NutritionResponse.json(): JsonObject {
    if (status !in 200..299) {
        val code = try { JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")?.get("code")?.asString } catch (_: Exception) { null }
        val category = try { code?.uppercase()?.let { NutritionError.valueOf(it) } } catch (_: Exception) { null }
        throw NutritionException(category ?: when(status) { 401 -> NutritionError.AUTHENTICATION; 403 -> NutritionError.ACCESS_ENTITLEMENT; 404 -> NutritionError.NOT_FOUND; 429 -> NutritionError.RATE_LIMITED; else -> NutritionError.UNAVAILABLE }, retryAfterSeconds)
    }
    return try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { throw NutritionException(NutritionError.MALFORMED_RESPONSE) }
}

internal fun retryAfter(value: String?): Long? {
    if (value == null) return null
    return value.toLongOrNull()?.coerceIn(1, 86400) ?: try {
        (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().epochSecond - Instant.now().epochSecond).coerceIn(1, 86400)
    } catch (_: Exception) { null }
}
