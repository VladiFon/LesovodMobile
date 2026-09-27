package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.data.session.canManageLesokultury
import com.lesovod.mobile.data.session.canViewProba
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.Dimens
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/**
 * Экран «Ещё» (docs/SCREENS.md, раздел «Навигация»): вкладка нижней навигации —
 * профиль, уведомления и разделы, доступные не всем ролям (Проба рубок ухода, Лесные
 * культуры), плюс отчёт, у которого больше нет отдельной вкладки. Остатки и Кубатурник
 * теперь свои вкладки нижней навигации (см. [com.lesovod.mobile.ui.navigation.bottomNavItems])
 * и здесь не дублируются.
 */
@Composable
fun MoreScreen(
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWorkReport: () -> Unit,
    onOpenProba: () -> Unit,
    onOpenInventarizatsiya: () -> Unit,
    onOpenPerevod: () -> Unit,
) {
    val context = LocalContext.current
    val session by SessionManager.getInstance(context).session.collectAsState()
    val badgeManager = remember { NotificationsBadgeManager.getInstance(context) }
    val unreadCount by badgeManager.unreadCount.collectAsState()
    val role = session?.role

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTitle("Ещё")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MoreRow(icon = Icons.Filled.Person, label = "Профиль", onClick = onOpenProfile)
            MoreRow(
                icon = Icons.Filled.Notifications,
                label = "Уведомления",
                badgeCount = unreadCount,
                onClick = onOpenNotifications,
            )
            MoreRow(icon = Icons.Filled.PostAdd, label = "Отчёт", onClick = onOpenWorkReport)

            if (role?.canViewProba == true) {
                MoreRow(icon = Icons.Filled.Science, label = "Проба рубок ухода", onClick = onOpenProba)
            }
            if (role?.canManageLesokultury == true) {
                MoreRow(icon = Icons.Filled.Forest, label = "Инвентаризация лесных культур", onClick = onOpenInventarizatsiya)
                MoreRow(icon = Icons.Filled.SwapHoriz, label = "Перевод лесных культур", onClick = onOpenPerevod)
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l)
            .heightIn(min = Dimens.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
