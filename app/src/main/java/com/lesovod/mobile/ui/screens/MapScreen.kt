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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.map.ColorMode
import com.lesovod.mobile.ui.map.DONE_COLOR
import com.lesovod.mobile.ui.map.DelyankaCard
import com.lesovod.mobile.ui.map.DelyankaStatus
import com.lesovod.mobile.ui.map.ForestMapView
import com.lesovod.mobile.ui.map.GeoNoteDraft
import com.lesovod.mobile.ui.map.LESOKULTURY_COLOR
import com.lesovod.mobile.ui.map.LatLon
import com.lesovod.mobile.ui.map.MapLayers
import com.lesovod.mobile.ui.map.MapSelection
import com.lesovod.mobile.ui.map.MapTool
import com.lesovod.mobile.ui.map.MapUiState
import com.lesovod.mobile.ui.map.MapViewModel
import com.lesovod.mobile.ui.map.ShapeKind
import com.lesovod.mobile.ui.map.TASK_COLOR
import com.lesovod.mobile.ui.map.VydelCard
import kotlinx.coroutines.delay

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val density = LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* карта и без геолокации работает — просто не покажет "где я" */ }

    // своя подложка — файл .mbtiles; тип у таких файлов обычно не определён, поэтому "*/*"
    val offlineBasePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importOfflineBase(uri)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    // короткие сообщения ("Контур сохранён…") сами исчезают
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(4500)
            viewModel.dismissMessage()
        }
    }

    // пока идёт обмер обходом, экран не гаснет — иначе GPS-точки перестают приходить
    val view = LocalView.current
    DisposableEffect(state.walk.recording) {
        view.keepScreenOn = state.walk.recording
        onDispose { view.keepScreenOn = false }
    }

    var layersOpen by remember { mutableStateOf(false) }
    var toolsMenuOpen by remember { mutableStateOf(false) }
    var bottomPanelPx by remember { mutableStateOf(0) }

    val toolPanelVisible = state.tool != MapTool.NONE
    val cardVisible = state.cardOpen
    val bottomPanelVisible = cardVisible || toolPanelVisible || state.tasksOpen ||
        state.noteDraft != null || state.selectedGeoNote != null || state.selectedSklad != null
    // первый тап только выделяет участок — внизу подсказка, что второй тап откроет таксацию
    val hintVisible = state.selection != null && !bottomPanelVisible
    var lastSelection by remember { mutableStateOf(state.selection) }
    if (state.selection != null) lastSelection = state.selection
    val bottomInset = when {
        bottomPanelVisible -> with(density) { bottomPanelPx.toDp() }
        hintVisible -> 76.dp
        else -> 0.dp
    }
    val measureBottom = Modifier
        .padding(horizontal = 12.dp, vertical = 12.dp)
        .onSizeChanged { bottomPanelPx = it.height + with(density) { 24.dp.roundToPx() } }
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

    val closeOverlays = { layersOpen = false; toolsMenuOpen = false }

    BackHandler(
        enabled = state.search.open || cardVisible || layersOpen || toolsMenuOpen || state.selection != null ||
            state.noteDraft != null || state.selectedGeoNote != null || state.selectedSklad != null ||
            state.tasksOpen || (toolPanelVisible && !state.walk.recording),
    ) {
        when {
            state.search.open -> viewModel.openSearch(false)
            state.noteDraft != null -> viewModel.dismissNoteDraft()
            state.selectedGeoNote != null -> viewModel.dismissGeoNotePopup()
            state.selectedSklad != null -> viewModel.dismissSklad()
            state.tasksOpen -> viewModel.openTasks(false)
            layersOpen || toolsMenuOpen -> closeOverlays()
            toolPanelVisible && !state.walk.recording -> viewModel.setTool(MapTool.NONE)
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
            geoNotes = if (state.layers.geoNotes) state.geoNotes else emptyList(),
            colorMode = state.colorMode,
            workColors = state.workColors,
            delyankaStatusColors = state.delyankaStatusColors,
            lesokulturyKeys = if (state.layers.lesokultury) state.lesokulturyKeys else emptySet(),
            taskKeys = if (state.layers.tasks) viewModel.taskKeys(state) else emptySet(),
            sklady = if (state.layers.sklady) state.sklady else emptyList(),
            tool = state.tool,
            rulerPoints = state.rulerPoints,
            walkPoints = state.walk.points,
            navTarget = state.navTarget?.point,
            myLocation = state.myLocation,
            focus = state.focus,
            offlineBasePath = state.offlineBasePath.takeIf { state.layers.offlineBase },
            onSkladTap = { closeOverlays(); viewModel.onSkladTap(it) },
            onRulerTap = viewModel::onRulerTap,
            onLocationFix = viewModel::onLocationFix,
            onShapeTap = { closeOverlays(); viewModel.onShapeTap(it) },
            onEmptyTap = { closeOverlays(); viewModel.clearSelection() },
            onGeoNoteTap = { closeOverlays(); viewModel.onGeoNoteTap(it) },
            onMapLongPress = { lat, lon -> closeOverlays(); if (state.tool == MapTool.NONE) viewModel.startNoteDraft(lat, lon) },
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
                RoundButton(Icons.Filled.Search, "Поиск", active = state.search.open) {
                    closeOverlays()
                    viewModel.openSearch(!state.search.open)
                }
                Box {
                    RoundButton(Icons.Filled.Straighten, "Инструменты", active = toolsMenuOpen || toolPanelVisible) {
                        layersOpen = false
                        toolsMenuOpen = !toolsMenuOpen
                    }
                    DropdownMenu(expanded = toolsMenuOpen, onDismissRequest = { toolsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Линейка и площадь") },
                            leadingIcon = { Icon(Icons.Filled.Straighten, contentDescription = null) },
                            onClick = { toolsMenuOpen = false; viewModel.setTool(MapTool.RULER) },
                        )
                        DropdownMenuItem(
                            text = { Text("Обмер обходом") },
                            leadingIcon = { Icon(Icons.Filled.DirectionsWalk, contentDescription = null) },
                            onClick = { toolsMenuOpen = false; viewModel.setTool(MapTool.WALK) },
                        )
                        DropdownMenuItem(
                            text = { Text("Мои задачи") },
                            leadingIcon = { Icon(Icons.Filled.Assignment, contentDescription = null) },
                            onClick = { toolsMenuOpen = false; viewModel.openTasks(true) },
                        )
                    }
                }
                RoundButton(Icons.Filled.Layers, "Слои карты", active = layersOpen) {
                    toolsMenuOpen = false
                    layersOpen = !layersOpen
                }
            }

            AnimatedVisibility(
                visible = state.search.open,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                SearchPanel(
                    state = state.search,
                    onQuery = viewModel::onSearchQuery,
                    onPick = viewModel::goTo,
                    onClose = { viewModel.openSearch(false) },
                )
            }

            AnimatedVisibility(
                visible = layersOpen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier.align(Alignment.End),
            ) {
                LayersPanel(
                    layers = state.layers,
                    colorMode = state.colorMode,
                    offlineBaseName = state.offlineBaseName,
                    downloadRunning = state.download.running,
                    onChange = viewModel::setLayers,
                    onColorMode = viewModel::setColorMode,
                    onDownload = viewModel::downloadCurrent,
                    onRefresh = viewModel::refreshOverlays,
                    onImportBase = { offlineBasePicker.launch(arrayOf("*/*")) },
                    onRemoveBase = viewModel::removeOfflineBase,
                )
            }

            if (!state.search.open) {
                if (state.layers.vydela || state.layers.lesoseki) {
                    Box(modifier = Modifier.padding(end = 56.dp)) { LegendRow(legendFor(state)) } // справа — колонка кнопок
                }

                state.navTarget?.let { target ->
                    Box(modifier = Modifier.padding(end = 56.dp)) {
                        NavigationChip(target = target, me = state.myLocation, mapBearing = 0f, onStop = viewModel::stopNavigation)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 56.dp).horizontalScroll(rememberScrollState()),
                ) {
                    state.here?.let { place -> MapChip("Вы здесь: ${place.label}", color = Color(0xFF00E676), onClick = viewModel::openHere) }
                    val taskCount = state.tasks.size
                    if (taskCount > 0 && !state.tasksOpen) {
                        MapChip("Задачи: $taskCount", color = Color(TASK_COLOR), onClick = { viewModel.openTasks(true) })
                    }
                }

                if (state.download.running) StatusChip("Загрузка карты: ${(state.download.fraction * 100).toInt()}%")
                state.download.error?.let { StatusChip(it, isError = true) }
                state.error?.let { StatusChip(it, isError = true) }
                state.geoNotesError?.let { StatusChip(it, isError = true) }
                state.message?.let { StatusChip(it) }
                if (state.usedFallbackRectangles && state.vydela.isNotEmpty()) {
                    StatusChip("Контуры лесосек ещё не получены — показаны границы выдела")
                }
            }
        }

        BottomPanel(visible = cardVisible) {
            ObjectCard(
                state = state,
                point = viewModel.selectedPoint(),
                onNavigate = { label -> viewModel.navigateToSelected(label) },
                onDismiss = viewModel::closeCard,
                modifier = measureBottom,
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

        BottomPanel(visible = state.tool == MapTool.RULER && state.noteDraft == null) {
            RulerPanel(
                points = state.rulerPoints,
                onUndo = viewModel::undoRulerPoint,
                onClear = viewModel::clearRuler,
                onClose = { viewModel.setTool(MapTool.NONE) },
                modifier = measureBottom,
            )
        }

        BottomPanel(visible = state.tool == MapTool.WALK) {
            WalkPanel(
                walk = state.walk,
                accuracy = state.myAccuracy,
                onStart = viewModel::startWalk,
                onPause = viewModel::pauseWalk,
                onUndo = viewModel::undoWalkPoint,
                onFinish = viewModel::finishWalk,
                onClose = { viewModel.setTool(MapTool.NONE) },
                modifier = measureBottom,
            )
        }
        if (state.walk.showSaveDialog) {
            WalkSaveDialog(
                walk = state.walk,
                defaultName = state.here?.label?.let { "Обмер, $it" } ?: "Обмер",
                onSave = viewModel::saveWalk,
                onDismiss = viewModel::dismissWalkDialog,
            )
        }

        BottomPanel(visible = state.tasksOpen) {
            TasksPanel(
                tasks = state.tasks,
                onPick = viewModel::goToTask,
                onClose = { viewModel.openTasks(false) },
                modifier = measureBottom,
            )
        }

        var lastSklad by remember { mutableStateOf(state.selectedSklad) }
        if (state.selectedSklad != null) lastSklad = state.selectedSklad
        BottomPanel(visible = state.selectedSklad != null) {
            (state.selectedSklad ?: lastSklad)?.let { sklad ->
                SkladDetails(
                    sklad = sklad,
                    onNavigate = { viewModel.dismissSklad(); viewModel.navigateTo(LatLon(sklad.lat, sklad.lon), "склад ${sklad.nazvanie}") },
                    onDismiss = viewModel::dismissSklad,
                    modifier = measureBottom,
                )
            }
        }

        var lastNoteDraft by remember { mutableStateOf(state.noteDraft) }
        if (state.noteDraft != null) lastNoteDraft = state.noteDraft
        BottomPanel(visible = state.noteDraft != null) {
            (state.noteDraft ?: lastNoteDraft)?.let { draft ->
                GeoNoteDraftCard(
                    draft = draft,
                    onTextChange = viewModel::updateNoteDraftText,
                    onCategoryChange = viewModel::updateNoteDraftCategory,
                    onPhotoChange = viewModel::updateNoteDraftPhoto,
                    onSubmit = viewModel::submitNoteDraft,
                    onDismiss = viewModel::dismissNoteDraft,
                    modifier = measureBottom,
                )
            }
        }

        var lastGeoNote by remember { mutableStateOf(state.selectedGeoNote) }
        if (state.selectedGeoNote != null) lastGeoNote = state.selectedGeoNote
        BottomPanel(visible = state.selectedGeoNote != null) {
            (state.selectedGeoNote ?: lastGeoNote)?.let { note ->
                GeoNoteDetails(
                    note = note,
                    onNavigate = {
                        viewModel.dismissGeoNotePopup()
                        viewModel.navigateTo(LatLon(note.lat, note.lon), "метка: ${note.category.label}")
                    },
                    onDelete = { viewModel.deleteGeoNote(note) },
                    onDismiss = viewModel::dismissGeoNotePopup,
                    modifier = measureBottom,
                )
            }
        }
    }
}

