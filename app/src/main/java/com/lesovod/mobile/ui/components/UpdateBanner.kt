package com.lesovod.mobile.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lesovod.mobile.BuildConfig
import com.lesovod.mobile.data.update.AppUpdateManager
import com.lesovod.mobile.data.update.UpdateState
import com.lesovod.mobile.ui.theme.Spacing

/**
 * Плашка «Вышла новая версия» на главном экране (03.11.2026): раньше обновление было видно
 * только в Профиле, и многие его не замечали. Кнопка «Обновить» сразу качает APK.
 * «Позже» прячет плашку до следующего запуска приложения.
 * Заодно один раз просит разрешение на уведомления (Android 13+) — без него не придёт
 * и системное уведомление о новой версии.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { AppUpdateManager.getInstance(context) }
    val state by manager.state.collectAsState()
    var hidden by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        manager.checkQuietly()
        val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notifications", false)
        ) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val s = state
    if (hidden) return
    val info = when (s) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Installing -> s.info
        else -> return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
            .padding(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Вышла новая версия ${info.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    when {
                        s is UpdateState.Downloading && s.percent >= 0 -> "Скачивается: ${s.percent}%. Установка начнётся сама"
                        s is UpdateState.Downloading -> "Скачивается… установка начнётся сама"
                        s is UpdateState.Installing -> "Устанавливается. Если телефон спросит, нажмите «Обновить»"
                        s is UpdateState.Available && s.problem != null -> s.problem
                        else -> "У вас ${BuildConfig.VERSION_NAME}. Нажмите «Обновить», дальше всё поставится само."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
        if (s is UpdateState.Available) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(
                    onClick = {
                        if (manager.canInstallPackages()) manager.startDownload(info)
                        else context.startActivity(manager.requestInstallPermissionIntent())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("Обновить", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { hidden = true }) { Text("Позже", color = MaterialTheme.colorScheme.onPrimary) }
            }
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}
