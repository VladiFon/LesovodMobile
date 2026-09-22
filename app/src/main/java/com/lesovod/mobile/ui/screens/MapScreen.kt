package com.lesovod.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.map.DONE_COLOR
import com.lesovod.mobile.ui.map.DelyankaCard
import com.lesovod.mobile.ui.map.ForestMapView
import com.lesovod.mobile.ui.map.GeoNoteDraft
import com.lesovod.mobile.ui.map.GeoNoteMarker
import com.lesovod.mobile.ui.map.MapLayers
import com.lesovod.mobile.ui.map.MapSelection
import com.lesovod.mobile.ui.map.ShapeKind
import com.lesovod.mobile.ui.map.MapUiState
import com.lesovod.mobile.ui.map.MapViewModel
import com.lesovod.mobile.ui.map.VydelCard
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestSurface

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val density = LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* карта и без геолокации работает — просто не покажет "где я" */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    var layersOpen by remember { mutableStateOf(false) }
    var cardHeightPx by remember { mutableStateOf(0) }

    val cardVisible = state.cardOpen
    // первый тап только выделяет участок — внизу подсказка, что второй тап откроет таксацию
    val hintVisible = state.selection != null && !cardVisible
    var lastSelection by remember { mutableStateOf(state.selection) }
    if (state.selection != null) lastSelection = state.selection
    val bottomInset = when {
        cardVisible -> with(density) { cardHeightPx.toDp() }
        hintVisible -> 76.dp
        else -> 0.dp
    }
    // Scaffold в MainScaffold уже отдаёт содержимому отступ под статус-бар — второй раз его не добавляем
    val topInset = 0.dp

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.refreshCompleted()
            viewModel.applyDefaultIfChanged()
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(
        enabled = cardVisible || layersOpen || state.selection != null || state.noteDraft != null || state.selectedGeoNote != null,
    ) {
        when {
            state.noteDraft != null -> viewModel.dismissNoteDraft()
            state.selectedGeoNote != null -> viewModel.dismissGeoNotePopup()
            layersOpen -> layersOpen = false
            cardVisible -> viewModel.closeCard()
            else -> viewModel.clearSelection()
        }
    }

    // Карта на весь экран — всё остальное лежит поверх неё полупрозрачными элементами.
    Box(modifier = Modifier.fillMaxSize()) {
        ForestMapView(
            kvartaly = state.kvartaly,
            vydela = state.vydela,
            lesoseki = state.lesoseki,
            layers = state.layers,
            selection = state.selection,
            completed = state.completed,
            fitToken = state.fitToken,
            lesosekiTappable = !state.usedFallbackRectangles,
            geoNotes = state.geoNotes,
            onShapeTap = { layersOpen = false; viewModel.onShapeTap(it) },
            onEmptyTap = { layersOpen = false; viewModel.clearSelection() },
            onGeoNoteTap = { layersOpen = false; viewModel.onGeoNoteTap(it) },
            onMapLongPress = { lat, lon -> layersOpen = false; viewModel.startNoteDraft(lat, lon) },
            onViewportChanged = viewModel::onViewportChanged,
            initialCamera = remember { viewModel.camera },
            controlsTopPadding = topInset + 68.dp,
            bottomInset = bottomInset,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = topInset + 12.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LesnichestvoPill(
                    options = state.lesnichestva.keys.toList(),
                    selected = state.selectedLesnichestvo,
                    loading = state.isLoadingLayers,
                    onSelected = viewModel::selectLesnichestvo,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = { layersOpen = !layersOpen },
                    shape = CircleShape,
                    color = if (layersOpen) ForestPrimary.copy(alpha = 0.92f) else ForestSurface.copy(alpha = 0.88f),
                    contentColor = if (layersOpen) MaterialTheme.colorScheme.onPrimary else ForestPrimary,
                    border = BorderStroke(1.dp, ForestOutline.copy(alpha = 0.7f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Слои карты" },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Layers, contentDescription = null) }
                }
            }

            AnimatedVisibility(
                visible = layersOpen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier.align(Alignment.End),
            ) {
                LayersPanel(layers = state.layers, onChange = viewModel::setLayers, onDownload = viewModel::downloadCurrent, downloadRunning = state.download.running)
            }

            if (state.layers.vydela || state.layers.lesoseki) Box(modifier = Modifier.padding(end = 56.dp)) { Legend() } // справа — колонка кнопок

            if (state.download.running) StatusChip("Загрузка карты: ${(state.download.fraction * 100).toInt()}%")
            state.download.error?.let { StatusChip(it, isError = true) }
            state.error?.let { StatusChip(it, isError = true) }
            if (state.usedFallbackRectangles && state.vydela.isNotEmpty()) {
                StatusChip("Контуры лесосек ещё не получены — показаны границы выдела")
            }
        }

        AnimatedVisibility(
            visible = cardVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ObjectCard(
                state = state,
                onDismiss = viewModel::closeCard,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onSizeChanged { cardHeightPx = it.height + with(density) { 24.dp.roundToPx() } },
            )
        }

        AnimatedVisibility(
            visible = hintVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastSelection?.let { SelectionHint(it, onOpen = viewModel::openSelected, onClear = viewModel::clearSelection) }
        }

        var lastNoteDraft by remember { mutableStateOf(state.noteDraft) }
        if (state.noteDraft != null) lastNoteDraft = state.noteDraft
        AnimatedVisibility(
            visible = state.noteDraft != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            (state.noteDraft ?: lastNoteDraft)?.let { draft ->
                GeoNoteDraftCard(
                    draft = draft,
                    onTextChange = viewModel::updateNoteDraftText,
                    onPhotoChange = viewModel::updateNoteDraftPhoto,
                    onSubmit = viewModel::submitNoteDraft,
                    onDismiss = viewModel::dismissNoteDraft,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        var lastGeoNote by remember { mutableStateOf(state.selectedGeoNote) }
        if (state.selectedGeoNote != null) lastGeoNote = state.selectedGeoNote
        AnimatedVisibility(
            visible = state.selectedGeoNote != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            (state.selectedGeoNote ?: lastGeoNote)?.let { note ->
                GeoNotePopup(
                    note = note,
                    onDismiss = viewModel::dismissGeoNotePopup,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Форма новой метки — открывается долгим нажатием на карту, отправляет текст и/или фото. */
@Composable
private fun GeoNoteDraftCard(
    draft: GeoNoteDraft,
    onTextChange: (String) -> Unit,
    onPhotoChange: (android.net.Uri?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ForestSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, ForestOutline),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    "Новая метка",
                    style = MaterialTheme.typography.titleLarge,
                    color = ForestPrimary,
                    modifier = Modifier.padding(end = 40.dp),
                )

                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onTextChange,
                    label = { Text("Текст (необязательно)") },
                    enabled = !draft.isSubmitting,
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )

                Box(modifier = Modifier.padding(top = 12.dp)) {
                    PhotoPickerField(uri = draft.photoUri, onPicked = onPhotoChange, label = "Добавить фото")
                }

                if (draft.error != null) {
                    Text(
                        draft.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Button(
                    onClick = onSubmit,
                    enabled = !draft.isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    if (draft.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Сохранить метку")
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Карточка уже существующей метки — текст, "есть фото" (без загрузки самого файла) и автор/дата, если сервер их прислал. */
@Composable
private fun GeoNotePopup(note: GeoNoteMarker, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ForestSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, ForestOutline),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column(modifier = Modifier.padding(start = 18.dp, end = 40.dp, top = 14.dp, bottom = 14.dp)) {
                Text("Метка", style = MaterialTheme.typography.titleMedium, color = ForestPrimary)
                note.authorFio?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!note.noteText.isNullOrBlank()) {
                    Text(note.noteText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
                if (!note.photoPath.isNullOrBlank()) {
                    Text(
                        "Есть фото",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                note.createdAt?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LesnichestvoPill(
    options: List<String>,
    selected: String?,
    loading: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { if (options.isNotEmpty()) expanded = true },
            shape = RoundedCornerShape(22.dp),
            color = ForestSurface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, ForestOutline.copy(alpha = 0.7f)),
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Лесничество", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        selected ?: "Выберите",
                        style = MaterialTheme.typography.titleSmall,
                        color = ForestPrimary,
                        maxLines = 1,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = ForestPrimary)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Всего четыре галочки: без тематических раскрасок, легенд и настроек масштабов видимости. */
@Composable
private fun LayersPanel(layers: MapLayers, onChange: (MapLayers) -> Unit, onDownload: () -> Unit, downloadRunning: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ForestSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, ForestOutline.copy(alpha = 0.7f)),
        shadowElevation = 4.dp,
        modifier = Modifier.width(210.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            LayerRow("Кварталы", layers.kvartaly) { onChange(layers.copy(kvartaly = it)) }
            LayerRow("Выделы", layers.vydela) { onChange(layers.copy(vydela = it)) }
            LayerRow("Делянки", layers.lesoseki) { onChange(layers.copy(lesoseki = it)) }
            LayerRow("Спутниковый снимок", layers.satellite) { onChange(layers.copy(satellite = it)) }
            HorizontalDivider(color = ForestOutline)
            TextButton(onClick = onDownload, enabled = !downloadRunning, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (downloadRunning) "Идёт загрузка…" else "Скачать карту", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LayerRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(onClick = { onCheckedChange(!checked) }, color = Color.Transparent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(10.dp),
                colors = CheckboxDefaults.colors(checkedColor = ForestPrimary),
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusChip(text: String, isError: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ForestSurface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary).copy(alpha = 0.6f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Карточка снизу поверх карты — не модальная: карту вокруг можно двигать, тап по пустому месту закрывает. */
@Composable
private fun ObjectCard(state: MapUiState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ForestSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, ForestOutline),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                when {
                    state.isLoadingCard -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }

                    state.cardError != null -> Text(
                        state.cardError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 40.dp, top = 4.dp, bottom = 4.dp),
                    )

                    state.selectedDelyanka != null -> DelyankaCardContent(state.selectedDelyanka)

                    state.cardNotice != null -> Text(
                        state.cardNotice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 40.dp, top = 4.dp, bottom = 4.dp),
                    )

                    state.selectedCard != null -> VydelCardContent(state.selectedCard, state.selection?.kind)

                    state.selectedKvartalLabel != null -> {
                        Text(
                            state.selectedKvartalLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = ForestPrimary,
                            modifier = Modifier.padding(end = 40.dp),
                        )
                        Text(
                            "Нажмите на выдел, чтобы увидеть таксацию",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VydelCardContent(card: VydelCard, kind: ShapeKind?) {
    var expanded by remember(card) { mutableStateOf(false) }

    Text(
        (if (kind == ShapeKind.LESOSEKA) "Делянка · " else "Выдел · ") + "кв. ${card.kvartal ?: "—"}, выд. ${card.vydel ?: "—"}",
        style = MaterialTheme.typography.titleLarge,
        color = ForestPrimary,
        modifier = Modifier.padding(end = 40.dp),
    )
    card.lesnichestvo?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Три главные цифры — сразу; остальное — по кнопке "Подробнее", чтобы карточка не закрывала карту.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Stat("Площадь", card.ploshad?.let { "$it га" }, Modifier.weight(1f))
        Stat("Запас на га", card.zapasNaGa?.let { "$it м³" }, Modifier.weight(1f))
        Stat("Полнота", card.polnota, Modifier.weight(1f))
    }

    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            InfoRow("Категория лесов", card.kategoriyaLesov)
            InfoRow("Тип леса", card.tipLesa)
            InfoRow("ТУМ", card.tlu)
            InfoRow("Бонитет", card.bonitet)
            InfoRow("Запас на выделе", card.zapasNaVydele?.let { "$it м³" })
            InfoRow("Целевая порода", card.tselevayaPoroda)
            InfoRow("Состав", card.formulaSostava)
            InfoRow("Примечания", card.primechaniya)
        }
    }

    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 2.dp)) {
        Text(if (expanded) "Свернуть" else "Подробнее", color = MaterialTheme.colorScheme.secondary)
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ForestPrimary)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private val legendItems = listOf(
    Color(0xFFFF4444) to "Рубка",
    Color(0xFFFFEB3B) to "Уход / осветление",
    Color(0xFF4CAF50) to "Посадка / дополнение",
    Color(0xFF8BC34A) to "Прочие работы",
    Color(DONE_COLOR) to "Выполнено",
)

/** Одна строка поверх карты: цвет выдела — вид работ, синий — рабочий отметил выполнение. */
@Composable
private fun Legend() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ForestSurface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, ForestOutline.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            legendItems.forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** Подсказка после первого тапа: участок выделен на карте, второй тап (или нажатие сюда) откроет таксацию. */
@Composable
private fun SelectionHint(selection: MapSelection, onOpen: () -> Unit, onClear: () -> Unit) {
    val title = selection.title()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = ForestSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, ForestOutline),
        shadowElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ForestPrimary)
                Text(
                    if (selection.vydel != null) "Нажмите ещё раз — таксация" else "Нажмите ещё раз — информация",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Снять выделение", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Заголовок выбранного объекта: делянка, выдел и квартал называются по-разному, чтобы было ясно, что выбрано. */
private fun MapSelection.title(): String = when (kind) {
    ShapeKind.LESOSEKA -> "Делянка · кв. $kvartal, выд. $vydel"
    ShapeKind.VYDEL -> "Выдел · кв. $kvartal, выд. $vydel"
    ShapeKind.KVARTAL -> "Квартал $kvartal"
}

/** Данные самой делянки (лесосеки) из документа МДО — не общая таксация выдела. */
@Composable
private fun DelyankaCardContent(card: DelyankaCard) {
    Text(
        "Делянка" + (card.nazvanie?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.titleLarge,
        color = ForestPrimary,
        modifier = Modifier.padding(end = 40.dp),
    )
    Text(
        "кв. ${card.kvartal ?: "—"}, выд. ${card.vydel ?: "—"}" + (card.statusRabot?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Stat("Площадь", card.ploshad?.let { "$it га" }, Modifier.weight(1f))
        Stat("Запас на га", card.zapasNaGa?.let { "$it м³" }, Modifier.weight(1f))
        Stat("Вырубаемый запас", card.vyrubaemyyZapas?.let { "$it м³" }, Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Stat("Возраст", card.vozrast, Modifier.weight(1f))
        Stat("Полнота", card.polnota, Modifier.weight(1f))
        Stat("Бонитет", card.bonitet, Modifier.weight(1f))
    }

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
        InfoRow("Состав", card.sostav)
        InfoRow("Тип леса", card.tipLesa)
        InfoRow("Категория лесов", card.kategoriyaLesov)
    }
    Text(
        "Данные из документа МДО — могут отличаться от общей таксации выдела",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
