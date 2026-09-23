package com.lesovod.mobile.ui.kubaturnik

import android.view.SoundEffectConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.local.KubaturnikCalculation
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestSuccess
import java.text.SimpleDateFormat
import java.util.Date
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
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("История") })
        }

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> KubaturnikCounting(state = state, viewModel = viewModel)
                1 -> KubaturnikSummary(state = state)
                else -> KubaturnikHistory(state = state, viewModel = viewModel)
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
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hasCount) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (hasCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .aspectRatio(1.3f)
            .combinedClickable(
                onClick = {
                    // Звук подтверждает счёт брёвен, не глядя на экран — как штатный клик кнопки.
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    onTap()
                },
                onLongClick = onUndo,
            ),
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
    val summary = state.fullSummary()
    if (summary.tables.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(summary.tables, key = { "${it.sort}|${it.destination}" }) { table ->
            SortDestinationSummaryCard(table)
        }
        item { TotalsBySortCard(summary.bySort) }
        item { TotalsByDestinationCard(summary.byDestination) }
        item { GrandTotalCard(count = summary.grandCount, volume = summary.grandVolume) }
    }
}

@Composable
private fun SortDestinationSummaryCard(table: SortDestinationTable) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${table.sort} · ${table.destination.label}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            SummaryTableHeader()
            table.groups.forEach { group ->
                group.diameters.forEach { row ->
                    SummaryTableRow(label = "⌀${row.diameter}", count = row.count, volume = row.volume)
                }
                SummaryTableRow(
                    label = group.rangeLabel,
                    count = group.count,
                    volume = group.volume,
                    emphasize = true,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            SummaryTableRow(label = "Итого", count = table.totalCount, volume = table.totalVolume, emphasize = true)
        }
    }
}

@Composable
private fun SummaryTableHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Диаметр", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
        Text("Кол-во", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
        Text("Объём, м³", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
    }
}

@Composable
private fun SummaryTableRow(label: String, count: Int, volume: Double, emphasize: Boolean = false) {
    val color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val weight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = weight, modifier = Modifier.weight(0.3f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = weight, modifier = Modifier.weight(0.3f))
        Text(volume.fmt(3), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = weight, modifier = Modifier.weight(0.4f))
    }
}

@Composable
private fun TotalsBySortCard(rows: List<TotalsRow<String>>) {
    TotalsCard(title = "Итого по сортам") {
        rows.forEach { row -> TotalsCardRow(label = row.key, count = row.count, volume = row.volume) }
    }
}

@Composable
private fun TotalsByDestinationCard(rows: List<TotalsRow<KubaturnikDestination>>) {
    TotalsCard(title = "Итого по машине/прицепу") {
        rows.forEach { row -> TotalsCardRow(label = row.key.label, count = row.count, volume = row.volume) }
    }
}

@Composable
private fun TotalsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun TotalsCardRow(label: String, count: Int, volume: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$count шт", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
        Text("${volume.fmt(3)} м³", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f))
    }
}

@Composable
private fun GrandTotalCard(count: Int, volume: Double) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Общий итог", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "${volume.fmt(3)} м³",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$count брёвен — все сорта, машина + прицеп",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KubaturnikHistory(state: KubaturnikUiState, viewModel: KubaturnikViewModel) {
    var selected by remember { mutableStateOf<KubaturnikCalculation?>(null) }
    var pendingDelete by remember { mutableStateOf<KubaturnikCalculation?>(null) }

    val current = selected
    when {
        current != null -> KubaturnikCalculationDetail(
            calculation = current,
            detailState = viewModel.uiStateFor(current),
            onBack = { selected = null },
            onDelete = { pendingDelete = current },
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
                }) { Text("Удалить", color = ForestAccent) }
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
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            KubaturnikSummary(state = detailState)
        }
    }
}

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
            TextButton(onClick = onConfirm) { Text("Сохранить и начать новую", color = ForestAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
