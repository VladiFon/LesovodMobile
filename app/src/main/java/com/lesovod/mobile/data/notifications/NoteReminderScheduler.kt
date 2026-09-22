package com.lesovod.mobile.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lesovod.mobile.data.local.NoteReminderEntry
import com.lesovod.mobile.data.local.NoteReminderStore

/**
 * Планирует обычное локальное уведомление Android на заданное время — без сервера и без сети
 * в момент срабатывания. Будильник неточный (setAndAllowWhileIdle), поэтому не требует разрешения
 * "Будильники и напоминания" (SCHEDULE_EXACT_ALARM) — для напоминания вроде "через 15 минут" этого достаточно.
 *
 * Каждое напоминание также сохраняется в [NoteReminderStore]: AlarmManager не переживает
 * перезагрузку телефона сам по себе, поэтому [rescheduleAll] переставляет их заново по
 * BOOT_COMPLETED, а сохранённый список даёт экрану «Напоминания» что показывать и что отменять.
 */
object NoteReminderScheduler {
    const val CHANNEL_ID = "note_reminders"
    const val EXTRA_NOTE_TEXT = "note_text"
    const val EXTRA_NOTE_ID = "note_id"

    fun schedule(context: Context, noteId: Int, noteText: String, triggerAtMillis: Long) {
        ensureChannel(context)
        armAlarm(context, noteId, noteText, triggerAtMillis)
        NoteReminderStore(context).put(NoteReminderEntry(noteId, noteText, triggerAtMillis))
    }

    /** Снять уже поставленное напоминание — из экрана со списком напоминаний. */
    fun cancel(context: Context, noteId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId,
            Intent(context, NoteReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        NoteReminderStore(context).remove(noteId)
    }

    /** Вызывается из BootCompletedReceiver: переставляет все ещё не сработавшие напоминания заново. */
    fun rescheduleAll(context: Context) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        NoteReminderStore(context).list().forEach { entry ->
            // Если время уже прошло, пока телефон был выключен — напоминание не должно
            // молча пропасть: ставим его почти немедленно вместо того чтобы отбросить.
            armAlarm(context, entry.noteId, entry.noteText, maxOf(entry.triggerAtMillis, now + 1_000L))
        }
    }

    private fun armAlarm(context: Context, noteId: Int, noteText: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_TEXT, noteText)
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Напоминания по заметкам", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
    }
}
