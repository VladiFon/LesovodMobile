package com.lesovod.mobile.ui.tabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.network.dto.DelyankaByLocationDto
import com.lesovod.mobile.data.network.dto.TabelLesokulturyUchastokDto
import com.lesovod.mobile.ui.theme.ForestErrorBorder
import com.lesovod.mobile.ui.theme.ForestErrorContainer
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestSuccess
import com.lesovod.mobile.ui.theme.ForestSurfaceContainer
import kotlinx.coroutines.delay

@Composable
fun TabelPlacePane(state: TabelUiState, viewModel: TabelViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = viewModel::closePane) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Место работы", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }

        state.editingEmployee?.let { TabelWhoCard(it) }

        PlaceSegmentedTabs(selected = state.placeTab, onSelect = viewModel::setPlaceTab)

        Box(modifier = Modifier.weight(1f)) {
            when (state.placeTab) {
                PlaceTab.DELYANKA -> DelyankaPane(state = state, viewModel = viewModel)
                PlaceTab.LESOKULTURY -> LesokulturyPane(state = state, viewModel = viewModel)
            }
        }

        PlaceFooter(state = state, viewModel = viewModel)
    }
}

@Composable
internal fun TabelWhoCard(employee: TabelEmployee) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ForestOutline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column {
                Text(employee.fio, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(employee.dolzhnost, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (employee.status != null) {
                Surface(shape = RoundedCornerShape(999.dp), color = ForestSuccess) {
                    Text(
                        employee.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceSegmentedTabs(selected: PlaceTab, onSelect: (PlaceTab) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ForestSurfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            SegmentButton("Делянка", selected == PlaceTab.DELYANKA, Modifier.weight(1f)) { onSelect(PlaceTab.DELYANKA) }
            SegmentButton("Лесные культуры", selected == PlaceTab.LESOKULTURY, Modifier.weight(1f)) { onSelect(PlaceTab.LESOKULTURY) }
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DelyankaPane(state: TabelUiState, viewModel: TabelViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = state.placeKvartal,
                onValueChange = viewModel::onPlaceKvartalChange,
                label = { Text("Квартал") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.placeVydel,
                onValueChange = viewModel::onPlaceVydelChange,
                label = { Text("Выдел") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::findDelyanka, modifier = Modifier.height(56.dp)) {
                Text("Найти")
            }
        }

        if (state.delyankaLoading) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.delyankaSearched && state.delyankaResults.isEmpty()) {
            NotFoundWarning(
                title = "Делянка не найдена",
                text = "Кв. ${state.delyankaSearchedKvartal}, выд. ${state.delyankaSearchedVydel} нет в базе. " +
                    "Проверьте номера или оставьте место пустым — табель сохранится и так.",
            )
        } else if (state.delyankaResults.isNotEmpty()) {
            Text(
                "Найдено: ${state.delyankaResults.size} " + delyankaWordForm(state.delyankaResults.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.delyankaResults.forEach { dto ->
                    DelyankaChip(
                        dto = dto,
                        label = dto.toPlaceLabel(state.delyankaSearchedKvartal, state.delyankaSearchedVydel),
                        selected = (state.selectedPlace as? TabelPlace.Delyanka)?.itemId == dto.itemId,
                        onClick = { viewModel.selectDelyankaResult(dto) },
                    )
                }
            }
            SelectedPlaceSummary(state.selectedPlace)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DelyankaChip(dto: DelyankaByLocationDto, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else ForestOutline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(end = 6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun delyankaWordForm(count: Int): String = if (count == 1) "делянка" else "делянки"

@Composable
private fun SelectedPlaceSummary(place: TabelPlace?) {
    if (place == null) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ForestSuccess.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Будет записано", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(place.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NotFoundWarning(title: String, text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ForestErrorContainer,
        border = BorderStroke(1.dp, ForestErrorBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun LesokulturyPane(state: TabelUiState, viewModel: TabelViewModel) {
    LaunchedEffect(state.kulQuery) {
        delay(300)
        viewModel.searchLesokultury(state.kulQuery)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.kulQuery,
            onValueChange = viewModel::onKulQueryChange,
            placeholder = { Text("Квартал, выдел, лесничество или порода") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { if (state.kulLoading) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Text(
            when {
                state.kulQuery.trim().length < 2 -> "Поиск по кварталу, выделу, лесничеству и породе · от 2 символов"
                state.kulLoading -> "Ищем участки…"
                state.kulSearched -> "Найдено: ${state.kulResults.size} " + uchastokWordForm(state.kulResults.size)
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        when {
            state.kulSearched && !state.kulLoading && state.kulResults.isEmpty() -> NotFoundWarning(
                title = "Ничего не найдено",
                text = "По запросу «${state.kulQuery}» лесных культур нет. Проверьте номер квартала или выдела, " +
                    "название лесничества или породу. Место можно оставить пустым.",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(state.kulResults, key = { it.id }) { dto ->
                    KulResultCard(
                        dto = dto,
                        selected = (state.selectedPlace as? TabelPlace.Lesokultury)?.uchastokId == dto.id,
                        onClick = { viewModel.selectKulResult(dto) },
                    )
                }
            }
        }
    }
}

private fun uchastokWordForm(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "участок"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "участка"
    else -> "участков"
}

@Composable
private fun KulResultCard(dto: TabelLesokulturyUchastokDto, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ForestSuccess.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (selected) ForestSuccess else ForestOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Кв. ${dto.kvartal.orEmpty()} · выд. ${dto.vydel.orEmpty()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                dto.lesnichestvo?.let {
                    Text("$it лесничество", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                dto.glavnayaPoroda?.let {
                    Surface(shape = RoundedCornerShape(999.dp), color = ForestSurfaceContainer, modifier = Modifier.padding(top = 6.dp)) {
                        Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                    }
                }
            }
            if (selected) {
                Surface(shape = RoundedCornerShape(999.dp), color = ForestSuccess, modifier = Modifier.size(24.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private enum class PlaceFooterMode { SET, NONE_ENABLED, DISABLED }

private fun placeFooterMode(state: TabelUiState): PlaceFooterMode {
    if (state.selectedPlace != null) return PlaceFooterMode.SET
    val notFound = when (state.placeTab) {
        PlaceTab.DELYANKA -> state.delyankaSearched && state.delyankaResults.isEmpty()
        PlaceTab.LESOKULTURY -> state.kulSearched && !state.kulLoading && state.kulResults.isEmpty()
    }
    return if (notFound) PlaceFooterMode.NONE_ENABLED else PlaceFooterMode.DISABLED
}

@Composable
private fun PlaceFooter(state: TabelUiState, viewModel: TabelViewModel) {
    val mode = placeFooterMode(state)
    val primaryLabel = if (mode == PlaceFooterMode.NONE_ENABLED) {
        "Оставить без места"
    } else if (state.placeTab == PlaceTab.DELYANKA) {
        "Выбрать место"
    } else {
        "Выбрать участок"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = {
                if (mode == PlaceFooterMode.NONE_ENABLED) viewModel.clearPlace() else viewModel.confirmPlace()
            },
            enabled = mode != PlaceFooterMode.DISABLED,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(primaryLabel)
        }
        if (mode != PlaceFooterMode.NONE_ENABLED) {
            OutlinedButton(onClick = viewModel::clearPlace, modifier = Modifier.fillMaxWidth()) {
                Text("Оставить без места")
            }
        }
    }
}
