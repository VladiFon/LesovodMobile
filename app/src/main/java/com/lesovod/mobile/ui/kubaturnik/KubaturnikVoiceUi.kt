package com.lesovod.mobile.ui.kubaturnik

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lesovod.mobile.data.voice.VoskModelManager

/**
 * Кнопка микрофона в шапке «Счёта». Сама спрашивает разрешение на микрофон; если модели речи
 * ещё нет — ViewModel откроет окно загрузки.
 */
@Composable
fun VoiceMicButton(voice: VoiceUiState, viewModel: KubaturnikViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoice()
    }
    val active = voice.listening || voice.starting
    IconButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            when {
                active -> viewModel.stopVoice()
                granted -> viewModel.startVoice()
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        colors = if (active) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        },
    ) {
        if (voice.starting) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = if (active) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (active) "Выключить голосовой ввод" else "Голосовой ввод диаметров",
            )
        }
    }
}

/**
 * Плашка голосового режима под шапкой: куда сейчас пишем (сорт · машина/прицеп), что слышно прямо
 * сейчас и последние распознанные фразы с результатом. Пока она на экране, экран не гаснет.
 */
@Composable
fun VoiceBar(state: KubaturnikUiState, voice: VoiceUiState, viewModel: KubaturnikViewModel) {
    val active = voice.listening || voice.starting
    if (!active && voice.error == null) return

    if (active) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    Surface(
        color = if (voice.error != null) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            voice.error?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::dismissVoiceError) { Text("ОК") }
                }
            }
            if (active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (voice.starting) "🎙 Включаю…" else "🎙 Слушаю → ${state.activeSort} · ${state.destination.label}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = voice.speakBack,
                        onClick = { viewModel.setVoiceSpeakBack(!voice.speakBack) },
                        label = { Text("Озвучка", fontSize = 12.sp) },
                    )
                }
                Text(
                    voice.partial.ifEmpty { "«24», «24 две штуки», «удали 24», «прицеп», «сорт два», «отмена», «итого»" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (voice.partial.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                voice.log.take(3).forEachIndexed { index, entry ->
                    Row {
                        Text(
                            "«${entry.heard}»",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = if (index == 0) 1f else 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            (if (entry.ok) "✓ " else "✗ ") + entry.result,
                            fontSize = 12.sp,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (entry.ok) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Первый запуск голосового ввода: модель речи качается один раз, дальше всё работает без интернета. */
@Composable
fun VoiceModelDialog(voice: VoiceUiState, viewModel: KubaturnikViewModel) {
    if (!voice.showModelDialog && !voice.downloading) return
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importVoiceModel(uri)
    }
    AlertDialog(
        onDismissRequest = { if (!voice.downloading) viewModel.showVoiceModelDialog(false) },
        title = { Text("Голосовой ввод") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Для распознавания речи без интернета нужна русская модель Vosk " +
                        "(~${VoskModelManager.MODEL_SIZE_MB} МБ). Качается один раз — лучше по Wi‑Fi, " +
                        "дальше в лесу работает без связи.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (voice.downloading) {
                    if (voice.downloadProgress >= 0f) {
                        LinearProgressIndicator(progress = { voice.downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text("Загружаю… ${(voice.downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    voice.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Не качается? Скачай на компьютере vosk-model-small-ru-0.22.zip с alphacephei.com/vosk/models, " +
                            "перекинь на телефон и нажми «Из файла».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!voice.downloading) {
                TextButton(onClick = viewModel::downloadVoiceModel) { Text("Скачать") }
            }
        },
        dismissButton = {
            if (!voice.downloading) {
                Row {
                    TextButton(onClick = { pickFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                        Text("Из файла")
                    }
                    TextButton(onClick = { viewModel.showVoiceModelDialog(false) }) { Text("Отмена") }
                }
            }
        },
    )
}

/** Микрофон выключается, когда уходим с экрана или сворачиваем приложение. */
@Composable
fun VoiceLifecycleGuard(viewModel: KubaturnikViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopVoice()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopVoice()
        }
    }
}
