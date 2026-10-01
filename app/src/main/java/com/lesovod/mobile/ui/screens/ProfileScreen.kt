package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import com.lesovod.mobile.data.local.PendingActionType
import com.lesovod.mobile.data.local.ThemeMode
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.ui.components.SendStatusChip
import com.lesovod.mobile.data.local.ThemePrefs
import com.lesovod.mobile.BuildConfig
import com.lesovod.mobile.data.update.AppUpdateManager
import com.lesovod.mobile.data.update.UpdateState
import com.lesovod.mobile.ui.map.MapSettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.auth.AuthViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.DestructiveButton
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/**
 * Экран 11 редизайна «Поляна» (docs/SCREENS.md) — «Профиль»: личные данные, карта и выход.
 * Уведомления и разделы, доступные не всем ролям (Инвентаризация/Перевод лесных культур,
 * Проба, Остатки, Кубатурник), переехали в «Ещё» ([MoreScreen]) вместе с реструктуризацией
 * нижней навигации — здесь их дублировать не нужно.
 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenTitle("Профиль")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }

            Text(
                session?.fio?.takeIf { it.isNotBlank() } ?: "Сотрудник",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Text(
                session?.role?.displayName ?: "Должность не указана",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softCard()
                    .padding(Spacing.l)
                    .padding(top = Spacing.xl),
            ) {
                ProfileRow("Логин", session?.login?.takeIf { it.isNotBlank() } ?: "—")
                ProfileRow("Должность", session?.dolzhnost?.takeIf { it.isNotBlank() } ?: "—")
            }

            ThemeCard()

            QueueCard()

            MapSettingsCard()

            UpdateCard()

            DestructiveButton(
                text = "Выйти",
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xl),
            )
        }
    }
}

/**
 * «Не отправлено: N» — сколько действий (отчёты, заметки, метки, отметки…) лежит в офлайн-очереди,
 * с кнопкой «Отправить сейчас». Пустая очередь — короткая строка «Всё отправлено».
 */
