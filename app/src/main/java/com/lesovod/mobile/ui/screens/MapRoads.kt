package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.ui.components.LvBigButton
import com.lesovod.mobile.ui.components.LvSoftButton
import com.lesovod.mobile.ui.map.LatLon
import com.lesovod.mobile.ui.map.NavTarget
import com.lesovod.mobile.ui.map.Road
import com.lesovod.mobile.ui.map.RoadDraft
import com.lesovod.mobile.ui.map.RoadRoute
import com.lesovod.mobile.ui.map.RoadTip
import com.lesovod.mobile.ui.map.Vehicle
import com.lesovod.mobile.ui.map.bearingDegrees
import com.lesovod.mobile.ui.map.bearingName
import com.lesovod.mobile.ui.map.distanceMeters
import com.lesovod.mobile.ui.map.formatDistance
import com.lesovod.mobile.ui.map.formatMinutes
import com.lesovod.mobile.ui.map.guidance
import com.lesovod.mobile.ui.map.say
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import kotlin.math.roundToInt

private val NAV_PINK = Color(0xFFFF4081)

// ------------------------------------------------------------- маршрут ---

/**
 * Навигатор по лесным дорогам (этап 5): куда, на чём, сколько ехать, где повернуть.
 * Дорог рядом нет — честно говорит «веду по прямой» и показывает азимут.
 */
@Composable
internal fun RoutePanel(
    target: NavTarget,
    me: LatLon?,
    route: RoadRoute?,
    loading: Boolean,
    vehicle: Vehicle,
    driving: Boolean,
    sending: Boolean,
    hasRoads: Boolean,
    onVehicle: (Vehicle) -> Unit,
    onDrive: (Boolean) -> Unit,
    onSend: () -> Unit,
    onAzimuth: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    val g = if (me != null && route != null) route.guidance(me) else null
    val headline: String
    var arrow = 0f
    when {
        me == null -> headline = "Жду GPS…"
        g != null && route!!.byRoad -> {
            headline = g.say()
            arrow = when {
                g.toTurnMeters == null || g.turn == 0 -> 0f
                g.sharp -> g.turn * 135f
                else -> g.turn * 90f
            }
        }
        else -> {
            val d = distanceMeters(me, target.point)
            val b = bearingDegrees(me, target.point)
            headline = if (d < 25) "Вы на месте" else "${formatDistance(d)} на ${bearingName(b)} (${b.roundToInt()}°)"
            arrow = b.toFloat()
        }
    }
    val sub = buildString {
        append("к «${target.label}»")
        when {
            loading && route == null -> append(" · строю маршрут…")
            route != null && route.byRoad -> {
                append(" · ${formatDistance(g?.remainingMeters ?: route.totalMeters)}")
                append(" · ${formatMinutes(g?.remainingMinutes ?: route.minutes)}")
                if (route.walkMeters >= 50) append(" · пешком ${formatDistance(route.walkMeters)}")
            }
            route != null && hasRoads -> append(" · по дорогам не короче, веду по прямой")
            route != null -> append(" · дорог пока нет, веду по прямой")
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(lv.ov)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(if (driving) 52.dp else 40.dp).clip(CircleShape).background(NAV_PINK),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Navigation, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(if (driving) 34.dp else 26.dp).rotate(arrow),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    headline, color = lv.tx,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (driving) 22.sp else 17.sp),
                )
                Text(sub, color = lv.tx2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
            }
            IconButton(onClick = onStop) { Icon(Icons.Rounded.Close, contentDescription = "Остановить", tint = lv.tx2) }
        }
        if (!driving) {
            Row(
                Modifier.padding(top = 8.dp, end = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Vehicle.tabs.forEach { v -> PillChip(v.label, selected = v == vehicle, onClick = { onVehicle(v) }) }
            }
        }
        Row(
            Modifier.padding(top = 8.dp, end = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (driving) {
                PillChip("Стоп", selected = false, icon = Icons.Rounded.Stop, onClick = { onDrive(false) })
            } else {
                PillChip("Поехали", selected = true, icon = Icons.Rounded.PlayArrow, onClick = { onDrive(true) })
            }
            PillChip(if (sending) "Отправляю…" else "Отправить", selected = false, icon = Icons.AutoMirrored.Rounded.Send, onClick = { if (!sending) onSend() })
            PillChip("Азимут", selected = false, onClick = onAzimuth)
        }
    }
}

@Composable
private fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    color: Color? = null,
) {
    val lv = LocalLv.current
    val bg = when {
        color != null && selected -> color
        selected -> lv.g
        else -> lv.sf2
    }
    val fg = if (selected) Color.White else lv.tx
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp))
    }
}

