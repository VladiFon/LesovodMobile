package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.roundToInt
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.PorodaRemainingDto
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import com.lesovod.mobile.ui.bot.StockViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Экран 7 редизайна «Поляна» (docs/SCREENS.md) — «Остатки по делянке». */
@Composable
fun StockScreen(viewModel: StockViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Остатки по делянке")

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = state.kvartal,
                    onValueChange = viewModel::onKvartalChange,
                    label = { Text("Квартал") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Делянки",
                    onClick = viewModel::loadDelyanki,
                    enabled = !state.isLoadingDelyanki && state.kvartal.isNotBlank(),
                    modifier = Modifier.weight(0.6f),
                )
            }

            if (state.isLoadingDelyanki) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            if (state.delyanki.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    state.delyanki.forEach { delyanka: DelyankaDto ->
                        val label = if (delyanka.lesosekaNomer.isNullOrBlank()) {
                            "выд. ${delyanka.vydel}"
                        } else {
                            "выд. ${delyanka.vydel} / лес. ${delyanka.lesosekaNomer}"
                        }
                        AssistChip(onClick = { viewModel.selectDelyanka(delyanka) }, label = { Text(label) })
                    }
                }
            }

            OutlinedTextField(
                value = state.vydel,
                onValueChange = viewModel::onVydelChange,
                label = { Text("Выдел") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.lesoseka,
                onValueChange = viewModel::onLesosekaChange,
                label = { Text("Лесосека (необязательно)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )

            PrimaryButton(
                text = "Проверить остаток",
                onClick = viewModel::loadRemaining,
                enabled = !state.isLoadingRemaining,
                icon = if (state.isLoadingRemaining) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else null,
            )

            if (state.error != null) {
                StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
            }

            val remaining = state.remaining
            if (remaining != null) {
                if (!remaining.found) {
                    Text(
                        "Делянка не найдена в системе расхода",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val delyankaLabel = buildString {
                        if (state.vydel.isNotBlank()) append("выд. ${state.vydel}")
                        if (state.lesoseka.isNotBlank()) {
                            if (isNotEmpty()) append(" / ")
                            append("лес. ${state.lesoseka}")
                        }
                    }

                    remaining.grouped?.forEach { (poroda, group) ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PorodaCard(poroda, group, delyankaLabel)
                            SpeciesTotalFooter(group)
                        }
                    }

                    Column(modifier = Modifier.padding(bottom = Spacing.xl, top = Spacing.s)) {
                        remaining.lastUpdate?.let {
                            Text(
                                "Наряды обновлены: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        remaining.egaisImportedAt?.let {
                            Text(
                                "ЕГАИС загружен: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Double?.fmt(): String = if (this == null) "—" else String.format(Locale.US, "%.2f", this)

/** Карточка породы: заголовок + секции «Деловая древесина» / «Дрова» (мокап variant-A). */
@Composable
private fun PorodaCard(poroda: String, group: PorodaRemainingDto, delyankaLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .softCard(MaterialTheme.shapes.medium)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(poroda, style = MaterialTheme.typography.titleMedium)
            if (delyankaLabel.isNotBlank()) {
                Text(
                    delyankaLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        group.delovaya?.let { WoodSection("Деловая древесина", it) }
        if (group.delovaya != null && group.drova != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        group.drova?.let { WoodSection("Дрова", it) }
    }
}

/** Секция объёма (Деловая древесина / Дрова): остаток + кольцо, полосы Наряд/ЕГАИС, баннер расхождения. */
@Composable
private fun WoodSection(label: String, volume: VolumeBreakdownDto) {
    val limit = volume.limit ?: 0.0
    val ostatok = volume.ostatokSafe ?: 0.0
    val naryad = volume.faktNaryad ?: 0.0
    val egais = volume.faktEgais ?: 0.0
    val egaisExceedsNaryad = egais > naryad
    val remainderPercent = if (limit > 0) ((ostatok / limit) * 100).roundToInt().coerceIn(0, 100) else null

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            RemainderPanel(ostatok = ostatok, modifier = Modifier.weight(1f))
            if (remainderPercent != null) {
                RemainderRing(percent = remainderPercent)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ProgressBarRow("Наряд", current = naryad, limit = limit, fillColor = MaterialTheme.colorScheme.primary)
            ProgressBarRow("ЕГАИС", current = egais, limit = limit, fillColor = MaterialTheme.colorScheme.tertiary)
        }

        if (egaisExceedsNaryad) {
            DiscrepancyBanner(diff = egais - naryad)
        }
    }
}

/** RemainderPanel: остаток + строка допуска ±10%. */
@Composable
private fun RemainderPanel(ostatok: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Text(
            "Остаток",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                ostatok.fmt(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "м³",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        Text(
            "−10%: ${(ostatok * 0.9).fmt()}  ·  +10%: ${(ostatok * 1.1).fmt()} м³",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Кольцевая диаграмма доли остатка от лимита (ostatokSafe / limit). Чисто демонстративный акцент из мокапа. */
@Composable
private fun RemainderRing(percent: Int, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 7.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

/** Строка прогресс-бара «Наряд» / «ЕГАИС»: текущее/цель м³ + цветная полоса. */
@Composable
private fun ProgressBarRow(label: String, current: Double, limit: Double, fillColor: Color) {
    val fraction = if (limit > 0) (current / limit).toFloat().coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${current.fmt()} / ${limit.fmt()} м³",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(fillColor, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** Баннер расхождения ЕГАИС/Наряда — та же логика (egaisExceedsNaryad), стиль под мокап variant-A. */
@Composable
private fun DiscrepancyBanner(diff: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "ЕГАИС превышает наряд на ${diff.fmt()} м³",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** SpeciesTotalFooter: тёмная (primary) карточка с итогом по породе + допуск ±10%. */
@Composable
private fun SpeciesTotalFooter(group: PorodaRemainingDto) {
    val delovayaOstatok = group.delovaya?.ostatokSafe ?: 0.0
    val drovaOstatok = group.drova?.ostatokSafe ?: 0.0
    val total = delovayaOstatok + drovaOstatok
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Text("Итого остаток", style = MaterialTheme.typography.labelSmall, color = onPrimary.copy(alpha = 0.75f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(total.fmt(), style = MaterialTheme.typography.titleLarge, color = onPrimary)
            Text("м³", style = MaterialTheme.typography.labelLarge, color = onPrimary.copy(alpha = 0.85f))
        }

        val breakdown = buildList {
            if (group.delovaya != null) add("Деловая ${delovayaOstatok.fmt()}")
            if (group.drova != null) add("Дрова ${drovaOstatok.fmt()}")
        }
        if (breakdown.isNotEmpty()) {
            Text(
                breakdown.joinToString(" + "),
                style = MaterialTheme.typography.labelSmall,
                color = onPrimary.copy(alpha = 0.75f),
            )
        }

        Text(
            "−10%: ${(total * 0.9).fmt()}  ·  +10%: ${(total * 1.1).fmt()} м³",
            style = MaterialTheme.typography.labelSmall,
            color = onPrimary.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
