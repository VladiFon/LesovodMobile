package com.lesovod.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.lesovod.mobile.ui.theme.Dimens
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Лист плавающей центральной кнопки «+» (docs/SCREENS.md, раздел «Навигация»): та же
 * сетка быстрых действий, что и на «Смене» («Быстро»: Поломка, Трелёвка, Остаток, Заметка),
 * доступная с любого экрана. Пункты фильтруются по правам роли на месте вызова.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(actions: List<QuickAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            Text("Быстрые действия", style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                contentPadding = PaddingValues(top = Spacing.l, bottom = Spacing.xl),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(actions) { action ->
                    QuickActionTile(action = action, onClick = { onDismiss(); action.onClick() })
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: QuickAction, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .clickable(onClick = onClick)
            .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(
            modifier = Modifier
                .size(Dimens.quickActionTile / 2)
                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Text(action.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

object QuickActionIcons {
    val Breakdown: ImageVector = Icons.Rounded.Build
    val Trelevka: ImageVector = Icons.Rounded.LocalShipping
    val Stock: ImageVector = Icons.Rounded.Inventory2
    val Note: ImageVector = Icons.Rounded.EditNote
}
