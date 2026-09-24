package com.lesovod.mobile.ui.stock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestAccentText
import com.lesovod.mobile.ui.theme.ForestBarTrack
import com.lesovod.mobile.ui.theme.ForestError
import com.lesovod.mobile.ui.theme.ForestErrorBorder
import com.lesovod.mobile.ui.theme.ForestErrorContainer
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestRemainderPanel
import com.lesovod.mobile.ui.theme.ForestRingTrack
import com.lesovod.mobile.ui.theme.ForestSuccess
import com.lesovod.mobile.ui.theme.ForestTextMuted
import com.lesovod.mobile.ui.theme.ForestTolerance
import com.lesovod.mobile.ui.theme.LesovodTheme
import com.lesovod.mobile.ui.theme.Manrope

/** Кольцо доли выбранного лимита — трек + дуга, с процентом в центре. */
@Composable
fun RemainderRing(percent: Int, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    Box(
        modifier = modifier
            .size(96.dp)
            .semantics { contentDescription = "Выбрано $clamped% лимита" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 10.dp.toPx()
            val diameterPx = 38.dp.toPx() * 2
            val topLeft = Offset((size.width - diameterPx) / 2f, (size.height - diameterPx) / 2f)
            val arcSize = Size(diameterPx, diameterPx)
            drawArc(
                color = ForestRingTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
            if (clamped > 0) {
                drawArc(
                    color = ForestSuccess,
                    startAngle = -90f,
                    sweepAngle = clamped / 100f * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$clamped%", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp), color = ForestPrimary)
            Text(
                "выбрано",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                color = ForestTextMuted,
            )
        }
    }
}

/** Главная панель категории: крупное число остатка слева (+ строка допуска ±10% лимита), кольцо доли — справа. */
@Composable
fun RemainderPanel(
    remainderText: String,
    percent: Int,
    minus10Text: String,
    plus10Text: String,
    modifier: Modifier = Modifier,
    remainderColor: Color = ForestPrimary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(ForestRemainderPanel, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Column {
            Text(
                "Остаток",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = ForestAccentText,
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    remainderText,
                    style = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 54.sp,
                        letterSpacing = (-0.02).em,
                        fontFeatureSettings = "tnum",
                    ),
                    color = remainderColor,
                    modifier = Modifier.alignBy(FirstBaseline),
                )
                Text(
                    "м³",
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                    color = ForestTextMuted,
                    modifier = Modifier
                        .alignBy(FirstBaseline)
                        .padding(start = 6.dp),
                )
            }
            Text(
                "−10%: $minus10Text  ·  +10%: $plus10Text м³",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFeatureSettings = "tnum"),
                color = ForestTolerance,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        RemainderRing(percent = percent)
    }
}

/** Прогресс-бар одного источника факта (Наряд / ЕГАИС) — может состоять из нескольких цветных сегментов. */
@Composable
fun SourceBar(
    label: String,
    valueText: String,
    valueColor: Color,
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 17.sp),
                color = ForestPrimary,
            )
            Text(
                valueText,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFeatureSettings = "tnum"),
                color = valueColor,
            )
        }
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ForestBarTrack),
        ) {
            var used = 0f
            segments.forEach { (fraction, color) ->
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(color),
                    )
                    used += fraction
                }
            }
            val rest = (1f - used).coerceAtLeast(0.0001f)
            Spacer(modifier = Modifier.weight(rest))
        }
    }
}

/** Плашка расхождения: ЕГАИС больше наряда — требует внимания мастера. */
@Composable
fun DiscrepancyBanner(differenceText: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ForestErrorContainer, RoundedCornerShape(14.dp))
            .border(1.dp, ForestErrorBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Внимание: ЕГАИС показывает больше, чем наряд. Разница $differenceText"
            },
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = ForestError, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                "ЕГАИС показывает больше, чем наряд",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = ForestError,
            )
            Text(
                "Разница: $differenceText",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                color = ForestError,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun RemainderRingPreview() {
    LesovodTheme {
        RemainderRing(percent = 62, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun RemainderPanelPreview() {
    LesovodTheme {
        RemainderPanel(remainderText = "45,5", percent = 62, minus10Text = "33,5", plus10Text = "57,5", modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun SourceBarNaryadPreview() {
    LesovodTheme {
        SourceBar(
            label = "Наряд",
            valueText = "74,5 м³",
            valueColor = ForestPrimary,
            segments = listOf(0.62f to ForestPrimary),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun SourceBarEgaisDiscrepancyPreview() {
    LesovodTheme {
        SourceBar(
            label = "ЕГАИС",
            valueText = "25,4 м³",
            valueColor = ForestError,
            segments = listOf(0.55f to ForestAccent, 0.085f to ForestError),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun DiscrepancyBannerPreview() {
    LesovodTheme {
        DiscrepancyBanner(differenceText = "+3,4 м³", modifier = Modifier.padding(16.dp))
    }
}
