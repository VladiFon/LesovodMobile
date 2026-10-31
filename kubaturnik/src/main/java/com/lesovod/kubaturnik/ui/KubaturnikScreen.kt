package com.lesovod.kubaturnik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.lesovod.kubaturnik.data.KubaturnikCalculation
import com.lesovod.kubaturnik.data.TilesSide
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun Double.fmt(decimals: Int = 3): String = String.format(Locale.US, "%.${decimals}f", this)

@Composable
fun KubaturnikScreen(
    viewModel: KubaturnikViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val voice by viewModel.voiceState.collectAsState()
    val darkTheme by viewModel.darkTheme.collectAsState()
    val tapSoundOn by viewModel.tapSoundOn.collectAsState()
    val context = LocalContext.current
    VoiceLifecycleGuard(viewModel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppTitle(
            darkTheme = darkTheme,
            onToggleTheme = viewModel::toggleTheme,
            tapSoundOn = tapSoundOn,
            onToggleTapSound = viewModel::toggleTapSound,
        )
        AppBanners(updates = viewModel.updates)

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
                    voice = voice,
                    viewModel = viewModel,
                    onShare = { s -> shareCalculation(context, s, title = "Текущая партия") },
                )
            }
        }
    }

    if (state.showResetConfirm) {
        ResetConfirmDialog(onConfirm = viewModel::confirmReset, onDismiss = viewModel::dismissReset)
    }
    VoiceModelDialog(voice = voice, viewModel = viewModel)
}

/** Заголовок приложения: «Лесовод · Кубатурник» и переключатель тёмной/светлой темы. */
@Composable
private fun AppTitle(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    tapSoundOn: Boolean,
    onToggleTapSound: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Лесовод",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Кубатурник",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onToggleTapSound) {
            Icon(
                imageVector = if (tapSoundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = if (tapSoundOn) "Звук нажатия включён — выключить" else "Звук нажатия выключен — включить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (darkTheme) "Светлая тема" else "Тёмная тема",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    voice: VoiceUiState,
    viewModel: KubaturnikViewModel,
    onShare: (KubaturnikUiState) -> Unit,
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
                0 -> KubaturnikCounting(state = state, voice = voice, viewModel = viewModel, onShare = onShare)
                1 -> KubaturnikSummary(state = state)
                else -> KubaturnikHistory(state = state, viewModel = viewModel, onShare = onShare)
            }
        }
    }
}

@Composable
private fun KubaturnikCounting(
    state: KubaturnikUiState,
    voice: VoiceUiState,
    viewModel: KubaturnikViewModel,
    onShare: (KubaturnikUiState) -> Unit,
) {
    // Весь «Счёт» прокручивается: шапку (длина, шаг, сорта, итог, голос) можно увести вверх,
    // и тогда сетка плиток занимает весь видимый экран. Высота сетки фиксирована по экрану —
    // внутри прокрутки у неё нет «оставшегося места», а плиткам нужна конечная высота.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridHeight = maxHeight.coerceAtLeast(MinScoreHeight)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            KubaturnikHeader(state = state, voice = voice, viewModel = viewModel, onShare = onShare)
            VoiceBar(state = state, voice = voice, viewModel = viewModel)
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
                modifier = Modifier.height(gridHeight),
            )
        }
    }
}

private val MinScoreHeight = 360.dp

@Composable
private fun KubaturnikHeader(
    state: KubaturnikUiState,
    voice: VoiceUiState,
    viewModel: KubaturnikViewModel,
    onShare: (KubaturnikUiState) -> Unit,
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
            VoiceMicButton(voice = voice, viewModel = viewModel)
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
                    ShareButton(onClick = { onShare(state) })
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
        // Сортов может быть больше, чем влезает в ширину, — листаются вбок.
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        Text("Сорт:", style = MaterialTheme.typography.labelMedium)
        state.sorts.forEach { sort ->
            AssistChip(
                onClick = { viewModel.setActiveSort(sort) },
                label = { Text(sort, style = MaterialTheme.typography.titleSmall) },
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
    onShare: (KubaturnikUiState) -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<KubaturnikCalculation?>(null) }
    var pendingDelete by remember { mutableStateOf<KubaturnikCalculation?>(null) }

    val current = selected
    when {
        current != null -> KubaturnikCalculationDetail(
            calculation = current,
            detailState = viewModel.uiStateFor(current),
            onBack = { selected = null },
            onDelete = { pendingDelete = current },
            onShare = { s -> shareCalculation(context, s, title = "${current.label}, ${formatCalculationDate(current.savedAt)}") },
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
    onShare: (KubaturnikUiState) -> Unit,
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
                    ShareButton(onClick = { onShare(detailState) })
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            KubaturnikSummary(state = detailState)
        }
    }
}

/**
 * «Поделиться»: сводка партии текстом (длина, итог, таблица по ступеням) — в Telegram, WhatsApp,
 * СМС или почту. Своего сервера у Кубатурника нет, так что это и есть способ отправить расчёт.
 */
@Composable
private fun ShareButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text("Поделиться")
    }
}

private fun shareCalculation(context: Context, state: KubaturnikUiState, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Кубатурник: ${state.totalVolume.fmt(3)} м³")
        putExtra(Intent.EXTRA_TEXT, state.toShareText(title))
    }
    context.startActivity(Intent.createChooser(intent, "Отправить расчёт"))
}

/** Текст для отправки: шапка и таблицы «Диаметр — кол-во — объём», как на вкладке сводки. */
internal fun KubaturnikUiState.toShareText(title: String): String = buildString {
    appendLine("Лесовод Кубатурник — $title")
    appendLine("Длина ${selectedLengthValue.fmt(2)} м, ГОСТ 2708-75")
    appendLine("Итого: ${totalVolume.fmt(3)} м³, $totalLogCount брёвен")
    usedSorts().takeIf { it.isNotEmpty() }?.let { sorts ->
        appendLine("Сорта: ${sorts.joinToString(", ")}")
    }
    thicknessSummary().forEach { table ->
        appendLine()
        appendLine(table.title)
        appendLine("Ø — шт — м³")
        table.rows.forEach { row ->
            appendLine("${row.label} — ${row.count} — ${row.volume.fmt(3)}")
        }
    }
}.trimEnd()

/** Сорта, по которым в партии реально есть брёвна. */
private fun KubaturnikUiState.usedSorts(): List<String> =
    sorts.filter { sort -> counts[sort]?.values?.any { byDiam -> byDiam.values.sum() > 0 } == true }.map(::sortLabel)

private fun formatCalculationDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(millis))

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
