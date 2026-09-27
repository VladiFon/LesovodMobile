package com.lesovod.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.ui.theme.RadiusChip
import com.lesovod.mobile.ui.theme.semanticColors

/** Чип состояния (В работе / Ждёт / Готово / Почти всё / Больше плана и т.п.), редизайн «Поляна». */
enum class ChipTone { NEUTRAL, PRIMARY, OK, WARN, ERROR }

@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val semantic = MaterialTheme.semanticColors
    val (container, content) = when (tone) {
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ChipTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ChipTone.OK -> semantic.ok.copy(alpha = 0.16f) to semantic.ok
        ChipTone.WARN -> semantic.warnContainer to semantic.onWarnContainer
        ChipTone.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f) to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(RadiusChip))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
