package com.lesovod.mobile.data.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lesovod.mobile.R
import com.lesovod.mobile.data.local.NoteReminderStore

/** Срабатывает по будильнику от [NoteReminderScheduler] и показывает уведомление с текстом заметки. */
class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_TEXT) ?: return
        val noteId = intent.getIntExtra(NoteReminderScheduler.EXTRA_NOTE_ID, 0)
        NoteReminderStore(context).remove(noteId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notification = NotificationCompat.Builder(context, NoteReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Напоминание о заметке")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(noteId, notification)
    }
}
