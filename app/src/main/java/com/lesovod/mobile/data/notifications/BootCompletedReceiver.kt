package com.lesovod.mobile.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager сбрасывает все будильники при перезагрузке телефона — без этого приёмника
 * напоминания по заметкам тихо пропадали бы. Переставляет их заново по сохранённому списку
 * из [com.lesovod.mobile.data.local.NoteReminderStore].
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        NoteReminderScheduler.rescheduleAll(context)
    }
}
