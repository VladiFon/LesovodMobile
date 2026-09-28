package com.lesovod.mobile.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.network.dto.SkladDto
import com.lesovod.mobile.data.network.dto.VydelHistoryDto
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.ui.map.AuthPhoto
import com.lesovod.mobile.ui.map.GeoNoteCategory
import com.lesovod.mobile.ui.map.GeoNoteMarker
import com.lesovod.mobile.ui.map.LatLon
import com.lesovod.mobile.ui.map.NavTarget
import com.lesovod.mobile.ui.map.SearchItem
import com.lesovod.mobile.ui.map.SearchState
import com.lesovod.mobile.ui.map.WalkState
import com.lesovod.mobile.ui.map.bearingDegrees
import com.lesovod.mobile.ui.map.bearingName
import com.lesovod.mobile.ui.map.distanceMeters
import com.lesovod.mobile.ui.map.formatArea
import com.lesovod.mobile.ui.map.formatCoordinates
import com.lesovod.mobile.ui.map.formatDistance
import com.lesovod.mobile.ui.map.pathLengthMeters
import com.lesovod.mobile.ui.map.polygonAreaSquareMeters
import kotlin.math.roundToInt

// ---------------------------------------------------------------- общее ---

/** Отправить координаты в мессенджер/SMS — ссылка открывается картой у получателя. */
internal fun shareLocation(context: Context, point: LatLon, label: String) {
    val coords = formatCoordinates(point)
    val text = "$label\n$coords\nhttps://maps.google.com/?q=${point.lat},${point.lon}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться точкой"))
}

/** Открыть точку в навигаторе/картах телефона (Яндекс, Google, OsmAnd — что установлено). */
internal fun openInNavigator(context: Context, point: LatLon, label: String) {
    val uri = Uri.parse("geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        shareLocation(context, point, label)
    }
}

@Composable
internal fun FloatingCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) { content() }
}

/** Маленькая плашка поверх карты; onClick — необязательно. */
@Composable
internal fun MapChip(text: String, color: Color? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = if (trailing != null) 2.dp else 12.dp)) {
            if (color != null) Box(modifier = Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(color))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            trailing?.invoke()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) { content() }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) { content() }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(4.dp).size(40.dp)) {
        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Строка кнопок "Вести сюда / В навигатор / Поделиться" для любой точки. */
@Composable
internal fun PointActions(point: LatLon, label: String, onNavigate: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onNavigate != null) {
            OutlinedButton(onClick = onNavigate) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Вести сюда", modifier = Modifier.padding(start = 6.dp))
            }
        }
        OutlinedButton(onClick = { openInNavigator(context, point, label) }) {
            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("В навигатор", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { shareLocation(context, point, label) }) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Поделиться", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

// ---------------------------------------------------------------- поиск ---

@Composable
internal fun SearchPanel(
    state: SearchState,
    onQuery: (String) -> Unit,
    onPick: (SearchItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    FloatingCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("12/5, кв 12 выд 5, делянка, сосна 2021") },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                CloseButton(onClose)
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Ищу…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
                if (!state.loading && state.error == null && state.query.isNotBlank() && state.results.isEmpty()) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                }
                if (state.query.isBlank()) {
                    Text(
                        "Квартал, выдел («12/5»), название делянки, порода или год лесных культур",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                state.results.forEach { item ->
                    Surface(onClick = { onPick(item) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(searchKindColor(item.kind)))
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    listOfNotNull(searchKindLabel(item.kind), item.subtitle).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}

private fun searchKindLabel(kind: String) = when (kind) {
    "kvartal" -> "Квартал"
    "vydel" -> "Выдел"
    "delyanka" -> "Делянка"
    "lesokultury" -> "Лесные культуры"
    else -> null
}

private fun searchKindColor(kind: String) = when (kind) {
    "kvartal" -> Color(0xFFFFFFFF)
    "vydel" -> Color(0xFF8BC34A)
    "delyanka" -> Color(0xFFFF9800)
    "lesokultury" -> Color(0xFF00E5FF)
    else -> Color.Gray
}

// ------------------------------------------------------ веди до делянки ---

@Composable
internal fun NavigationChip(target: NavTarget, me: LatLon?, mapBearing: Float, onStop: () -> Unit) {
    val text: String
    var arrow = 0f
    if (me == null) {
        text = "К «${target.label}»: жду GPS…"
    } else {
        val d = distanceMeters(me, target.point)
        val b = bearingDegrees(me, target.point)
        arrow = (b + mapBearing).toFloat()
        text = if (d < 25) "Вы на месте: ${target.label}" else "${formatDistance(d)} на ${bearingName(b)} (${b.roundToInt()}°) · ${target.label}"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFF4081).copy(alpha = 0.92f),
        contentColor = Color.White,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
            if (me != null) Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(22.dp).rotate(arrow))
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false))
            IconButton(onClick = onStop) { Icon(Icons.Filled.Close, contentDescription = "Остановить", tint = Color.White) }
        }
    }
}