/** Выезжающая снизу панель — у всех карточек одна анимация. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BottomPanel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) { content() }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
    }
}

/** Форма новой метки — открывается долгим нажатием на карту: тип, текст и/или фото. */
@Composable
private fun GeoNoteDraftCard(
    draft: GeoNoteDraft,
    onTextChange: (String) -> Unit,
    onCategoryChange: (com.lesovod.mobile.ui.map.GeoNoteCategory) -> Unit,
    onPhotoChange: (android.net.Uri?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    "Новая метка",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 40.dp),
                )

                Text(
                    "Тип",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                CategoryPicker(selected = draft.category, enabled = !draft.isSubmitting, onSelect = onCategoryChange)

                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onTextChange,
                    label = { Text("Текст (необязательно)") },
                    enabled = !draft.isSubmitting,
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
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
                        containerColor = MaterialTheme.colorScheme.primary,
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
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
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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


/** Галочки слоёв, способ раскраски, скачивание и своя офлайн-подложка. */
@Composable
private fun LayersPanel(
    layers: MapLayers,
    colorMode: ColorMode,
    offlineBaseName: String?,
    downloadRunning: Boolean,
    onChange: (MapLayers) -> Unit,
    onColorMode: (ColorMode) -> Unit,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onImportBase: () -> Unit,
    onRemoveBase: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 4.dp,
        modifier = Modifier.width(250.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            LayerRow("Кварталы", layers.kvartaly) { onChange(layers.copy(kvartaly = it)) }
            LayerRow("Выделы", layers.vydela) { onChange(layers.copy(vydela = it)) }
            LayerRow("Делянки", layers.lesoseki) { onChange(layers.copy(lesoseki = it)) }
            LayerRow("Лесные культуры", layers.lesokultury) { onChange(layers.copy(lesokultury = it)) }
            LayerRow("Мои задачи", layers.tasks) { onChange(layers.copy(tasks = it)) }
            LayerRow("Мои метки", layers.geoNotes) { onChange(layers.copy(geoNotes = it)) }
            LayerRow("Склады", layers.sklady) { onChange(layers.copy(sklady = it)) }
            LayerRow("Спутниковый снимок", layers.satellite) { onChange(layers.copy(satellite = it)) }
            if (offlineBaseName != null) {
                LayerRow("Своя подложка", layers.offlineBase) { onChange(layers.copy(offlineBase = it)) }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Цвет выделов",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            ColorModeRow("По видам работ", colorMode == ColorMode.WORKS) { onColorMode(ColorMode.WORKS) }
            ColorModeRow("По статусу делянок", colorMode == ColorMode.STATUS) { onColorMode(ColorMode.STATUS) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Обновить цвета и метки", modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onDownload, enabled = !downloadRunning, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (downloadRunning) "Идёт загрузка…" else "Скачать карту", modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onImportBase, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (offlineBaseName == null) "Загрузить подложку (.mbtiles)" else "Заменить подложку", modifier = Modifier.padding(start = 8.dp))
            }
            if (offlineBaseName != null) {
                Text(
                    offlineBaseName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(onClick = onRemoveBase, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Удалить подложку", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ColorModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp)) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
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
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusChip(text: String, isError: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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
private fun ObjectCard(
    state: MapUiState,
    point: LatLon?,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection = state.selection
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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

                    state.selectedCard != null -> VydelCardContent(state.selectedCard, selection?.kind)

                    state.selectedKvartalLabel != null -> {
                        Text(
                            state.selectedKvartalLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
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

                if (!state.isLoadingCard && selection != null) {
                    val label = selection.title()
                    if (point != null) {
                        PointActions(point = point, label = label, onNavigate = { onNavigate(label) }, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (selection.vydel != null) VydelHistorySection(state.history, state.historyLoading)
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
        color = MaterialTheme.colorScheme.primary,
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
            Text(value ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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

/** Цвета — те же, что на сервере (app/map_features.py) и в QGIS. */
private val workLegend = listOf(
    Color(0xFFFF4444) to "Рубка",
    Color(0xFFFFEB3B) to "Уход / осветление",
    Color(0xFF4CAF50) to "Посадка / дополнение",
    Color(0xFF8BC34A) to "Прочие работы",
    Color(DONE_COLOR) to "Выполнено (отметка в приложении)",
)

/** Легенда зависит от способа раскраски; культуры и задачи — только когда они есть на карте. */
private fun legendFor(state: MapUiState): List<Pair<Color, String>> {
    val base = when (state.colorMode) {
        ColorMode.WORKS -> workLegend
        ColorMode.STATUS -> DelyankaStatus.entries.map { Color(it.color) to "Делянка: ${it.label.lowercase()}" }
    }
    val extra = buildList {
        if (state.layers.lesokultury && state.lesokulturyKeys.isNotEmpty()) add(Color(LESOKULTURY_COLOR) to "Лесные культуры")
        if (state.layers.tasks && state.tasks.isNotEmpty()) add(Color(TASK_COLOR) to "Мои задачи")
    }
    return base + extra
}

/** Подсказка после первого тапа: участок выделен на карте, второй тап (или нажатие сюда) откроет таксацию. */
@Composable
private fun SelectionHint(selection: MapSelection, onOpen: () -> Unit, onClear: () -> Unit) {
    val title = selection.title()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
        color = MaterialTheme.colorScheme.primary,
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
