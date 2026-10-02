package com.lesovod.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.session.canReportBreakdown
import com.lesovod.mobile.data.session.canTrelevka
import com.lesovod.mobile.ui.bot.WorkReportLocationMode
import com.lesovod.mobile.ui.bot.WorkReportViewModel
import com.lesovod.mobile.ui.bot.filterBySearch
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.components.UchastokSelector
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Экран 4 редизайна «Поляна» (docs/SCREENS.md) — «Отчёт о работе». */
@Composable
fun WorkReportScreen(
    onReportBreakdown: () -> Unit,
    onReportTrelevka: () -> Unit = {},
    viewModel: WorkReportViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val session by viewModel.session.collectAsState()
    val canReportBreakdown = session?.role?.canReportBreakdown == true
    val canTrelevka = session?.role?.canTrelevka == true

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* отчёт всё равно отправится — геометка просто не приложится без разрешения */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Отчёт о работе")

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        ) {
            if (state.submitted) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Отчёт отправлен", tone = ChipTone.OK)
                    Text(
                        "Он появится в журнале у лесничего на проверке.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Отправить ещё один", onClick = viewModel::resetSubmitted)
                }
            } else if (state.queuedOffline) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Нет сети — отчёт сохранён на устройстве", tone = ChipTone.WARN)
                    Text(
                        "Он отправится автоматически, как только появится связь.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Отправить ещё один", onClick = viewModel::resetSubmitted)
                }
            } else {
                state.prefillNote?.let { StatusChip(text = it, tone = ChipTone.PRIMARY) }

                OutlinedTextField(
                    value = state.tipRaboty,
                    onValueChange = viewModel::onTipRabotyChange,
                    label = { Text("Тип работы") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Локация отчёта: делянка из справочника, участок лесных культур (поиск + год)
                // или ручной ввод — запасной путь для работ вне заведённых объектов.
                Text("Где велась работа", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    WorkReportLocationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.locationMode == mode,
                            onClick = { viewModel.setLocationMode(mode) },
                            label = { Text(mode.label) },
                            enabled = !state.isSubmitting,
                        )
                    }
                }
                if (state.locationMode == WorkReportLocationMode.LESOKULTURY) {
                    when {
                        state.isLoadingUchastki -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        state.uchastkiError != null -> {
                            StatusChip(text = state.uchastkiError.orEmpty(), tone = ChipTone.ERROR)
                            SecondaryButton(text = "Повторить", onClick = viewModel::retryLoadUchastki)
                        }
                        else -> UchastokSelector(
                            uchastki = state.uchastki,
                            selected = state.selectedUchastok,
                            onSelect = viewModel::selectUchastok,
                            enabled = !state.isSubmitting,
                        )
                    }
                } else if (state.locationMode == WorkReportLocationMode.DELYANKA) {
                    val selectedDelyanka = state.selectedDelyanka
                    if (selectedDelyanka != null) {
                        SelectedDelyankaCard(
                            delyanka = selectedDelyanka,
                            enabled = !state.isSubmitting,
                            onChange = viewModel::openDelyankaPicker,
                        )
                    } else {
                        SecondaryButton(
                            text = "Выбрать делянку",
                            onClick = viewModel::openDelyankaPicker,
                            enabled = !state.isSubmitting,
                            icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = state.kvartal,
                        onValueChange = viewModel::onKvartalChange,
                        label = { Text("Квартал (необязательно)") },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedTextField(
                            value = state.vydelInput,
                            onValueChange = viewModel::onVydelInputChange,
                            label = { Text("Выдел") },
                            singleLine = true,
                            enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Добавить",
                            onClick = viewModel::addVydel,
                            enabled = !state.isSubmitting && state.vydelInput.isNotBlank(),
                            modifier = Modifier.weight(0.6f),
                        )
                    }

                    if (state.vydels.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            state.vydels.forEach { vydel ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.removeVydel(vydel) },
                                    label = { Text(vydel) },
                                    trailingIcon = {
                                        Icon(Icons.Filled.Close, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = state.opisanie,
                    onValueChange = viewModel::onOpisanieChange,
                    label = { Text("Что сделано (необязательно)") },
                    enabled = !state.isSubmitting,
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.obyom,
                    onValueChange = viewModel::onObyomChange,
                    label = { Text("Объём, м³ (необязательно)") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                PhotoPickerField(uri = state.photoUri, onPicked = viewModel::onPhotoPicked)

                if (state.error != null) {
                    StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
                }

                PrimaryButton(
                    text = "Отправить отчёт",
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    icon = if (state.isSubmitting) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                )
            }

            if (canReportBreakdown) {
                SecondaryButton(
                    text = "Сообщить о поломке техники",
                    onClick = onReportBreakdown,
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                )
            }

            if (canTrelevka) {
                SecondaryButton(
                    text = "Отметить трелёвку",
                    onClick = onReportTrelevka,
                    icon = { Icon(Icons.Filled.LocalShipping, contentDescription = null) },
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (state.showDelyankaPicker) {
        DelyankaPickerSheet(
            query = state.delyankaSearchQuery,
            onQueryChange = viewModel::onDelyankaSearchQueryChange,
            delyanki = state.delyanki.filterBySearch(state.delyankaSearchQuery),
            isLoading = state.isLoadingDelyanki,
            error = state.delyankiError,
            onRetry = viewModel::retryLoadDelyanki,
            onSelect = viewModel::selectDelyanka,
            onDismiss = viewModel::dismissDelyankaPicker,
        )
    }
}

/** Карточка уже выбранной делянки — вместо свободного текста показываем то, что реально в базе. */
@Composable
private fun SelectedDelyankaCard(delyanka: DelyankaMapRefDto, enabled: Boolean, onChange: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                delyanka.nazvanie?.takeIf { it.isNotBlank() } ?: "Делянка без названия",
                style = MaterialTheme.typography.titleMedium,
            )
            val details = listOfNotNull(
                delyanka.kvartal?.let { "квартал $it" },
                delyanka.vydel?.let { "выдел $it" },
                delyanka.lesnichestvo,
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onChange, enabled = enabled) {
            Icon(Icons.Filled.Edit, contentDescription = "Изменить делянку")
        }
    }
}

/** Поиск/выбор реальной делянки — по названию, кварталу, выделу или лесничеству. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelyankaPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    delyanki: List<DelyankaMapRefDto>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (DelyankaMapRefDto) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Выбор делянки", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Поиск по названию, кварталу, выделу") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                isLoading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(vertical = Spacing.m),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Загружаем делянки…", style = MaterialTheme.typography.bodyMedium)
                }

                error != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = error, tone = ChipTone.ERROR)
                    SecondaryButton(text = "Повторить", onClick = onRetry)
                }

                delyanki.isEmpty() -> Text(
                    "Ничего не найдено — работа не на заведённой делянке? Закройте окно и укажите квартал/выдел вручную.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.m),
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(delyanki, key = { it.delyankaId }) { delyanka ->
                        DelyankaRow(delyanka = delyanka, onClick = { onSelect(delyanka) })
                    }
                }
            }

            Spacer(Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun DelyankaRow(delyanka: DelyankaMapRefDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .clickable(onClick = onClick)
            .padding(Spacing.m),
    ) {
        Text(
            delyanka.nazvanie?.takeIf { it.isNotBlank() } ?: "Делянка без названия",
            style = MaterialTheme.typography.titleMedium,
        )
        val details = listOfNotNull(
            delyanka.kvartal?.let { "квартал $it" },
            delyanka.vydel?.let { "выдел $it" },
            delyanka.lesnichestvo,
        ).joinToString(" · ")
        if (details.isNotBlank()) {
            Text(
                details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
