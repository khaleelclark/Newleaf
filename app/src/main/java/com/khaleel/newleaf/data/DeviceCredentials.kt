package com.khaleel.newleaf.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Two independent Keystore keys; only ciphertext is persisted in device preferences. */
internal class DeviceCredentials(context: Context) {
    private val prefs = context.getSharedPreferences("newleaf_device_credentials", Context.MODE_PRIVATE)
    private enum class Slot(val preference: String, val alias: String) {
        GATEWAY("token", "newleaf_gateway_token_v1"),
        USDA("usda_api_key", "newleaf_usda_api_key_v1")
    }
    private fun key(slot: Slot): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(slot.alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(slot.alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String = read(Slot.GATEWAY)
    fun readUsdaApiKey(): String = read(Slot.USDA)
    private fun read(slot: Slot): String {
        val encoded = prefs.getString(slot.preference, null) ?: return ""
        val pieces = encoded.split(":")
        require(pieces.size == 2) { "Invalid credential storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(slot), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    private fun encrypt(slot: Slot, value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(slot))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    fun save(gatewayToken: String, usdaApiKey: String) {
        // Prepare both ciphertexts before committing either value. Keep the legacy gateway slot.
        val gateway = gatewayToken.takeIf { it.isNotEmpty() }?.let { encrypt(Slot.GATEWAY, it) }
        val usda = usdaApiKey.takeIf { it.isNotEmpty() }?.let { encrypt(Slot.USDA, it) }
        check(prefs.edit().putString(Slot.GATEWAY.preference, gateway)
            .putString(Slot.USDA.preference, usda).commit()) { "Credential write failed" }
    }
}
