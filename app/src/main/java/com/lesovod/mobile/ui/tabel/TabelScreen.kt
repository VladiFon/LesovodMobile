package com.lesovod.mobile.ui.tabel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.TabelLesokulturyUchastokDto
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.EmptyState
import com.lesovod.mobile.ui.components.ErrorState
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.SkeletonBlock
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Экран «Табель — ручной ввод» (см. reference-прототип задачи) — заполнение табеля мастером/лесничим на рядовых рабочих. */
@Composable
fun TabelScreen(
    onBack: () -> Unit,
    viewModel: TabelViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var placeSheetFor by remember { mutableStateOf<TabelRowState?>(null) }
    var workSheetFor by remember { mutableStateOf<TabelRowState?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Табель — ручной ввод",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (state.rows.isNotEmpty()) {
                Text(
                    "${state.rows.size} чел.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.m),
                )
            }
        }

        DateNavRow(date = state.date, isToday = state.isToday, onPrev = viewModel::goToPreviousDay, onNext = viewModel::goToNextDay)

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            placeholder = { Text("Поиск по ФИО или должности") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l),
                ) {
                    repeat(6) { SkeletonBlock() }
                }
                state.error != null -> ErrorState(
                    title = "Не удалось загрузить табель",
                    subtitle = state.error.orEmpty(),
                    retryLabel = "Повторить",
                    onRetry = viewModel::loadCurrentDay,
                    modifier = Modifier.padding(Spacing.l),
                )
                state.filteredRows.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Никого не найдено",
                    subtitle = "Измените запрос поиска или очистите его",
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                ) {
                    items(state.filteredRows, key = { it.sotrudnikId }) { row ->
                        EmployeeCard(
                            row = row,
                            onStatusClick = { status -> viewModel.setStatus(row.sotrudnikId, status) },
                            onPlaceClick = { placeSheetFor = row },
                            onWorkClick = { workSheetFor = row },
                            onCommentChange = { viewModel.setKommentariy(row.sotrudnikId, it) },
                        )
                    }
                }
            }
        }

        TabelFooter(state = state, onSave = viewModel::saveDay)
    }

    placeSheetFor?.let { row ->
        PlaceOfWorkSheet(
            viewModel = viewModel,
            row = row,
            onDismiss = { placeSheetFor = null },
            onSelect = { place -> viewModel.setPlace(row.sotrudnikId, place); placeSheetFor = null },
            onClear = { viewModel.setPlace(row.sotrudnikId, null); placeSheetFor = null },
        )
    }

    workSheetFor?.let { row ->
        WorkTypeSheet(
            viewModel = viewModel,
            row = row,
            onDismiss = { workSheetFor = null },
            onSelect = { id, name -> viewModel.setVidRaboty(row.sotrudnikId, id, name); workSheetFor = null },
            onClear = { viewModel.setVidRaboty(row.sotrudnikId, null, null); workSheetFor = null },
        )
    }
}

