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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import com.lesovod.mobile.data.session.canReportBreakdown
import com.lesovod.mobile.data.session.canSeeStock
import com.lesovod.mobile.data.session.canTrelevka
import kotlinx.coroutines.launch
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
    onOpenWorkReport: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenTrelevka: () -> Unit = {},
    onOpenBreakdown: () -> Unit = {},
    viewModel: TasksViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val session by viewModel.session.collectAsState()
    val role = session?.role
    val canInputProba = role?.canInputProba == true
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Главные действия своей роли — крупными кнопками вверху (3–4 штуки), остальное ниже как раньше.
    val homeActions = buildList {
        add(HomeAction("Отметиться", Icons.Rounded.HowToReg, onOpenAttendance))
        add(HomeAction("Мои задачи", Icons.Rounded.TaskAlt) { scope.launch { listState.animateScrollToItem(1) } })
        if (role?.canSeeStock == true) add(HomeAction("Остатки", Icons.Rounded.Inventory2, onOpenStock))
        add(HomeAction("Отчёт за день", Icons.Rounded.PostAdd, onOpenWorkReport))
        if (size < 4 && role?.canTrelevka == true) add(HomeAction("Трелёвка", Icons.Rounded.LocalShipping, onOpenTrelevka))
        if (size < 4 && role?.canReportBreakdown == true) add(HomeAction("Поломка", Icons.Rounded.Build, onOpenBreakdown))
        if (size < 4 && canInputProba) add(HomeAction("Проба", Icons.Rounded.Forest, onOpenProba))
    }

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
                state = listState,
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        HomeActionsGrid(homeActions)
                        if (canInputProba && homeActions.none { it.label == "Проба" }) {
                            ProbaRow(onClick = onOpenProba)
                        }
                    }
                }

                item {
                    Column {
                        state.offlineStamp?.let {
                            StatusChip(text = it, tone = ChipTone.WARN, modifier = Modifier.padding(bottom = Spacing.s))
                        }
                        Text(
                            "СЕГОДНЯ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

private data class HomeAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Крупные кнопки главного экрана — по две в ряд, высокие, чтобы попадать пальцем в перчатке. */
@Composable
private fun HomeActionsGrid(actions: List<HomeAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        actions.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { index, action ->
                    // первая кнопка («Отметиться») — главная, выделена цветом
                    val primary = rowIndex == 0 && index == 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .background(
                                if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(20.dp),
                            )
                            .softCard(RoundedCornerShape(20.dp), filled = false)
                            .clickable(onClick = action.onClick)
                            .padding(Spacing.s),
                    ) {
                        val tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
                        Text(
                            action.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
