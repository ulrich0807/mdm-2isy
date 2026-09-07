package com.mdm2isy.agent.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LocalCommandState {
    RECEIVED,
    ACKNOWLEDGED,
    EXECUTING,
    RESULT_PENDING,
    FINAL,
    EXPIRED,
    QUARANTINED,
}

data class JournalEntry(
    val publicId: String,
    val type: String,
    val payloadJson: String,
    val expiresAt: String,
    val state: LocalCommandState,
    val finalRequestJson: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

class CommandJournal(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun recordReceived(
        publicId: String,
        type: String,
        payloadJson: String,
        expiresAt: String,
    ): JournalEntry {
        val entries = readEntries().toMutableList()
        val existingIndex = entries.indexOfFirst {
            it.publicId.equals(publicId, ignoreCase = true)
        }
        if (existingIndex >= 0) {
            val existing = entries[existingIndex]
            if (
                existing.type != type ||
                existing.payloadJson != payloadJson ||
                existing.expiresAt != expiresAt
            ) {
                val quarantined = existing.copy(
                    state = LocalCommandState.QUARANTINED,
                    finalRequestJson = null,
                )
                entries[existingIndex] = quarantined
                writeEntries(entries)
                return quarantined
            }
            return existing
        }

        val entry = JournalEntry(
            publicId = publicId,
            type = type,
            payloadJson = payloadJson,
            expiresAt = expiresAt,
            state = LocalCommandState.RECEIVED,
        )
        entries += entry
        writeEntries(prune(entries))
        return entry
    }

    @Synchronized
    fun find(publicId: String): JournalEntry? = readEntries().firstOrNull {
        it.publicId == publicId
    }

    @Synchronized
    fun actionable(): List<JournalEntry> = readEntries().filter {
        it.state !in setOf(
            LocalCommandState.FINAL,
            LocalCommandState.EXPIRED,
            LocalCommandState.QUARANTINED,
        )
    }

    fun markAcknowledged(publicId: String): JournalEntry? = update(publicId) {
        it.copy(state = LocalCommandState.ACKNOWLEDGED)
    }

    fun markExecuting(publicId: String): JournalEntry? = update(publicId) {
        it.copy(state = LocalCommandState.EXECUTING)
    }

    fun storeFinalRequest(publicId: String, requestJson: String): JournalEntry? = update(publicId) {
        it.copy(
            state = LocalCommandState.RESULT_PENDING,
            finalRequestJson = requestJson,
        )
    }

    fun markFinal(publicId: String): JournalEntry? = update(publicId) {
        it.copy(state = LocalCommandState.FINAL, finalRequestJson = null)
    }

    fun markExpired(publicId: String): JournalEntry? = update(publicId) {
        it.copy(state = LocalCommandState.EXPIRED, finalRequestJson = null)
    }

    fun quarantine(publicId: String): JournalEntry? = update(publicId) {
        it.copy(state = LocalCommandState.QUARANTINED, finalRequestJson = null)
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().remove(KEY_ENTRIES).commit()) {
            "Unable to clear the command journal."
        }
    }

    @Synchronized
    private fun update(
        publicId: String,
        transform: (JournalEntry) -> JournalEntry,
    ): JournalEntry? {
        val entries = readEntries().toMutableList()
        val index = entries.indexOfFirst { it.publicId == publicId }
        if (index < 0) return null

        val updated = transform(entries[index])
        entries[index] = updated
        writeEntries(entries)
        return updated
    }

    private fun readEntries(): List<JournalEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    add(
                        JournalEntry(
                            publicId = json.getString("public_id"),
                            type = json.getString("type"),
                            payloadJson = json.getString("payload_json"),
                            expiresAt = json.getString("expires_at"),
                            state = LocalCommandState.valueOf(json.getString("state")),
                            finalRequestJson = json.optString("final_request_json")
                                .takeIf(String::isNotBlank),
                            createdAtEpochMs = json.optLong("created_at_epoch_ms", 0L),
                        ),
                    )
                }
            }
        } catch (exception: Exception) {
            // Fail closed: silently returning an empty journal could cause a
            // destructive command to be delivered and executed a second time.
            throw IllegalStateException(
                "The local command journal is corrupted; command execution is suspended.",
                exception,
            )
        }
    }

    private fun writeEntries(entries: List<JournalEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("public_id", entry.publicId)
                    .put("type", entry.type)
                    .put("payload_json", entry.payloadJson)
                    .put("expires_at", entry.expiresAt)
                    .put("state", entry.state.name)
                    .put("final_request_json", entry.finalRequestJson ?: "")
                    .put("created_at_epoch_ms", entry.createdAtEpochMs),
            )
        }
        check(preferences.edit().putString(KEY_ENTRIES, array.toString()).commit()) {
            "Unable to persist the command journal."
        }
    }

    private fun prune(entries: List<JournalEntry>): List<JournalEntry> {
        if (entries.size <= MAX_ENTRIES) return entries

        val removableIds = entries
            .filter {
                it.state in setOf(
                    LocalCommandState.FINAL,
                    LocalCommandState.EXPIRED,
                    LocalCommandState.QUARANTINED,
                )
            }
            .sortedBy(JournalEntry::createdAtEpochMs)
            .take(entries.size - MAX_ENTRIES)
            .map(JournalEntry::publicId)
            .toSet()

        return entries.filterNot { it.publicId in removableIds }
    }

    private companion object {
        const val PREFERENCES = "mdm_command_journal"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 200
    }
}
