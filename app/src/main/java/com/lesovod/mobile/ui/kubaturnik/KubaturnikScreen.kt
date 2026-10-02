package com.lesovod.mobile.ui.kubaturnik

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.local.KubaturnikCalculation
import com.lesovod.mobile.data.local.TilesSide
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.navigation.WorkReportPrefill
import com.lesovod.mobile.ui.navigation.WorkReportPrefillRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun Double.fmt(decimals: Int = 3): String = String.format(Locale.US, "%.${decimals}f", this)

@Composable
fun KubaturnikScreen(
    onOpenWorkReport: () -> Unit = {},
    viewModel: KubaturnikViewModel = viewModel(),
) {
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
                else -> KubaturnikMain(
                    state = state,
                    viewModel = viewModel,
                    onAddToReport = { s ->
                        WorkReportPrefillRequest.set(s.toReportPrefill())
                        onOpenWorkReport()
                    },
                )
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
private fun KubaturnikMain(
    state: KubaturnikUiState,
    viewModel: KubaturnikViewModel,
    onAddToReport: (KubaturnikUiState) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Счёт") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Сводка по ступеням") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("История") })
        }

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> KubaturnikCounting(state = state, viewModel = viewModel, onAddToReport = onAddToReport)
                1 -> KubaturnikSummary(state = state)
                else -> KubaturnikHistory(state = state, viewModel = viewModel, onAddToReport = onAddToReport)
            }
        }
    }
}

@Composable
private fun KubaturnikCounting(
    state: KubaturnikUiState,
    viewModel: KubaturnikViewModel,
    onAddToReport: (KubaturnikUiState) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        KubaturnikHeader(state = state, viewModel = viewModel, onAddToReport = onAddToReport)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ScoreBody(
            tilesSide = state.tilesSide,
            diameters = state.diameterButtonValues,
            counts = state.currentCombo,
            lastTapped = state.lastTap?.first,
            onTap = viewModel::tapDiameter,
            onUndo = viewModel::undoDiameter,
            panel = {
                ReferencePanel(
                    lastTap = state.lastTap,
                    rows = state.currentComboRows,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KubaturnikHeader(
    state: KubaturnikUiState,
    viewModel: KubaturnikViewModel,
    onAddToReport: (KubaturnikUiState) -> Unit,
) {
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
            IconButton(onClick = viewModel::toggleTilesSide) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = if (state.tilesSide == TilesSide.LEFT) {
                        "Плитки слева — переключить направо"
                    } else {
                        "Плитки справа — переключить налево"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                if (state.totalLogCount > 0) {
                    AddToReportButton(onClick = { onAddToReport(state) })
                }
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
private fun KubaturnikSummary(state: KubaturnikUiState) {
    val tables = state.thicknessSummary()
    if (tables.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tables) { table ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        table.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    ThicknessLine("Диаметр", "кол-во", "Объём, м³", header = true)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    table.rows.forEach { row ->
                        when (row.kind) {
                            ThicknessRowKind.DIAMETER ->
                                ThicknessLine(row.label, row.count.toString(), row.volume.fmt(3))
                            ThicknessRowKind.GROUP ->
                                ThicknessLine(row.label, row.count.toString(), row.volume.fmt(3), group = true)
                            ThicknessRowKind.TOTAL -> {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                ThicknessLine(row.label, row.count.toString(), row.volume.fmt(3), header = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка таблицы сводки. Диаметры — числом справа, как в ведомости; итоговые строки групп
 * («14–24», «26 и б.») — подписью слева на подсвеченном фоне, чтобы их не спутать со ступенью.
 */
@Composable
private fun ThicknessLine(label: String, count: String, volume: String, header: Boolean = false, group: Boolean = false) {
    val style = if (header || group) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (group) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = style,
            color = if (group) MaterialTheme.colorScheme.primary else Color.Unspecified,
            textAlign = if (header || group) TextAlign.Start else TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(count, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(volume, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun KubaturnikHistory(
    state: KubaturnikUiState,
    viewModel: KubaturnikViewModel,
    onAddToReport: (KubaturnikUiState) -> Unit,
) {
    var selected by remember { mutableStateOf<KubaturnikCalculation?>(null) }
    var pendingDelete by remember { mutableStateOf<KubaturnikCalculation?>(null) }

    val current = selected
    when {
        current != null -> KubaturnikCalculationDetail(
            calculation = current,
            detailState = viewModel.uiStateFor(current),
            onBack = { selected = null },
            onDelete = { pendingDelete = current },
            onAddToReport = onAddToReport,
        )
        state.calculations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Пока нет сохранённых расчётов", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.calculations, key = { it.id }) { calculation ->
                val calcState = viewModel.uiStateFor(calculation)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = calculation },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(calculation.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    formatCalculationDate(calculation.savedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { pendingDelete = calculation }) { Text("Удалить") }
                        }
                        Text(
                            "${calcState.totalVolume.fmt(3)} м³ · ${calcState.totalLogCount} брёвен",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "Длина ${calcState.selectedLengthValue.fmt(2)} м",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { calculation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить расчёт?") },
            text = { Text("«${calculation.label}» будет удалён без возможности восстановить.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCalculation(calculation.id)
                    if (selected?.id == calculation.id) selected = null
                    pendingDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun KubaturnikCalculationDetail(
    calculation: KubaturnikCalculation,
    detailState: KubaturnikUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onAddToReport: (KubaturnikUiState) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Box(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("Удалить") }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(calculation.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    formatCalculationDate(calculation.savedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${detailState.totalVolume.fmt(3)} м³ · ${detailState.totalLogCount} брёвен · длина ${detailState.selectedLengthValue.fmt(2)} м",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (detailState.totalLogCount > 0) {
                    AddToReportButton(onClick = { onAddToReport(detailState) })
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            KubaturnikSummary(state = detailState)
        }
    }
}

/** «Добавить в отчёт»: открывает «Отчёт о работе» с объёмом партии в поле «Объём, м³». */
@Composable
private fun AddToReportButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text("Добавить в отчёт")
    }
}

/** Объём партии и её описание (длина, брёвна, сорта) для отчёта. */
private fun KubaturnikUiState.toReportPrefill() = WorkReportPrefill(
    obyom = totalVolume,
    opisanie = "Кубатурник: ${totalVolume.fmt(3)} м³, $totalLogCount брёвен, длина ${selectedLengthValue.fmt(2)} м" +
        (usedSorts().takeIf { it.isNotEmpty() }?.let { ", сорта: ${it.joinToString(", ")}" } ?: ""),
)

/** Сорта, по которым в партии реально есть брёвна. */
private fun KubaturnikUiState.usedSorts(): List<String> =
    counts.filter { (_, byDest) -> byDest.values.any { byDiam -> byDiam.values.sum() > 0 } }.keys.toList()

private fun formatCalculationDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(millis))

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
        text = { Text("Текущий расчёт сохранится в «Истории», а счётчики на экране очистятся для новой партии.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Сохранить и начать новую", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
