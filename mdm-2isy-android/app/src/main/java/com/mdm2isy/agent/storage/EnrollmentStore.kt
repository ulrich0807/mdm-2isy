package com.mdm2isy.agent.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EnrollmentSession(
    val apiUrl: String,
    val deviceId: String,
    val deviceToken: String,
)

class EnrollmentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun save(apiUrl: String, deviceId: String, deviceToken: String) {
        require(apiUrl.isNotBlank()) { "The API URL is required." }
        require(deviceId.isNotBlank()) { "The public device identifier is required." }
        require(deviceToken.startsWith("mdm_device_")) { "Unexpected device token format." }

        val encryptedToken = encrypt(deviceToken)
        check(
            preferences.edit()
                .putString(KEY_API_URL, apiUrl.trim().trimEnd('/') + "/")
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_DEVICE_TOKEN, encryptedToken)
                .commit(),
        ) { "Unable to persist the enrollment identity." }
    }

    @Synchronized
    fun load(): EnrollmentSession? {
        val apiUrl = preferences.getString(KEY_API_URL, null) ?: return null
        val deviceId = preferences.getString(KEY_DEVICE_ID, null) ?: return null
        val encryptedToken = preferences.getString(KEY_DEVICE_TOKEN, null) ?: return null
        val token = runCatching { decrypt(encryptedToken) }.getOrNull() ?: return null

        return EnrollmentSession(apiUrl, deviceId, token)
    }

    fun isEnrolled(): Boolean = load() != null

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit()) {
            "Unable to clear the enrollment identity."
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return listOf(cipher.iv, encrypted).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split('.', limit = 2)
        require(parts.size == 2) { "Invalid encrypted token envelope." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))

        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "mdm_enrollment"
        const val KEY_API_URL = "api_url"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_TOKEN = "device_token_encrypted"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "mdm_2isy_device_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

