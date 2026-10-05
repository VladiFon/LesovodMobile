package com.lesovod.mobile.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Экран 10 редизайна «Поляна» (docs/SCREENS.md) — «Уведомления». */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenMap: () -> Unit = {},
    viewModel: NotificationsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Уведомления", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state.items.any { !it.isRead }) {
                TextButton(onClick = viewModel::markAllRead) {
                    Text("Отметить всё прочитанным")
                }
            }
        }

        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
            )
        }
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(modifier = Modifier.fillMaxSize().padding(Spacing.l), contentAlignment = Alignment.Center) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.items.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(Spacing.l), contentAlignment = Alignment.Center) {
                Text("Уведомлений нет", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.items, key = { it.id }) { item ->
                    NotificationCard(
                        item = item,
                        onClick = {
                            if (item.type != NotificationType.METKA) viewModel.markRead(item.id)
                            if (item.type == NotificationType.NOTE || item.type == NotificationType.OTVET_NA_ZAMETKU) onOpenNote()
                            if (item.type == NotificationType.METKA && item.answered == true) onOpenMap()
                        },
                        onAnswerMark = { accept -> viewModel.answerMark(item, accept) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onClick: () -> Unit, onAnswerMark: (Boolean) -> Unit = {}) {
    val containerColor = if (item.isRead) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, MaterialTheme.shapes.medium)
            .softCard(filled = false)
            .clickable(onClick = onClick)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            item.type.title()?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.text.ifBlank { "Уведомление" }, style = MaterialTheme.typography.bodyLarge)
            item.createdAt?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
            }
            if (item.type == NotificationType.METKA && item.relatedId != null) {
                when (item.answered) {
                    true -> Text("Принята — нажмите, чтобы открыть карту", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.xs))
                    false -> Text("Отклонена", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                    null -> if (!item.isRead) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.xs)) {
                        Button(onClick = { onAnswerMark(true) }) { Text("Принять") }
                        OutlinedButton(onClick = { onAnswerMark(false) }) { Text("Отклонить") }
                    }
                }
            }
        }
        if (!item.isRead) {
            Box(modifier = Modifier.size(9.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        }
    }
}

/** Подпись над текстом для новых типов событий — чтобы было видно, о чём уведомление. */
private fun NotificationType.title(): String? = when (this) {
    NotificationType.OSVOENIE -> "Освоение делянки"
    NotificationType.SROK -> "Срок"
    NotificationType.OTVET_NA_ZAMETKU -> "Ответ на заметку"
    NotificationType.METKA -> "Метка от коллеги"
    else -> null
}

private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.BREAKDOWN -> Icons.Filled.Build
    NotificationType.NOTE -> Icons.Filled.Message
    NotificationType.PROBA -> Icons.Filled.Forest
    NotificationType.TRELEVKA -> Icons.Filled.LocalShipping
    NotificationType.OSVOENIE -> Icons.Filled.Warning
    NotificationType.SROK -> Icons.Filled.Schedule
    NotificationType.OTVET_NA_ZAMETKU -> Icons.Filled.Forum
    NotificationType.METKA -> Icons.Filled.Place
    NotificationType.OTHER -> Icons.Filled.Info
}
