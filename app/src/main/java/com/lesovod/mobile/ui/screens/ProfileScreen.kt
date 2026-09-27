package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.session.canManageLesokultury
import com.lesovod.mobile.ui.auth.AuthViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.DestructiveButton
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Экран 11 редизайна «Поляна» (docs/SCREENS.md) — «Профиль». */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenInventarizatsiya: () -> Unit = {},
    onOpenPerevod: () -> Unit = {},
    viewModel: AuthViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsState()
    val context = LocalContext.current
    val badgeManager = remember { NotificationsBadgeManager.getInstance(context) }
    val unreadCount by badgeManager.unreadCount.collectAsState()
    val canManageLesokultury = session?.role?.canManageLesokultury == true

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

            MapSettingsCard()

            SecondaryButton(
                text = if (unreadCount > 0) "Уведомления ($unreadCount)" else "Уведомления",
                onClick = onOpenNotifications,
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                modifier = Modifier.padding(top = Spacing.l),
            )

            if (canManageLesokultury) {
                SecondaryButton(
                    text = "Инвентаризация лесных культур",
                    onClick = onOpenInventarizatsiya,
                    icon = { Icon(Icons.Filled.Forest, contentDescription = null) },
                    modifier = Modifier.padding(top = Spacing.s),
                )
                SecondaryButton(
                    text = "Перевод лесных культур",
                    onClick = onOpenPerevod,
                    icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }

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

@Composable
private fun ProfileRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
