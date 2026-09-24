package com.lesovod.mobile.ui.tabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.network.dto.VidRabotyDto
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestSuccess

@Composable
fun TabelWorkPane(state: TabelUiState, viewModel: TabelViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = viewModel::closePane) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Вид работы", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }

        state.editingEmployee?.let { TabelWhoCard(it) }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Выберите вид работы",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            WorkTypeFlow(
                vidyRaboty = state.vidyRaboty,
                selectedId = state.selectedWorkTypeId,
                newOpen = state.newWorkTypeOpen,
                onSelect = viewModel::selectWorkType,
                onToggleNew = viewModel::toggleNewWorkType,
            )

            if (state.newWorkTypeOpen) {
                NewWorkTypeForm(
                    creating = state.creatingWorkType,
                    onCancel = viewModel::toggleNewWorkType,
                    onCreate = viewModel::createWorkType,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = viewModel::confirmWorkType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Готово")
            }
            OutlinedButton(onClick = viewModel::clearWorkType, modifier = Modifier.fillMaxWidth()) {
                Text("Без вида работы")
            }
        }
    }
}

/** Простая обёртка-перенос строк без FlowRow (экспериментальный API) — фиксированное число колонок в ряд. */
@Composable
private fun WorkTypeFlow(
    vidyRaboty: List<VidRabotyDto>,
    selectedId: Int?,
    newOpen: Boolean,
    onSelect: (VidRabotyDto) -> Unit,
    onToggleNew: () -> Unit,
) {
    val rows = remember(vidyRaboty) { vidyRaboty.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { dto ->
                    WorkTypeChip(
                        label = dto.nazvanie,
                        selected = selectedId == dto.id,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(dto) },
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        NewWorkTypeChip(open = newOpen, onClick = onToggleNew)
    }
}

@Composable
private fun WorkTypeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else ForestOutline),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NewWorkTypeChip(open: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (open) ForestSuccess.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(1.5.dp, ForestSuccess.copy(alpha = if (open) 1f else 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = ForestSuccess, modifier = Modifier.padding(end = 6.dp))
            Text("новый вид работы", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ForestSuccess)
        }
    }
}

@Composable
private fun NewWorkTypeForm(creating: Boolean, onCancel: () -> Unit, onCreate: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, ForestSuccess),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Название нового вида работы", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Например, «Сбор порубочных остатков»") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.height(48.dp)) {
                    Text("Отмена")
                }
                FilledTonalButton(
                    onClick = { onCreate(text) },
                    enabled = text.isNotBlank() && !creating,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    if (creating) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Создать и выбрать")
                    }
                }
            }
            Text(
                "Новый вид сразу появится в списке и будет выбран для этого сотрудника.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