@Composable
private fun QueueCard() {
    val context = LocalContext.current
    val queueManager = remember { OfflineQueueManager.getInstance(context) }
    val pending by queueManager.pending.collectAsState()
    val isSyncing by queueManager.isSyncing.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(Spacing.l)
            .padding(top = Spacing.l),
    ) {
        Text("Отправка данных", style = MaterialTheme.typography.titleSmall)
        if (pending.isEmpty()) {
            SendStatusChip(pending = false, modifier = Modifier.padding(top = Spacing.m))
            Text(
                "Всё отправлено на сервер",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        } else {
            StatusChip(text = "Не отправлено: ${pending.size}", tone = ChipTone.WARN, modifier = Modifier.padding(top = Spacing.m))
            val byType = pending.groupingBy { it.type.labelRu() }.eachCount()
            Text(
                byType.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            pending.mapNotNull { it.lastError }.lastOrNull()?.let {
                Text(
                    "Последняя ошибка: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            PrimaryButton(
                text = if (isSyncing) "Отправляем…" else "Отправить сейчас",
                onClick = queueManager::retryNow,
                enabled = !isSyncing,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}

private fun PendingActionType.labelRu(): String = when (this) {
    PendingActionType.REPORT -> "отчёты"
    PendingActionType.BREAKDOWN -> "поломки"
    PendingActionType.ATTENDANCE -> "отметки"
    PendingActionType.TASK_COMPLETE -> "задачи"
    PendingActionType.TRELEVKA -> "трелёвка"
    PendingActionType.PROBA -> "пробы"
    PendingActionType.NOTE -> "заметки"
    PendingActionType.LESOKULTURY -> "лесные культуры"
    PendingActionType.GEO_NOTE -> "метки на карте"
}

/** Выбор темы: «Тёмная» (по умолчанию) / «Светлая» / «Как в системе» — применяется сразу. */
@Composable
private fun ThemeCard() {
    val context = LocalContext.current
    val themePrefs = remember { ThemePrefs.getInstance(context) }
    val mode by themePrefs.mode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(Spacing.l)
            .padding(top = Spacing.l),
    ) {
        Text("Тема", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier
                .padding(top = Spacing.m)
                .horizontalScroll(rememberScrollState()),
        ) {
            ThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { themePrefs.setMode(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            "Тёмная тема лучше читается в лесу и бережёт батарею",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * «О приложении»: обновления вне Google Play — приложение сверяется с последним GitHub
 * Release репозитория и, если он новее, скачивает и ставит APK через системный
 * `DownloadManager` (см. [com.lesovod.mobile.data.update.AppUpdateManager]).
 */
@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val manager = remember { AppUpdateManager.getInstance(context) }
    val state by manager.state.collectAsState()

    LaunchedEffect(Unit) { manager.checkForUpdate() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(Spacing.l)
            .padding(top = Spacing.l),
    ) {
        Text("О приложении", style = MaterialTheme.typography.titleSmall)
        Text(
            "Версия ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        when (val s = state) {
            is UpdateState.Checking -> {
                Row(modifier = Modifier.padding(top = Spacing.m)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Проверка обновлений…",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
            is UpdateState.Available -> {
                Text(
                    "Доступно обновление до версии ${s.info.versionName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                PrimaryButton(
                    text = "Скачать обновление",
                    onClick = {
                        if (manager.canInstallPackages()) {
                            manager.startDownload(s.info)
                        } else {
                            context.startActivity(manager.requestInstallPermissionIntent())
                        }
                    },
                    icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
            is UpdateState.Downloading -> {
                Row(modifier = Modifier.padding(top = Spacing.m)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Загрузка версии ${s.info.versionName}… появится уведомление по завершении",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
            is UpdateState.Error -> {
                StatusChip(text = s.message, tone = ChipTone.ERROR, modifier = Modifier.padding(top = Spacing.m))
            }
            UpdateState.UpToDate -> {
                Text(
                    "У вас последняя версия",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
            UpdateState.Idle -> Unit
        }
    }
}

/** Лесничество по умолчанию (карта откроется сразу на нём) и скачивание карты на устройство. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapSettingsCard(viewModel: MapSettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val download = state.download
    val downloadingThis = download.running && download.lesnichestvo == state.defaultLesnichestvo

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(Spacing.l)
            .padding(top = Spacing.l),
    ) {
        Text("Карта", style = MaterialTheme.typography.titleSmall)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.padding(top = Spacing.m),
        ) {
            OutlinedTextField(
                value = state.defaultLesnichestvo ?: "Не выбрано",
                onValueChange = {},
                readOnly = true,
                label = { Text("Лесничество по умолчанию") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.lesnichestva.keys.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            viewModel.setDefault(name)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            "Карта будет открываться сразу на этом лесничестве",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        if (downloadingThis) {
            LinearProgressIndicator(
                progress = { download.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.l),
            )
            Text(
                "Загрузка: ${(download.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SecondaryButton(text = "Отменить", onClick = viewModel::cancelDownload, modifier = Modifier.padding(top = Spacing.s))
        } else {
            PrimaryButton(
                text = if (state.lastDownloadAt != null) "Обновить карту" else "Скачать карту",
                onClick = viewModel::download,
                enabled = state.defaultLesnichestvo != null && !download.running,
                icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier.padding(top = Spacing.l),
            )
        }

        state.lastDownloadAt?.let {
            Text(
                "Скачано: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        (download.error ?: state.error)?.let {
            StatusChip(text = it, tone = ChipTone.ERROR, modifier = Modifier.padding(top = Spacing.s))
        }

        Text(
            "Сохраняются границы кварталов, выделов и делянок — они работают без интернета. Спутниковый снимок " +
                "запоминается по мере просмотра: массовая загрузка снимков запрещена условиями поставщика. " +
                "Таксация открывается офлайн для тех участков, которые вы уже открывали.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}
