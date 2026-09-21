package com.lesovod.mobile.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Планирует обычное локальное уведомление Android на заданное время — без сервера и без сети
 * в момент срабатывания. Будильник неточный (setAndAllowWhileIdle), поэтому не требует разрешения
 * "Будильники и напоминания" (SCHEDULE_EXACT_ALARM) — для напоминания вроде "через 15 минут" этого достаточно.
 */
object NoteReminderScheduler {
    const val CHANNEL_ID = "note_reminders"
    const val EXTRA_NOTE_TEXT = "note_text"
    const val EXTRA_NOTE_ID = "note_id"

    fun schedule(context: Context, noteId: Int, noteText: String, triggerAtMillis: Long) {
        ensureChannel(context)
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
