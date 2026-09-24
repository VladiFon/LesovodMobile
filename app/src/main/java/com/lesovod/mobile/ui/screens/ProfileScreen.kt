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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Button
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.lesovod.mobile.data.session.canEditTabel
import com.lesovod.mobile.data.session.canManageLesokultury
import com.lesovod.mobile.ui.auth.AuthViewModel
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.ForestError

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenInventarizatsiya: () -> Unit = {},
    onOpenPerevod: () -> Unit = {},
    onOpenTabel: () -> Unit = {},
    viewModel: AuthViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsState()
    val context = LocalContext.current
    val badgeManager = remember { NotificationsBadgeManager.getInstance(context) }
    val unreadCount by badgeManager.unreadCount.collectAsState()
    val canManageLesokultury = session?.role?.canManageLesokultury == true
    val canEditTabel = session?.role?.canEditTabel == true

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
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }

            Text(
                session?.fio?.takeIf { it.isNotBlank() } ?: "Сотрудник",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                session?.role?.displayName ?: "Должность не указана",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileRow("Логин", session?.login?.takeIf { it.isNotBlank() } ?: "—")
                    ProfileRow("Должность", session?.dolzhnost?.takeIf { it.isNotBlank() } ?: "—")
                }
            }

            MapSettingsCard()

            OutlinedButton(
                onClick = onOpenNotifications,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    if (unreadCount > 0) "Уведомления ($unreadCount)" else "Уведомления",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (canManageLesokultury) {
                OutlinedButton(
                    onClick = onOpenInventarizatsiya,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Forest, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Инвентаризация лесных культур", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onOpenPerevod,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Перевод лесных культур", modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (canEditTabel) {
                OutlinedButton(
                    onClick = onOpenTabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Табель — ручной ввод", modifier = Modifier.padding(start = 8.dp))
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestError),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
            ) {
                Text("Выйти")
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Карта", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(top = 12.dp),
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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (downloadingThis) {
                LinearProgressIndicator(
                    progress = { download.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                Text(
                    "Загрузка: ${(download.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(onClick = viewModel::cancelDownload, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Отменить")
                }
            } else {
                Button(
                    onClick = viewModel::download,
                    enabled = state.defaultLesnichestvo != null && !download.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (state.lastDownloadAt != null) "Обновить карту" else "Скачать карту", modifier = Modifier.padding(start = 8.dp))
                }
            }

            state.lastDownloadAt?.let {
                Text(
                    "Скачано: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            (download.error ?: state.error)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Text(
                "Сохраняются границы кварталов, выделов и делянок — они работают без интернета. Спутниковый снимок " +
                    "запоминается по мере просмотра: массовая загрузка снимков запрещена условиями поставщика. " +
                    "Таксация открывается офлайн для тех участков, которые вы уже открывали.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
