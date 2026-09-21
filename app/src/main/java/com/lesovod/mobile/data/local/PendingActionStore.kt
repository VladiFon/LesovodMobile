package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Хранит список действий, ожидающих отправки, как JSON в SharedPreferences. */
class PendingActionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pending_actions", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun list(): List<PendingAction> {
        val raw = prefs.getString(KEY_ACTIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingAction>>(raw) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(action: PendingAction) {
        save(list() + action)
    }

    @Synchronized
    fun update(action: PendingAction) {
        save(list().map { if (it.id == action.id) action else it })
    }

    @Synchronized
    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    private fun save(actions: List<PendingAction>) {
        prefs.edit { putString(KEY_ACTIONS, json.encodeToString(actions)) }
    }

    private companion object {
        const val KEY_ACTIONS = "actions"
    }
}
