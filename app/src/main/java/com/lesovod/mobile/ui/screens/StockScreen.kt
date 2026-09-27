package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
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
                    remaining.grouped?.forEach { (poroda, group) ->
                        PorodaCard(poroda, group)
                    }

                    Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
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

@Composable
private fun PorodaCard(poroda: String, group: PorodaRemainingDto) {
    val totalLimit = (group.delovaya?.limit ?: 0.0) + (group.drova?.limit ?: 0.0)
    val totalOstatok = (group.delovaya?.ostatokSafe ?: 0.0) + (group.drova?.ostatokSafe ?: 0.0)

    Column(modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text(poroda, style = MaterialTheme.typography.titleSmall)
        group.delovaya?.let { VolumeRow("Деловая древесина", it) }
        group.drova?.let { VolumeRow("Дрова", it) }

        Text(
            "Итого лимит: ${totalLimit.fmt()} м³ · Итого остаток: ${totalOstatok.fmt()} м³",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun VolumeRow(label: String, volume: VolumeBreakdownDto) {
    val egaisExceedsNaryad = (volume.faktEgais ?: 0.0) > (volume.faktNaryad ?: 0.0)

    Column(modifier = Modifier.padding(top = Spacing.s)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Лимит: ${volume.limit.fmt()} м³ · Наряд: ${volume.faktNaryad.fmt()} м³ · " +
                "ЕГАИС: ${volume.faktEgais.fmt()} м³ · Остаток: ${volume.ostatokSafe.fmt()} м³",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (egaisExceedsNaryad) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(
                    "ЕГАИС показывает больше, чем наряд",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}
