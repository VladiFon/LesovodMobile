package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.canInputProba
import com.lesovod.mobile.ui.bot.TasksViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.EmptyState
import com.lesovod.mobile.ui.components.ErrorState
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.SkeletonBlock
import com.lesovod.mobile.ui.components.SkyState
import com.lesovod.mobile.ui.components.SkyStatusPill
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Dimens
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/**
 * Экран 2 редизайна «Поляна» (docs/SCREENS.md) — «Смена», стартовый экран приложения.
 * Структура — по эталону code/screens/ShiftScreen.kt из пакета передачи дизайна:
 * TopAppBar со SkyStatusPill → hero-карточка с одним PrimaryButton → лента задач.
 * Данные и вся бизнес-логика — прежние (TasksViewModel), переписан только UI-слой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenAttendance: () -> Unit,
    onOpenProba: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    viewModel: TasksViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val session by viewModel.session.collectAsState()
    val canInputProba = session?.role?.canInputProba == true

    val context = LocalContext.current
    val queueManager = remember { OfflineQueueManager.getInstance(context) }
    val pending by queueManager.pending.collectAsState()
    val isSyncing by queueManager.isSyncing.collectAsState()
    val badgeManager = remember { NotificationsBadgeManager.getInstance(context) }
    val unreadCount by badgeManager.unreadCount.collectAsState()
    val skyState = when {
        isSyncing -> SkyState.Syncing
        pending.isEmpty() -> SkyState.Sun
        else -> SkyState.Cloud(pending.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Смена", style = MaterialTheme.typography.titleSmall) },
                actions = {
                    SkyStatusPill(
                        state = skyState,
                        // «Не отправлено: N» — нажатие сразу пробует отправить очередь
                        onClick = queueManager::retryNow,
                        modifier = Modifier.padding(end = Spacing.s),
                    )
                    BadgedBox(badge = { if (unreadCount > 0) Badge { Text("${unreadCount.coerceAtMost(99)}") } }) {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Rounded.NotificationsNone, contentDescription = "Уведомления")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = {
                viewModel.loadTasks()
                queueManager.retryNow()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        HeroCard(onClick = onOpenAttendance)
                        if (canInputProba) {
                            ProbaRow(onClick = onOpenProba)
                        }
                    }
                }

                item {
                    Text(
                        "СЕГОДНЯ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.error != null) {
                    item {
                        ErrorState(
                            title = "Не удалось загрузить задачи",
                            subtitle = state.error.orEmpty(),
                            retryLabel = "Повторить",
                            onRetry = viewModel::loadTasks,
                        )
                    }
                }

                if (state.isLoading && state.items.isEmpty()) {
                    items(3) { SkeletonBlock() }
                } else if (state.items.isEmpty() && state.error == null) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.TaskAlt,
                            title = "Активных задач нет",
                            subtitle = "Новые задачи появятся здесь, как только мастер их назначит",
                        )
                    }
                }

                items(state.items, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        isCompleting = state.completingId == task.id,
                        isQueued = task.id in state.queuedIds,
                        onComplete = { viewModel.completeTask(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant),
                ),
                RoundedCornerShape(28.dp),
            )
            .softCard(RoundedCornerShape(28.dp), filled = false)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            "СЛЕДУЮЩЕЕ ДЕЙСТВИЕ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Отметиться на смене", style = MaterialTheme.typography.titleLarge)
        Text(
            "Работаю / не работаю / больничный",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Отметиться", onClick = onClick, icon = {
            Icon(Icons.Rounded.HowToReg, contentDescription = null)
        })
    }
}

@Composable
private fun ProbaRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(Icons.Rounded.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text("Проба рубок ухода", style = MaterialTheme.typography.titleSmall)
            Text(
                "Укладки и расчёт запаса",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: WorkPlanItemDto,
    isCompleting: Boolean,
    isQueued: Boolean,
    onComplete: () -> Unit,
) {
    val stripeColor = if (isQueued) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(Spacing.l),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(if (isQueued) 64.dp else Dimens.rowHeight - Spacing.l)
                .background(stripeColor, RoundedCornerShape(3.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(task.zadacha, style = MaterialTheme.typography.titleSmall)

            val place = listOfNotNull(
                task.kvartal?.let { "кв. $it" },
                task.vydel?.let { "выд. $it" },
                task.lesnichestvo,
            ).joinToString(", ")
            if (place.isNotEmpty()) {
                Text(
                    place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                task.data,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (isQueued) {
                StatusChip(
                    text = "Нет сети — отправится сама",
                    tone = ChipTone.WARN,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            } else {
                SecondaryButton(
                    text = "Готово",
                    onClick = onComplete,
                    enabled = !isCompleting,
                    modifier = Modifier.padding(top = Spacing.m),
                    icon = if (isCompleting) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                )
            }
        }
    }
}