// ------------------------------------------------------ рисуем дорогу ---

@Composable
internal fun RoadDraftPanel(
    draft: RoadDraft,
    hasGps: Boolean,
    onMyPoint: () -> Unit,
    onUndo: () -> Unit,
    onRecord: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    FloatingCard(modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Новая дорога", color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp))
                    Text(
                        when {
                            draft.recording -> "Пишу проездом: точка каждые 15 м · ${draft.points.size} точ. · ${formatDistance(draft.lengthM)}"
                            draft.points.isEmpty() -> "Нажимайте на карту по ходу дороги или включите запись и проезжайте её"
                            draft.points.size == 1 -> "Ещё точку — и появится линия"
                            else -> "${draft.points.size} точ. · ${formatDistance(draft.lengthM)}"
                        },
                        color = lv.tx2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = lv.tx2) }
            }
            draft.error?.let { Text(it, color = lv.r, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            Row(
                Modifier.padding(top = 10.dp, end = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillChip(
                    if (draft.recording) "Остановить запись" else "Записать проездом",
                    selected = draft.recording, color = lv.r,
                    icon = if (draft.recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                    onClick = onRecord,
                )
                if (hasGps) PillChip("Моя точка", selected = false, icon = Icons.Rounded.MyLocation, onClick = onMyPoint)
                if (draft.points.isNotEmpty()) PillChip("Убрать точку", selected = false, icon = Icons.AutoMirrored.Rounded.Undo, onClick = onUndo)
            }
            LvBigButton(
                text = "Дальше: тип и кто проедет",
                onClick = onNext,
                enabled = draft.points.size >= 2,
                icon = Icons.Rounded.Done,
                height = 52,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp),
            )
        }
    }
}

@Composable
internal fun RoadDetailsDialog(
    draft: RoadDraft,
    onName: (String) -> Unit,
    onComment: (String) -> Unit,
    onTip: (RoadTip) -> Unit,
    onVehicle: (Vehicle) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val lv = LocalLv.current
    AlertDialog(
        onDismissRequest = { if (!draft.saving) onBack() },
        title = { Text("Дорога · ${formatDistance(draft.lengthM)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.name, onValueChange = { onName(it.take(120)) },
                    label = { Text("Название (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Какая дорога", color = lv.tx2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoadTip.entries.forEach { t -> PillChip(t.label, selected = t == draft.tip, color = Color(t.color), onClick = { onTip(t) }) }
                }
                Text("Кто проедет", color = lv.tx2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Vehicle.entries.forEach { v -> PillChip(v.label, selected = v in draft.proezd, onClick = { onVehicle(v) }) }
                }
                OutlinedTextField(
                    value = draft.comment, onValueChange = { onComment(it.take(500)) },
                    label = { Text("Заметка: мост, яма, весной топко…") }, modifier = Modifier.fillMaxWidth(),
                )
                draft.error?.let { Text(it, color = lv.r, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !draft.saving) {
                if (draft.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onBack, enabled = !draft.saving) { Text("Назад к карте") } },
    )
}

// ------------------------------------------------------ карточка дороги ---

@Composable
internal fun RoadCard(
    road: Road,
    canDelete: Boolean,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    var confirm by remember { mutableStateOf(false) }
    FloatingCard(modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(road.tip.color)).border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(road.title, color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp))
                    Text(
                        "${road.tip.label} · ${formatDistance(road.lengthM)}",
                        color = lv.tx2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = lv.tx2) }
            }
            Text(
                "Проедет: " + road.proezd.sortedBy { it.ordinal }.joinToString(", ") { it.label }.ifEmpty { "никто не отмечен" },
                color = lv.tx, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
            )
            road.comment?.takeIf { it.isNotBlank() }?.let { Text(it, color = lv.tx, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
            road.author?.let { Text("Нарисовал: $it" + (road.createdAt?.take(10)?.let { d -> ", $d" } ?: ""), color = lv.tx2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Row(Modifier.padding(top = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LvBigButton("Вести к дороге", onClick = onNavigate, icon = Icons.Rounded.Navigation, height = 52, modifier = Modifier.weight(1f))
                if (canDelete) {
                    LvSoftButton("Убрать", onClick = { confirm = true }, icon = Icons.Rounded.Delete, height = 52)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Убрать дорогу с карты?") },
            text = { Text("Она пропадёт у всех, маршруты по ней строиться перестанут. В базе останется — контора сможет вернуть.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Убрать") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Оставить") } },
        )
    }
}
