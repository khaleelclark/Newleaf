package com.khaleel.newleaf.data

import com.google.gson.JsonElement

/** Explicit device credential names; unrelated future settings remain portable. */
internal object CredentialSettings {
    fun stripFields(value: JsonElement) {
        when {
            value.isJsonObject -> {
                val objectValue = value.asJsonObject
                objectValue.keySet().toList().forEach { key ->
                    val normalized = key.lowercase().replace("_", "").replace("-", "")
                    if (normalized in setOf("gatewaytoken", "usdaapikey")) objectValue.remove(key)
                    else stripFields(objectValue[key])
                }
            }
            value.isJsonArray -> value.asJsonArray.forEach(::stripFields)
        }
    }

    fun withoutCredentials(settings: AppSettings): AppSettings = settings.copy(
        gatewayToken = "", usdaApiKey = "",
        extensions = settings.extensions.deepCopy().also(::stripFields)
    )

    fun restore(incoming: AppSettings, local: AppSettings): AppSettings = withoutCredentials(incoming).copy(
        gatewayToken = local.gatewayToken, usdaApiKey = local.usdaApiKey,
        gatewayUrl = if (local.gatewayToken.isNotEmpty()) local.gatewayUrl else incoming.gatewayUrl
    )
}
