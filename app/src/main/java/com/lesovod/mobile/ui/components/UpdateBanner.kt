package com.lesovod.mobile.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.lesovod.mobile.data.update.AppUpdateManager
import com.lesovod.mobile.data.update.UpdateState

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
    val lv = LocalLv.current
    val ink = Color(0xFF1E1D19)
    val (bg, fg) = when (s) {
        is UpdateState.Downloading -> lv.sf2 to lv.tx
        is UpdateState.Installing -> lv.gS to lv.gT
        else -> lv.a to ink
    }
    val title = when (s) {
        is UpdateState.Downloading -> if (s.percent >= 0) "Скачивается ${s.percent} %" else "Скачивается…"
        is UpdateState.Installing -> "Устанавливается само, работать можно"
        else -> "Вышла новая версия ${info.versionName}"
    }
    val sub = when {
        s is UpdateState.Available && s.problem != null -> s.problem
        s is UpdateState.Downloading -> "Версия ${info.versionName}. Поставится сама, как скачается"
        s is UpdateState.Installing -> "Если телефон спросит, нажмите «Обновить»"
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (s is UpdateState.Available) Modifier.clickable {
                    if (manager.canInstallPackages()) manager.startDownload(info)
                    else context.startActivity(manager.requestInstallPermissionIntent())
                } else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (s is UpdateState.Available) ink else fg.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (s is UpdateState.Installing) {
                    CircularProgressIndicator(color = fg, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = if (s is UpdateState.Available) lv.a else fg, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                color = fg,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp),
                modifier = Modifier.weight(1f),
            )
            if (s is UpdateState.Available) {
                Text(
                    "Обновить",
                    color = fg,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                    modifier = Modifier.padding(start = 8.dp),
                )
                IconButton(onClick = { hidden = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Позже", tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
        if (sub != null) {
            Text(
                sub,
                color = fg.copy(alpha = 0.85f),
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (s is UpdateState.Downloading) {
            val p = s.percent
            if (p >= 0) {
                LinearProgressIndicator(
                    progress = { p / 100f },
                    color = lv.gT,
                    trackColor = lv.sf3,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else {
                LinearProgressIndicator(
                    color = lv.gT,
                    trackColor = lv.sf3,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}
