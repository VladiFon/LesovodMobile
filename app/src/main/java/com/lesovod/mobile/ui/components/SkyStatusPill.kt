package com.lesovod.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.ui.theme.semanticColors

/**
 * Индикатор состояния связи «Небо» (редизайн «Поляна») — пилюля с иконкой и словом
 * вместо цветной точки-статуса, чтобы состояние никогда не читалось только цветом.
 *
 * TODO: пока источник — только количество записей в [OfflineQueueManager.pending];
 * [Haze] (слабая связь: сеть формально есть, но запросы падают) не определяется —
 * нужен отдельный ConnectivityObserver с историей неудачных запросов, см. открытый
 * вопрос в docs/MIGRATION_PLAN.md пакета редизайна.
 */
sealed interface SkyState {
    data object Sun : SkyState
    data object Haze : SkyState
    data class Cloud(val queued: Int) : SkyState
    data object Syncing : SkyState
}

@Composable
fun SkyStatusPill(
    state: SkyState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = MaterialTheme.semanticColors
    val (color, label) = when (state) {
        is SkyState.Sun -> semantic.ok to "Ясно"
        is SkyState.Haze -> MaterialTheme.colorScheme.tertiary to "Дымка"
        is SkyState.Cloud -> semantic.sky to "Не отправлено: ${state.queued}"
        is SkyState.Syncing -> semantic.ok to "Синхронизация…"
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, color, CircleShape)
            .clickable(onClickLabel = "Отправить сейчас", onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SkyIcon(state = state, tint = color)
        Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SkyIcon(state: SkyState, tint: Color) {
    when (state) {
        is SkyState.Cloud -> Icon(Icons.Rounded.Cloud, contentDescription = null, tint = tint, modifier = Modifier.iconSize())
        is SkyState.Syncing -> {
            val transition = rememberInfiniteTransition(label = "sky-sync")
            val angle by transition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                label = "sky-sync-angle",
            )
            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = tint, modifier = Modifier.iconSize().rotate(angle))
        }
        else -> Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = tint, modifier = Modifier.iconSize())
    }
}

private fun Modifier.iconSize() = this.height(16.dp)
