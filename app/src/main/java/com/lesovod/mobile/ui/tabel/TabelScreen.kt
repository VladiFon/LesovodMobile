package com.lesovod.mobile.ui.tabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestError
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestSuccess
import com.lesovod.mobile.ui.theme.ForestSurface
import com.lesovod.mobile.ui.theme.ForestSurfaceContainer
import com.lesovod.mobile.ui.theme.ForestTextMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

@Composable
fun TabelScreen(onBack: () -> Unit, viewModel: TabelViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    when (state.pane) {
        TabelPane.MAIN -> TabelMainPane(state = state, viewModel = viewModel, onBack = onBack)
        TabelPane.PLACE -> TabelPlacePane(state = state, viewModel = viewModel)
        TabelPane.WORK -> TabelWorkPane(state = state, viewModel = viewModel)
    }
}

@Composable
private fun TabelMainPane(state: TabelUiState, viewModel: TabelViewModel, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Табель — ручной ввод",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${state.employees.size} чел.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            DateNavRow(state = state, viewModel = viewModel)
            SearchField(query = state.searchQuery, onQueryChange = viewModel::onSearchQueryChange)

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null && state.employees.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> EmployeeList(state = state, viewModel = viewModel)
                }
            }

            TabelFooter(state = state, onSave = viewModel::saveDay)
        }

        state.saveMessage?.let { message ->
            LaunchedEffect(message) {
                delay(2500)
                viewModel.dismissSaveMessage()
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 110.dp, start = 16.dp, end = 16.dp),
                ) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateNavRow(state: TabelUiState, viewModel: TabelViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        RoundIconButton(icon = Icons.Filled.ChevronLeft, contentDescription = "Предыдущий день", onClick = { viewModel.changeDate(-1) })

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ForestOutline),
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val label = state.date.format(DATE_LABEL_FORMAT)
                Text(
                    label.replaceFirstChar { it.titlecase(Locale("ru")) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.date == LocalDate.now()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ForestSuccess.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            "Сегодня",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForestSuccess,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        RoundIconButton(icon = Icons.Filled.ChevronRight, contentDescription = "Следующий день", onClick = { viewModel.changeDate(1) })
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ForestOutline),
        modifier = Modifier.size(44.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Поиск по ФИО или должности") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmployeeList(state: TabelUiState, viewModel: TabelViewModel) {
    val filtered = state.employees.filter { it.matches(state.searchQuery) }
    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Никого не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(filtered, key = { it.sotrudnikId }) { employee ->
            EmployeeCard(
                employee = employee,
                editingComment = state.editingCommentId == employee.sotrudnikId,
                onStatusClick = { status -> viewModel.setStatus(employee.sotrudnikId, status) },
                onPlaceClick = { viewModel.openPlacePicker(employee.sotrudnikId) },
                onWorkClick = { viewModel.openWorkPicker(employee.sotrudnikId) },
                onCommentClick = { viewModel.startEditComment(employee.sotrudnikId) },
                onCommentCommit = { text -> viewModel.commitComment(employee.sotrudnikId, text) },
            )
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: TabelEmployee,
    editingComment: Boolean,
    onStatusClick: (TabelStatus) -> Unit,
    onPlaceClick: () -> Unit,
    onWorkClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCommentCommit: (String) -> Unit,
) {
    val isWorking = employee.status == TabelStatus.WORK
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isWorking) ForestSuccess.copy(alpha = 0.5f) else ForestOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(employee.fio, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
            ) {
                Text(
                    employee.dolzhnost,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                employee.mobileStatusHint?.let { hint ->
                    Surface(shape = RoundedCornerShape(999.dp), color = ForestSurfaceContainer) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            StatusChipsRow(selected = employee.status, onSelect = onStatusClick)

            if (isWorking) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(ForestSuccess.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldButton(
                            label = "Место работы",
                            value = employee.place?.label,
                            emptyText = "не указано",
                            onClick = onPlaceClick,
                            modifier = Modifier.weight(1f),
                        )
                        FieldButton(
                            label = "Вид работы",
                            value = employee.vidRabotyLabel,
                            emptyText = "не указан",
                            onClick = onWorkClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CommentField(
                        comment = employee.kommentariy,
                        editing = editingComment,
                        onClick = onCommentClick,
                        onCommit = onCommentCommit,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChipsRow(selected: TabelStatus?, onSelect: (TabelStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        TabelStatus.entries.forEach { status ->
            val on = selected == status
            val (bg, fg) = statusColors(status, on)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = bg,
                border = if (status == TabelStatus.DAY_OFF && on) BorderStroke(1.5.dp, ForestTextMuted) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { onSelect(status) },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun statusColors(status: TabelStatus, selected: Boolean): Pair<Color, Color> {
    if (!selected) return ForestSurfaceContainer to ForestTextMuted
    return when (status) {
        TabelStatus.WORK -> ForestSuccess to Color.White
        TabelStatus.NOT_WORK -> ForestTextMuted to Color.White
        TabelStatus.SICK -> ForestError to Color.White
        TabelStatus.VACATION -> ForestAccent to Color.White
        TabelStatus.DAY_OFF -> ForestSurface to ForestTextMuted
    }
}

@Composable
private fun FieldButton(label: String, value: String?, emptyText: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ForestOutline),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value ?: emptyText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CommentField(comment: String, editing: Boolean, onClick: () -> Unit, onCommit: (String) -> Unit) {
    if (editing) {
        var text by remember(editing) { mutableStateOf(comment) }
        // onFocusChanged шлёт своё первое событие ("не в фокусе") ещё до того, как
        // LaunchedEffect ниже успеет реально поставить фокус — без этого флага комментарий
        // закрывался бы сам собой сразу при открытии, не дав ничего напечатать.
        var hasFocused by remember(editing) { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Комментарий") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(text); focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hasFocused = true
                    } else if (hasFocused) {
                        onCommit(text)
                    }
                },
        )
    } else if (comment.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ForestOutline),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Text(
                comment,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, ForestSuccess.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = ForestSuccess, modifier = Modifier.size(16.dp))
                Text("Комментарий", style = MaterialTheme.typography.labelMedium, color = ForestSuccess, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun TabelFooter(state: TabelUiState, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Заполнено: ${state.filledCount} из ${state.employees.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "изменено строк: ${state.dirtyCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val fraction = if (state.employees.isEmpty()) 0f else state.filledCount / state.employees.size.toFloat()
        LinearProgressIndicator(
            progress = { fraction },
            color = ForestSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp)
                .height(6.dp),
        )
        FilledTonalButton(
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Сохранить табель")
            }
        }
    }
}
