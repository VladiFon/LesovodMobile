package com.lesovod.mobile.ui.kubaturnik

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestSuccess
import java.util.Locale

private fun Double.fmt(decimals: Int = 3): String = String.format(Locale.US, "%.${decimals}f", this)

@Composable
fun KubaturnikScreen(viewModel: KubaturnikViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenTitle("Кубатурник")

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(20.dp),
                )
                !state.lengthChosen -> LengthPicker(state = state, viewModel = viewModel)
                else -> KubaturnikMain(state = state, viewModel = viewModel)
            }
        }
    }

    if (state.showAddSortDialog) {
        AddSortDialog(onConfirm = viewModel::addSort, onDismiss = { viewModel.showAddSortDialog(false) })
    }
    if (state.showResetConfirm) {
        ResetConfirmDialog(onConfirm = viewModel::confirmReset, onDismiss = viewModel::dismissReset)
    }
}

@Composable
private fun LengthPicker(state: KubaturnikUiState, viewModel: KubaturnikViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Выберите длину бревна — один раз на всю партию",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        state.table?.blocks?.forEach { block ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Блок ${block.id} м", style = MaterialTheme.typography.titleSmall)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false,
                    ) {
                        items(block.lengths.size) { index ->
                            OutlinedButton(onClick = { viewModel.selectLength(block.id, index, block.lengths[index]) }) {
                                Text(block.lengths[index].fmt(2))
                            }
                        }
                    }
                }
            }
        }

        TextButton(onClick = viewModel::requestUnavailableLength) {
            Text("Нужна другая длина?")
        }

        state.outOfRangeLengthMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::dismissOutOfRangeMessage, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Понятно")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KubaturnikMain(state: KubaturnikUiState, viewModel: KubaturnikViewModel) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Счёт") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Сводка по ступеням") })
        }

        Box(modifier = Modifier.weight(1f)) {
            if (tab == 0) {
                KubaturnikCounting(state = state, viewModel = viewModel)
            } else {
                KubaturnikSummary(state = state)
            }
        }
    }
}

@Composable
private fun KubaturnikCounting(state: KubaturnikUiState, viewModel: KubaturnikViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        KubaturnikHeader(state = state, viewModel = viewModel)

        Row(modifier = Modifier.weight(1f)) {
            LeftPanel(state = state, modifier = Modifier.weight(0.42f))
            DiameterGrid(state = state, viewModel = viewModel, modifier = Modifier.weight(0.58f))
        }
    }
}

@Composable
private fun KubaturnikHeader(state: KubaturnikUiState, viewModel: KubaturnikViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Длина: ${state.selectedLengthValue.fmt(2)} м",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::requestReset) {
                Text("Новая партия")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Шаг:", style = MaterialTheme.typography.labelMedium)
            StepToggle(1, state.stepCm) { viewModel.setStep(1) }
            StepToggle(2, state.stepCm) { viewModel.setStep(2) }

            Box(Modifier.weight(1f))

            DestinationToggle(
                selected = state.destination,
                onSelect = viewModel::setDestination,
            )
        }

        SortRow(state = state, viewModel = viewModel)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "${state.totalVolume.fmt(3)} м³",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Итого по партии — машина + прицеп, все сорта (${state.totalLogCount} брёвен)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepToggle(value: Int, selected: Int, onClick: () -> Unit) {
    FilterChip(
        selected = value == selected,
        onClick = onClick,
        label = { Text("$value см") },
    )
}

@Composable
private fun DestinationToggle(selected: KubaturnikDestination, onSelect: (KubaturnikDestination) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KubaturnikDestination.entries.forEach { dest ->
            FilterChip(
                selected = dest == selected,
                onClick = { onSelect(dest) },
                label = { Text(dest.label) },
            )
        }
    }
}

@Composable
private fun SortRow(state: KubaturnikUiState, viewModel: KubaturnikViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Сорт:", style = MaterialTheme.typography.labelMedium)
        state.sorts.forEach { sort ->
            AssistChip(
                onClick = { viewModel.setActiveSort(sort) },
                label = { Text(sort) },
                colors = if (sort == state.activeSort) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        labelColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
        AssistChip(onClick = { viewModel.showAddSortDialog(true) }, label = { Text("+") })
    }
}

@Composable
private fun LeftPanel(state: KubaturnikUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 4.dp),
    ) {
        state.lastTap?.let { (diameter, volume) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ForestSuccess.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("⌀ $diameter см", style = MaterialTheme.typography.labelMedium, color = ForestSuccess)
                    Text("${volume.fmt(4)} м³ — одно бревно", style = MaterialTheme.typography.bodyMedium, color = ForestSuccess)
                }
            }
        }

        Text(
            "${state.activeSort} · ${state.destination.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        val rows = state.currentComboRows
        if (rows.isEmpty()) {
            Text(
                "Пока пусто",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows, key = { it.diameter }) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("⌀${row.diameter}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.3f))
                        Text("×${row.count}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.3f))
                        Text(row.totalVolume.fmt(3), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Комбо: ${rows.sumOf { it.count }} шт · ${rows.sumOf { it.totalVolume }.fmt(3)} м³",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiameterGrid(state: KubaturnikUiState, viewModel: KubaturnikViewModel, modifier: Modifier = Modifier) {
    val buttons = state.diameterButtonValues
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(buttons) { diameter ->
            DiameterButton(
                diameter = diameter,
                count = state.currentCombo[diameter] ?: 0,
                onTap = { viewModel.tapDiameter(diameter) },
                onUndo = { viewModel.undoDiameter(diameter) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiameterButton(diameter: Int, count: Int, onTap: () -> Unit, onUndo: () -> Unit) {
    val hasCount = count > 0
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hasCount) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (hasCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .aspectRatio(1.3f)
            .combinedClickable(onClick = onTap, onLongClick = onUndo),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$diameter", style = MaterialTheme.typography.titleLarge)
            if (hasCount) {
                Text("×$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun KubaturnikSummary(state: KubaturnikUiState) {
    val rows = state.thicknessSummary()
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rows) { row ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Ступень ${row.rangeLabel} см", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${row.sort} · ${row.destination.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${row.count} брёвен · ${row.volume.fmt(3)} м³",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSortDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый сорт") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Название сорта") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая партия?") },
        text = { Text("Все текущие подсчёты по этой партии будут удалены с телефона. Отменить нельзя.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Очистить", color = ForestAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
