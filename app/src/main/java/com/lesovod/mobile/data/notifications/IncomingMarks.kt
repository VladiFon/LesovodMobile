package com.lesovod.mobile.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lesovod.mobile.MainActivity
import com.lesovod.mobile.R
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.GeoNoteShareDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.map.GeoNoteCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Метки, которые коллеги отправили мне (сервер 05.10.2026+). Пуш-сервера у приложения нет,
 * поэтому раз в 15 минут (WorkManager, и когда приложение закрыто) и раз в минуту, пока оно
 * открыто, спрашиваем сервер и показываем обычное уведомление Android о каждой новой метке.
 * Принять / отклонить — на экране «Уведомления».
 */
object IncomingMarks {
    const val CHANNEL_ID = "incoming_marks"
    private const val PREFS = "incoming_marks"
    private const val KEY_SHOWN = "shown_share_ids"
    private const val WORK_NAME = "incoming_marks_check"

    private val _version = MutableStateFlow(0)

    /** Растёт, когда метку приняли или убрали — карта перечитывает метки, не дожидаясь кэша. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Какую версию карта уже перечитала (на весь процесс: экран карты мог быть ещё не открыт). */
    @Volatile
    var mapSeenVersion = 0

    fun markersChanged() {
        _version.value += 1
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<IncomingMarksWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Спросить сервер и показать уведомления о новых метках. Старый сервер / нет сети — молча ничего. */
    suspend fun check(context: Context) {
        val app = context.applicationContext
        val session = SessionManager.getInstance(app)
        if (session.session.value == null) return
        val shares = BotRepository(NetworkModule.api, session).listIncomingGeoNoteShares().getOrNull() ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty()
        val fresh = shares.filter { it.shareId.toString() !in shown }
        if (fresh.isEmpty()) return
        fresh.forEach { notify(app, it) }
        // помним только ещё ожидающие — множество не растёт бесконечно
        prefs.edit().putStringSet(KEY_SHOWN, shares.map { it.shareId.toString() }.toSet()).apply()
        NotificationsBadgeManager.getInstance(app).refresh()
    }

    private fun notify(context: Context, share: GeoNoteShareDto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val label = GeoNoteCategory.fromCode(share.note.kategoriya).label
        val text = listOfNotNull(
            share.note.noteText?.takeIf { it.isNotBlank() },
            share.komment?.takeIf { it.isNotBlank() },
        ).joinToString(" — ").ifEmpty { "Откройте «Уведомления», чтобы принять метку на свою карту" }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${share.fromFio ?: "Коллега"} отправил(а) метку «$label»")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + share.shareId, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Метки от коллег", NotificationManager.IMPORTANCE_HIGH))
    }

    // id уведомлений напоминаний — id заметок; этим не пересекаемся
    private const val NOTIFICATION_ID_BASE = 500_000
}

class IncomingMarksWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { IncomingMarks.check(applicationContext) }
        return Result.success()
    }
}
