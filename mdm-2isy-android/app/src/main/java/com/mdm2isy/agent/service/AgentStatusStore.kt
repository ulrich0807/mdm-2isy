package com.mdm2isy.agent.service

import android.content.Context

data class AgentStatus(
    val running: Boolean,
    val lastSyncEpochMs: Long,
    val lastMessage: String,
)

class AgentStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun load(): AgentStatus = AgentStatus(
        running = preferences.getBoolean(KEY_RUNNING, false),
        lastSyncEpochMs = preferences.getLong(KEY_LAST_SYNC, 0L),
        lastMessage = preferences.getString(KEY_LAST_MESSAGE, "") ?: "",
    )

    fun update(running: Boolean, message: String, synced: Boolean = false) {
        val editor = preferences.edit()
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_LAST_MESSAGE, message.take(MAX_MESSAGE_LENGTH))
        if (synced) editor.putLong(KEY_LAST_SYNC, System.currentTimeMillis())
        editor.apply()
    }

    private companion object {
        const val PREFERENCES = "mdm_agent_status"
        const val KEY_RUNNING = "running"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_MESSAGE = "last_message"
        const val MAX_MESSAGE_LENGTH = 500
    }
}
