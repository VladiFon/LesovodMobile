package com.lesovod.mobile.ui.stock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.ui.theme.ForestEmptyIconBg
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestRemainderPanel
import com.lesovod.mobile.ui.theme.ForestRingTrack
import com.lesovod.mobile.ui.theme.ForestSkeleton
import com.lesovod.mobile.ui.theme.ForestSurface
import com.lesovod.mobile.ui.theme.ForestTextMuted
import com.lesovod.mobile.ui.theme.LesovodTheme
import com.lesovod.mobile.ui.theme.Manrope

/** Делянка не найдена в системе расхода — это не ошибка, спокойное пустое состояние. */
@Composable
fun NotFoundCard(kvartal: String, vydel: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ForestSurface,
        border = BorderStroke(1.dp, ForestOutline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(ForestEmptyIconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SearchOff, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(28.dp))
            }
            Text(
                "Делянка не найдена в системе расхода",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
                color = ForestPrimary,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "По кв. $kvartal, выд. $vydel данных о расходе нет. Проверьте номер выдела или выберите делянку из списка выше.",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 17.sp),
                color = ForestTextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Скелетон карточки породы на время проверки остатка — контур карточки, мягкая пульсация. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ForestSurface,
        border = BorderStroke(1.dp, ForestOutline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .graphicsLayer { this.alpha = alpha },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBar(width = 120.dp, height = 22.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SkeletonBar(width = 110.dp, height = 14.dp)
                SkeletonBar(width = 70.dp, height = 14.dp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(ForestRemainderPanel, RoundedCornerShape(18.dp)),
            ) {
                SkeletonBar(
                    width = 90.dp,
                    height = 16.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(72.dp)
                        .border(BorderStroke(10.dp, ForestRingTrack), CircleShape),
                )
            }
            SkeletonBar(width = null, height = 12.dp)
            SkeletonBar(width = null, height = 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBar(width = 140.dp, height = 12.dp)
                SkeletonBar(width = 90.dp, height = 12.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(ForestSkeleton),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun NotFoundCardPreview() {
    LesovodTheme {
        NotFoundCard(kvartal = "47", vydel = "99", modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun SkeletonCardPreview() {
    LesovodTheme {
        SkeletonCard(modifier = Modifier.padding(16.dp))
    }
}
