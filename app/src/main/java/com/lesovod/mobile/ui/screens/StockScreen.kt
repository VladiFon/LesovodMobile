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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.PorodaRemainingDto
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import com.lesovod.mobile.ui.bot.StockViewModel
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.ForestError

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.kvartal,
                    onValueChange = viewModel::onKvartalChange,
                    label = { Text("Квартал") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = viewModel::loadDelyanki,
                    enabled = !state.isLoadingDelyanki && state.kvartal.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Делянки")
                }
            }

            if (state.isLoadingDelyanki) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            if (state.delyanki.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Button(
                onClick = viewModel::loadRemaining,
                enabled = !state.isLoadingRemaining,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoadingRemaining) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Проверить остаток")
                }
            }

            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val remaining = state.remaining
            if (remaining != null) {
                if (!remaining.found) {
                    Text(
                        "Делянка не найдена в системе расхода",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    remaining.grouped?.forEach { (poroda, group) ->
                        PorodaCard(poroda, group)
                    }

                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                        remaining.lastUpdate?.let {
                            Text(
                                "Наряды обновлены: $it",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        remaining.egaisImportedAt?.let {
                            Text(
                                "ЕГАИС загружен: $it",
                                style = MaterialTheme.typography.labelMedium,
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

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(poroda, style = MaterialTheme.typography.titleMedium)
            group.delovaya?.let { VolumeRow("Деловая древесина", it) }
            group.drova?.let { VolumeRow("Дрова", it) }

            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "Итого лимит: ${totalLimit.fmt()} м³ · Итого остаток: ${totalOstatok.fmt()} м³",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun VolumeRow(label: String, volume: VolumeBreakdownDto) {
    val egaisExceedsNaryad = (volume.faktEgais ?: 0.0) > (volume.faktNaryad ?: 0.0)

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Лимит: ${volume.limit.fmt()} м³ · Наряд: ${volume.faktNaryad.fmt()} м³ · " +
                "ЕГАИС: ${volume.faktEgais.fmt()} м³ · Остаток: ${volume.ostatokSafe.fmt()} м³",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (egaisExceedsNaryad) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = ForestError,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "ЕГАИС показывает больше, чем наряд",
                    style = MaterialTheme.typography.labelMedium,
                    color = ForestError,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
