package com.mdm2isy.agent.identity

import android.content.Context
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.util.UUID

class DeviceIdentity(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun stableUuid(): String {
        preferences.getString(KEY_DEVICE_UID, null)?.let { return it }

        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        val seed = if (androidId.isBlank()) {
            UUID.randomUUID().toString()
        } else {
            "${appContext.packageName}:$androidId"
        }
        val uuid = UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
            .toString()
            .lowercase()

        check(preferences.edit().putString(KEY_DEVICE_UID, uuid).commit()) {
            "Unable to persist the stable device UUID."
        }

        return uuid
    }

    private companion object {
        const val PREFERENCES = "mdm_device_identity"
        const val KEY_DEVICE_UID = "device_uid"
    }
}