@Composable
private fun DateNavRow(date: LocalDate, isToday: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val label = remember(date) {
        val ruLocale = Locale("ru")
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ruLocale)
        val month = date.month.getDisplayName(TextStyle.FULL, ruLocale)
        "$weekday, ${date.dayOfMonth} $month ${date.year}".replaceFirstChar { it.uppercaseChar() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Предыдущий день") }
        Row(
            modifier = Modifier
                .weight(1f)
                .softCard()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (isToday) StatusChip(text = "Сегодня", tone = ChipTone.OK)
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Следующий день") }
    }
}

@Composable
private fun TabelFooter(state: TabelUiState, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        state.saveMessage?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (state.rows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Заполнено: ${state.filledCount} из ${state.rows.size}",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (state.changedCount > 0) {
                    Text(
                        "изменено строк: ${state.changedCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (state.rows.isEmpty()) 0f else state.filledCount.toFloat() / state.rows.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        val icon: (@Composable () -> Unit)? = if (state.isSaving) {
            { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
        } else null
        PrimaryButton(text = "Сохранить табель", onClick = onSave, enabled = !state.isSaving, icon = icon)
    }
}

@Composable
private fun EmployeeCard(
    row: TabelRowState,
    onStatusClick: (TabelStatus) -> Unit,
    onPlaceClick: () -> Unit,
    onWorkClick: () -> Unit,
    onCommentChange: (String) -> Unit,
) {
    val isWorking = row.status == TabelStatus.WORKED
    Column(
        modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(row.fio, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(row.dolzhnost, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            TabelStatus.entries.forEach { status ->
                FilterChip(
                    selected = row.status == status,
                    onClick = { onStatusClick(status) },
                    label = { Text(status.displayName, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (isWorking) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    PickerField(
                        label = "Место работы",
                        value = row.place?.label,
                        placeholder = "не указано",
                        onClick = onPlaceClick,
                        modifier = Modifier.weight(1f),
                    )
                    PickerField(
                        label = "Вид работы",
                        value = row.vidRabotyNazvanie,
                        placeholder = "не указан",
                        onClick = onWorkClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = row.kommentariy,
                    onValueChange = onCommentChange,
                    singleLine = true,
                    placeholder = { Text("Комментарий") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .softCard()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value ?: placeholder,
            style = MaterialTheme.typography.labelLarge,
            color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------- «Место работы» ----------

private enum class PlaceTab { DELYANKA, LESOKULTURY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceOfWorkSheet(
    viewModel: TabelViewModel,
    row: TabelRowState,
    onDismiss: () -> Unit,
    onSelect: (TabelPlace) -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var tab by remember { mutableStateOf(PlaceTab.LESOKULTURY) }
    var delyankaQuery by remember { mutableStateOf("") }
    var delyankaResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var lesokulturyQuery by remember { mutableStateOf("") }
    val lesokulturyState by viewModel.lesokulturySearch.collectAsState()
    var selectedLesokultury by remember { mutableStateOf<TabelLesokulturyUchastokDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.resetLesokulturySearch()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            Text("Место работы", style = MaterialTheme.typography.titleMedium)
            Text(
                row.fio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.m),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = tab == PlaceTab.LESOKULTURY,
                    onClick = { tab = PlaceTab.LESOKULTURY },
                    label = { Text("Лесные культуры") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = tab == PlaceTab.DELYANKA,
                    onClick = { tab = PlaceTab.DELYANKA },
                    label = { Text("Делянка") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.m))

            when (tab) {
                PlaceTab.LESOKULTURY -> {
                    OutlinedTextField(
                        value = lesokulturyQuery,
                        onValueChange = {
                            lesokulturyQuery = it
                            selectedLesokultury = null
                            viewModel.searchLesokultury(it)
                        },
                        singleLine = true,
                        placeholder = { Text("Квартал, выдел, лесничество или порода") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    when (val s = lesokulturyState) {
                        is LesokulturySearchState.Idle -> Text(
                            "Поиск по кварталу, выделу, лесничеству и породе · от 2 символов",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is LesokulturySearchState.Loading -> Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) { repeat(3) { SkeletonBlock() } }
                        is LesokulturySearchState.Empty -> Text(
                            "По запросу «$lesokulturyQuery» ничего не найдено. Место можно оставить пустым.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is LesokulturySearchState.Results -> Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            s.items.forEach { item ->
                                val label = "Кв. ${item.kvartal.orEmpty()} · выд. ${item.vydel.orEmpty()}"
                                val selected = selectedLesokultury?.id == item.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softCard()
                                        .clickable { selectedLesokultury = item }
                                        .padding(Spacing.s),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            listOfNotNull(item.lesnichestvo?.let { "$it лесничество" }, item.glavnayaPoroda)
                                                .joinToString(" · "),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (selected) StatusChip(text = "Выбрано", tone = ChipTone.OK)
                                }
                            }
                        }
                    }
                }
                PlaceTab.DELYANKA -> {
                    // Ограничение: GET /api/delyanki/for-map отдаёт delyanka_id (id всей делянки),
                    // а не id конкретного delyanka_item (выдела), который требуется полю
                    // tabel_zapis.delyanka_item_id. Показываем найденные делянки только для справки,
                    // выбор через эту вкладку отключён, чтобы не отправить на сервер неверный id.
                    OutlinedTextField(
                        value = delyankaQuery,
                        onValueChange = {
                            delyankaQuery = it
                            scope.launch { delyankaResults = viewModel.searchDelyankiForMapLabelsOnly(it) }
                        },
                        singleLine = true,
                        placeholder = { Text("Квартал, выдел или название делянки") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Выбор делянки временно недоступен: приложению нужен id конкретного выдела " +
                            "(delyanka_item_id), а этот справочник отдаёт только id всей делянки. " +
                            "Используйте вкладку «Лесные культуры» или оставьте место пустым.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.s))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        delyankaResults.forEach { label ->
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.s),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Без места", onClick = onClear, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Выбрать",
                    onClick = {
                        selectedLesokultury?.let {
                            onSelect(
                                TabelPlace.Lesokultury(
                                    it.id,
                                    "кв. ${it.kvartal.orEmpty()} · выд. ${it.vydel.orEmpty()} · культуры",
                                ),
                            )
                        }
                    },
                    enabled = selectedLesokultury != null,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}

// ---------- «Вид работы» ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WorkTypeSheet(
    viewModel: TabelViewModel,
    row: TabelRowState,
    onDismiss: () -> Unit,
    onSelect: (Int, String) -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val state by viewModel.uiState.collectAsState()
    var newOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            Text("Вид работы", style = MaterialTheme.typography.titleMedium)
            Text(
                row.fio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.m),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                state.vidyRabot.forEach { vid ->
                    FilterChip(
                        selected = row.vidRabotyId == vid.id,
                        onClick = { onSelect(vid.id, vid.nazvanie) },
                        label = { Text(vid.nazvanie) },
                    )
                }
                FilterChip(
                    selected = newOpen,
                    onClick = { newOpen = !newOpen },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("новый вид работы") },
                )
            }

            if (newOpen) {
                Spacer(Modifier.height(Spacing.m))
                Column(
                    modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Text("Название нового вида работы", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text("Например, «Сбор порубочных остатков»") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(text = "Отмена", onClick = { newOpen = false; newName = "" }, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            text = "Создать и выбрать",
                            onClick = {
                                viewModel.createAndSelectVidRaboty(row.sotrudnikId, newName)
                                newOpen = false
                                newName = ""
                                onDismiss()
                            },
                            enabled = newName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.l))
            SecondaryButton(text = "Без вида работы", onClick = onClear)
            Spacer(Modifier.height(Spacing.m))
        }
    }
}

