package com.lesovod.mobile.ui.lesokultury

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.components.UchastokSelector
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

@Composable
fun PerevodScreen(onBack: () -> Unit, viewModel: PerevodViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var confirmPerevod by remember { mutableStateOf(false) }

    if (confirmPerevod) {
        val label = state.selectedUchastok?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmPerevod = false },
            title = { Text("Перевести участок?") },
            text = { Text("Решение «Перевести» по участку «$label» сразу меняет его статус на сервере.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPerevod = false
                    viewModel.submit()
                }) { Text("Перевести") }
            },
            dismissButton = { TextButton(onClick = { confirmPerevod = false }) { Text("Отмена") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Перевод лесных культур", style = MaterialTheme.typography.titleSmall)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            val result = state.result
            if (result != null) {
                ServerResultCard(result = result, title = "Решение отправлено", onNewCard = viewModel::newCard)
                return@Column
            }
            if (state.queuedOffline) {
                QueuedOfflineCard(onNewCard = viewModel::newCard)
                return@Column
            }

            if (state.isLoadingReference) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                return@Column
            }

            // Шаг 1 — выбрать участок (поиск + фильтр по году создания); шаг 2 — сама карточка.
            UchastokSelector(
                uchastki = state.uchastki,
                selected = state.selectedUchastok,
                onSelect = viewModel::selectUchastok,
                enabled = !state.isSubmitting,
            )
            if (state.selectedUchastok == null) {
                if (state.error != null) {
                    StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
                }
                return@Column
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
            ) {
                Text("Год обследования", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                GodPicker(god = state.god, onSelect = viewModel::selectGod)

                ProbyList(
                    proby = state.proby,
                    enabled = !state.isSubmitting,
                    onNomerChange = viewModel::onProbaNomerChange,
                    onRazmerChange = viewModel::onProbaRazmerChange,
                    onRemove = viewModel::removeProba,
                    onAdd = viewModel::addProba,
                )
            }

            RezultatySection(
                porody = state.porody,
                rezultaty = state.rezultaty,
                proby = state.proby,
                enabled = !state.isSubmitting,
                onTap = viewModel::tapPoroda,
                onUndo = viewModel::undoPoroda,
                onVysazhenoChange = viewModel::onVysazhenoChange,
                onRemove = viewModel::removeRezultat,
                onAdd = viewModel::addPoroda,
            )

            PreviewCard(preview = state.preview)

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
            ) {
                Text("Решение", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerevodReshenie.entries.forEach { option ->
                        FilterChip(
                            selected = state.reshenie == option,
                            onClick = { viewModel.selectReshenie(option) },
                            label = { Text(option.displayName) },
                        )
                    }
                }
                if (state.reshenie == PerevodReshenie.PEREVESTI) {
                    Text(
                        "«Перевести» сразу меняет статус участка на сервере, без дополнительного подтверждения офисом",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (state.error != null) {
                StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
            }

            PrimaryButton(
                text = "Отправить",
                onClick = {
                    if (state.reshenie == PerevodReshenie.PEREVESTI) confirmPerevod = true else viewModel.submit()
                },
                enabled = !state.isSubmitting,
                modifier = Modifier.padding(bottom = Spacing.xl),
                icon = if (state.isSubmitting) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else null,
            )
        }
    }
}
