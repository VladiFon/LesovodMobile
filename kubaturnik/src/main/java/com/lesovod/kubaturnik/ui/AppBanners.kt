package com.lesovod.kubaturnik.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lesovod.kubaturnik.update.AppUpdateManager
import com.lesovod.kubaturnik.update.UpdateState

/**
 * Плашки над кубатурником:
 *  1) «Поставьте основное приложение Лесовод» — пока Лесовод не установлен на телефоне,
 *     плашка не убирается (Влад: основное приложение ставить обязательно);
 *  2) «Доступно обновление Кубатурника» — проверка при каждом запуске.
 */
@Composable
fun AppBanners(updates: AppUpdateManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lesovodInstalled by updates.lesovodInstalled.collectAsState()
    val lesovodDownloading by updates.lesovodDownloading.collectAsState()
    val update by updates.state.collectAsState()

    LaunchedEffect(Unit) { updates.checkForUpdate() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updates.refreshLesovodInstalled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** Без разрешения «установка из этого источника» Android не даст поставить APK — сначала просим его. */
    fun withInstallPermission(action: () -> Unit) {
        if (updates.canInstallPackages()) {
            action()
        } else {
            Toast.makeText(context, "Разрешите установку приложений из Кубатурника и нажмите ещё раз", Toast.LENGTH_LONG).show()
            runCatching { context.startActivity(updates.requestInstallPermissionIntent()) }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        if (!lesovodInstalled) {
            Banner(
                title = "Поставьте основное приложение «Лесовод»",
                text = "Кубатурник — часть Лесовода. Основное приложение нужно установить обязательно: " +
                    "наряды, остатки по делянкам, карта и отчёты — там.",
                button = if (lesovodDownloading) "Скачивается…" else "Установить Лесовод",
                enabled = !lesovodDownloading,
                accent = true,
                onClick = {
                    withInstallPermission {
                        Toast.makeText(context, "Лесовод скачивается — установка откроется сама", Toast.LENGTH_LONG).show()
                        updates.downloadLesovod { message ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
            )
        }

        when (val u = update) {
            is UpdateState.Available -> Banner(
                title = "Доступно обновление ${u.info.versionName}",
                text = "Новая версия Кубатурника. Счёт и история сохранятся.",
                button = "Обновить",
                onClick = { withInstallPermission { updates.startDownload(u.info) } },
            )
            is UpdateState.Downloading -> Banner(
                title = "Обновление ${u.info.versionName} скачивается",
                text = "Когда загрузится, откроется установка.",
                button = null,
                onClick = {},
            )
            else -> Unit
        }
    }
}

@Composable
private fun Banner(
    title: String,
    text: String,
    button: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val container = if (accent) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val titleColor = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (button != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onClick, enabled = enabled) { Text(button) }
                }
            }
        }
    }
}