// ---------------------------------------------------------- инструменты ---

@Composable
internal fun RulerPanel(points: List<LatLon>, onUndo: () -> Unit, onClear: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    FloatingCard(modifier = modifier) {
        Box {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {
                Text("Линейка", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (points.size < 2) {
                    Text("Нажимайте на карту — точки соединятся линией", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text("Длина: ${formatDistance(pathLengthMeters(points))}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    if (points.size >= 3) {
                        Text(
                            "Площадь: ${formatArea(polygonAreaSquareMeters(points))} · периметр ${formatDistance(pathLengthMeters(points, closed = true))}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    TextButton(onClick = onUndo, enabled = points.isNotEmpty()) { Text("Убрать точку") }
                    TextButton(onClick = onClear, enabled = points.isNotEmpty()) { Text("Очистить") }
                }
            }
            CloseButton(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
internal fun WalkPanel(
    walk: WalkState,
    accuracy: Float?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingCard(modifier = modifier) {
        Box {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)) {
                Text("Обмер обходом", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (walk.recording) "Идите по границе участка — точки ставятся сами. Экран не гаснет."
                    else "Встаньте на угол участка и нажмите «Начать». Держите карту открытой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 32.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    WalkStat("Площадь", if (walk.points.size >= 3) formatArea(walk.areaSquareMeters) else "—", Modifier.weight(1f))
                    WalkStat("Периметр", if (walk.points.size >= 2) formatDistance(walk.perimeterMeters) else "—", Modifier.weight(1f))
                    WalkStat("Точность", accuracy?.let { "±${it.roundToInt()} м" } ?: "нет GPS", Modifier.weight(1f))
                }
                walk.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    if (walk.recording) {
                        OutlinedButton(onClick = onPause) { Text("Пауза") }
                    } else {
                        Button(onClick = onStart) { Text(if (walk.points.isEmpty()) "Начать" else "Продолжить") }
                    }
                    TextButton(onClick = onUndo, enabled = walk.points.isNotEmpty()) { Text("Убрать точку") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onFinish,
                        enabled = walk.points.size >= 3 && !walk.saving,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Готово") }
                }
            }
            if (!walk.recording) CloseButton(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun WalkStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun WalkSaveDialog(walk: WalkState, defaultName: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!walk.saving) onDismiss() },
        title = { Text("Сохранить контур") },
        text = {
            Column {
                Text("Площадь ${formatArea(walk.areaSquareMeters)}, периметр ${formatDistance(walk.perimeterMeters)}, точек: ${walk.points.size}")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Примечание") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                walk.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, note) }, enabled = !walk.saving) {
                if (walk.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !walk.saving) { Text("Отмена") } },
    )
}

// ------------------------------------------------------------ метки ---

@Composable
internal fun CategoryPicker(selected: GeoNoteCategory, enabled: Boolean, onSelect: (GeoNoteCategory) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GeoNoteCategory.entries.forEach { category ->
            val active = category == selected
            Surface(
                onClick = { if (enabled) onSelect(category) },
                shape = RoundedCornerShape(16.dp),
                color = if (active) Color(category.color) else MaterialTheme.colorScheme.surface,
                contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, Color(category.color)),
            ) {
                Text(category.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
internal fun GeoNoteDetails(
    note: GeoNoteMarker,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember(note.id) { mutableStateOf(false) }
    FloatingCard(modifier = modifier) {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 40.dp)) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(note.category.color)))
                    Text(note.category.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    listOfNotNull(note.authorFio, note.createdAt).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (!note.noteText.isNullOrBlank()) {
                    Text(note.noteText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
                note.photoUrl?.let { AuthPhoto(it, modifier = Modifier.padding(top = 10.dp)) }
                Text(
                    formatCoordinates(LatLon(note.lat, note.lon)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                PointActions(
                    point = LatLon(note.lat, note.lon),
                    label = "Метка: ${note.category.label}" + (note.noteText?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                    onNavigate = onNavigate,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (note.id > 0) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text("Удалить метку", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            CloseButton(onDismiss, Modifier.align(Alignment.TopEnd))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить метку?") },
            text = { Text("Метка пропадёт с карты у вас и в QGIS.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

// ------------------------------------------------------------ склады ---

@Composable
internal fun SkladDetails(sklad: SkladDto, onNavigate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    FloatingCard(modifier = modifier) {
        Box {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
                Text("Склад · ${sklad.nazvanie}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 40.dp))
                sklad.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                PointActions(LatLon(sklad.lat, sklad.lon), "Склад: ${sklad.nazvanie}", onNavigate, Modifier.padding(top = 10.dp))
            }
            CloseButton(onDismiss, Modifier.align(Alignment.TopEnd))
        }
    }
}

// ------------------------------------------------------- мои задачи ---

@Composable
internal fun TasksPanel(tasks: List<WorkPlanItemDto>, onPick: (WorkPlanItemDto) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    FloatingCard(modifier = modifier) {
        Box {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {
                Text("Мои задачи", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Выделы с задачами обведены фиолетовым пунктиром",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    if (tasks.isEmpty()) Text("Активных задач нет", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                    tasks.forEach { task ->
                        val place = task.kvartal?.let { "кв. $it" + (task.vydel?.let { v -> ", выд. $v" } ?: "") }
                        Surface(
                            onClick = { onPick(task) },
                            enabled = task.kvartal != null,
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(task.zadacha, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull(task.data, place ?: "без места на карте").joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
            CloseButton(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

// -------------------------------------------------- история выдела ---

@Composable
internal fun VydelHistorySection(history: VydelHistoryDto?, loading: Boolean) {
    var photoFor by remember(history) { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.padding(top = 10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Text("История выдела", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Загружаю…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
            history == null -> Text("Нет связи — история недоступна", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            history.works.isEmpty() && history.lesokultury.isEmpty() && history.delyanki.isEmpty() ->
                Text("Работ на выделе ещё не было", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            else -> {
                history.delyanki.forEach { d ->
                    Text(
                        "Делянка «${d.nazvanie ?: d.delyankaId}» — ${d.statusRabot ?: "ожидает"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                history.lesokultury.forEach { u ->
                    Text(
                        "Лесные культуры: ${listOfNotNull(u.glavnayaPoroda, u.godSozdaniya, u.ploshad?.let { "$it га" }).joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF00897B),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                history.meropriyatiya.take(5).forEach { m ->
                    Text(
                        "• ${listOfNotNull(m.data, m.tip).joinToString(" — ")}" + (m.prizhivaemostPct?.let { ", приживаемость ${it.roundToInt()}%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                history.works.take(15).forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(w.tipRaboty ?: "Работа", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(w.dataVypolneniya, w.ispolnitelFio?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (w.hasPhoto) TextButton(onClick = { photoFor = if (photoFor == w.id) null else w.id }) { Text("Фото") }
                    }
                    if (photoFor == w.id) AuthPhoto("/api/map/completed-work/${w.id}/photo", modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------- легенда ---

@Composable
internal fun LegendRow(items: List<Pair<Color, String>>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
