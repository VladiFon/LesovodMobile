package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NoteReminderEntry(val noteId: Int, val noteText: String, val triggerAtMillis: Long)

/**
 * Список ещё не сработавших напоминаний по заметкам, как JSON в SharedPreferences.
 * AlarmManager сам по себе не переживает перезагрузку телефона, поэтому это единственное
 * место, откуда напоминания можно переставить заново (см. BootCompletedReceiver) и откуда
 * их читает экран со списком уже поставленных напоминаний.
 */
class NoteReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("note_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun list(): List<NoteReminderEntry> {
        val raw = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<NoteReminderEntry>>(raw) }.getOrDefault(emptyList())
            .sortedBy { it.triggerAtMillis }
    }

    /** Один noteId — одно активное напоминание (как и у PendingIntent-request-кода будильника). */
    @Synchronized
    fun put(entry: NoteReminderEntry) {
        save(list().filterNot { it.noteId == entry.noteId } + entry)
    }

    @Synchronized
    fun remove(noteId: Int) {
        save(list().filterNot { it.noteId == noteId })
    }

    private fun save(entries: List<NoteReminderEntry>) {
        prefs.edit { putString(KEY_REMINDERS, json.encodeToString(entries)) }
    }

    private companion object {
        const val KEY_REMINDERS = "reminders"
    }
}
