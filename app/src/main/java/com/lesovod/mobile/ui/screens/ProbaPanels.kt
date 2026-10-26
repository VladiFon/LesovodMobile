package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.network.dto.LesokulturyMapDto
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.components.SendStatusChip
import com.lesovod.mobile.ui.map.AuthPhoto
import com.lesovod.mobile.ui.map.GpsAverager
import com.lesovod.mobile.ui.map.KRUG_PRESETS
import com.lesovod.mobile.ui.map.LatLon
import com.lesovod.mobile.ui.map.ProbaDraft
import com.lesovod.mobile.ui.map.ProbaForma
import com.lesovod.mobile.ui.map.ProbaShape
import com.lesovod.mobile.ui.map.bearingDegrees
import com.lesovod.mobile.ui.map.bearingName
import com.lesovod.mobile.ui.map.rememberKompas
import com.lesovod.mobile.ui.map.distanceMeters
import com.lesovod.mobile.ui.map.formatCoordinates
import com.lesovod.mobile.ui.map.formatDistance
import com.lesovod.mobile.ui.map.razmerText
import kotlin.math.roundToInt

// ------------------------------------------------------------ новая проба ---

/**
 * Форма новой пробы (Инструменты → «Поставить пробу»). Встаньте в центр пробы — телефон
 * сам усредняет GPS-точки, граница рисуется на карте сразу. Тап по карте — центр вручную.
 */
@Composable
internal fun ProbaDraftCard(
    draft: ProbaDraft,
    uchastki: List<LesokulturyMapDto>,
    onChange: ((ProbaDraft) -> ProbaDraft) -> Unit,
    onRestartGps: () -> Unit,
    onPickUchastok: (Int?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTakeVeshka: () -> Unit = {},
    onUndoVeshka: () -> Unit = {},
) {
    val enabled = !draft.saving
    FloatingCard(modifier = modifier) {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text("Новая проба", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 40.dp))
                Text(
                    "Тех. приёмка, инвентаризация 1-го и 3-го года — на этой же пробе. Её увидят все.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 32.dp),
                )

                CenterStatus(draft, enabled, onRestartGps)

                if (draft.forma == ProbaForma.KONTUR) {
                    Text(
                        "Граница — по обходу: ${draft.kontur.size} точек" + (draft.areaM2()?.let { ", ${it.roundToInt()} м²" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                        listOf(ProbaForma.KRUG, ProbaForma.PRYAMOUGOLNIK, ProbaForma.VESHKI).forEach { f ->
                            ChoiceChip(f.label, draft.forma == f, enabled) { onChange { it.copy(forma = f) } }
                        }
                    }
                    if (draft.forma == ProbaForma.VESHKI) {
                        VeshkiBlock(draft, enabled, onTakeVeshka, onUndoVeshka)
                    } else if (draft.forma == ProbaForma.KRUG) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        ) {
                            KRUG_PRESETS.forEach { (m2, r) ->
                                ChoiceChip("$m2 м²", draft.number(draft.radius) == r, enabled) { onChange { it.copy(radius = r.toString()) } }
                            }
                        }
                        NumberField("Радиус, м", draft.radius, enabled, Modifier.fillMaxWidth().padding(top = 8.dp)) { v -> onChange { it.copy(radius = v) } }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            NumberField("Длина, м", draft.dlina, enabled, Modifier.weight(1f)) { v -> onChange { it.copy(dlina = v) } }
                            NumberField("Ширина, м", draft.shirina, enabled, Modifier.weight(1f)) { v -> onChange { it.copy(shirina = v) } }
                        }
                        var kompasOpen by remember { mutableStateOf(false) }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            NumberField("Азимут ряда, °", draft.azimut, enabled, Modifier.weight(1f)) { v -> onChange { it.copy(azimut = v) } }
                            OutlinedButton(onClick = { kompasOpen = true }, enabled = enabled) { Text("🧭 По телефону") }
                        }
                        if (kompasOpen) {
                            KompasDialog(
                                place = draft.center,
                                onTake = { az -> kompasOpen = false; onChange { it.copy(azimut = az.roundToInt().toString()) } },
                                onDismiss = { kompasOpen = false },
                            )
                        }
                        Text(
                            "Длина — вдоль ряда. Азимут проще всего взять «🧭 По телефону»; " +
                                "если направление не важно — оставь 0, проба ляжет север–юг. Как легла — видно на карте.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    draft.areaM2()?.let {
                        Text("Площадь пробы: ${it.roundToInt()} м²", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                UchastokPicker(draft, uchastki, enabled, onPickUchastok)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = draft.nomer,
                        onValueChange = { v -> onChange { it.copy(nomer = v.take(20)) } },
                        label = { Text("Номер") },
                        placeholder = { Text("авто") },
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.orientir,
                        onValueChange = { v -> onChange { it.copy(orientir = v.take(500)) } },
                        label = { Text("Ориентир") },
                        placeholder = { Text("колышек, 20 м от просеки") },
                        enabled = enabled,
                        modifier = Modifier.weight(2f),
                    )
                }
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    PhotoPickerField(uri = draft.photoUri, onPicked = { uri -> onChange { it.copy(photoUri = uri) } }, label = "Фото центра / ориентира")
                }

                draft.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                Button(
                    onClick = onSave,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    if (draft.saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else Text("Сохранить пробу")
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CenterStatus(draft: ProbaDraft, enabled: Boolean, onRestartGps: () -> Unit) {
    if (draft.forma == ProbaForma.KONTUR) return
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            val acc = draft.accuracy?.let { "±${it.roundToInt()} м" }
            val n = draft.veshki.size + 1
            val text = if (draft.forma == ProbaForma.VESHKI) when {
                draft.center == null -> "Вешка $n: жду GPS — встань у вешки (или нажми на карту)"
                draft.manualCenter -> "Вешка $n поставлена вручную (тап по карте)"
                draft.averaging -> "Вешка $n: уточняю GPS — ${draft.fixes} точек${acc?.let { ", $it" } ?: ""}, стой у вешки"
                else -> "Вешка $n по GPS готова: ${draft.fixes} точек${acc?.let { ", $it" } ?: ""}"
            } else when {
                draft.center == null -> "Нет точки центра — дождитесь GPS или нажмите на карту"
                draft.manualCenter -> "Центр поставлен вручную (тап по карте)"
                draft.averaging -> "Уточняю центр по GPS: ${draft.fixes} точек${acc?.let { ", $it" } ?: ""} — постойте в центре пробы"
                else -> "Центр по GPS готов: ${draft.fixes} точек${acc?.let { ", $it" } ?: ""}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            }
            if (draft.averaging && !draft.manualCenter) {
                LinearProgressIndicator(
                    progress = { (draft.fixes.toFloat() / GpsAverager.MAX_FIXES).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            draft.center?.let {
                Text(formatCoordinates(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (!draft.averaging || draft.manualCenter) {
                TextButton(onClick = onRestartGps, enabled = enabled) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        when {
                            draft.manualCenter -> if (draft.forma == ProbaForma.VESHKI) "Взять вешку по GPS" else "Взять центр по GPS"
                            else -> "Уточнить заново"
                        },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Проба по вешкам: встал у вешки → телефон копит GPS → «Вешка N здесь» → к следующей.
 * Порядок обхода не важен — углы сами встанут по кругу.
 */
@Composable
private fun VeshkiBlock(draft: ProbaDraft, enabled: Boolean, onTake: () -> Unit, onUndo: () -> Unit) {
    val n = draft.veshki.size
    Text(
        if (n < ProbaDraft.VESHEK) "Обойди вешки по очереди: у каждой постой, пока наберутся точки (чем больше, тем точнее), и жми «Вешка здесь». Порядок не важен."
        else "Все $n вешки есть — можно сохранять. Если вешек больше — отметь и их.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (n > 0) {
        Text(
            draft.veshki.indices.joinToString("  ") { i ->
                "${i + 1}" + (draft.veshkiTochnost.getOrNull(i)?.let { " ±${it.roundToInt()} м" } ?: " (вручную)")
            }.let { "Вешки: $it" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = onTake, enabled = enabled && draft.center != null, modifier = Modifier.weight(1f)) {
            Text("📍 Вешка ${n + 1} здесь")
        }
        if (n > 0) {
            OutlinedButton(onClick = onUndo, enabled = enabled) { Text("↺ Убрать ${n}-ю") }
        }
    }
}

@Composable
private fun UchastokPicker(draft: ProbaDraft, uchastki: List<LesokulturyMapDto>, enabled: Boolean, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = uchastki.firstOrNull { it.id == draft.uchastokId }
    Box(modifier = Modifier.padding(top = 10.dp)) {
        Surface(
            onClick = { if (enabled) open = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Участок лесных культур", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            selected != null -> uchastokLabel(selected) + if (draft.uchastokAuto) " · по месту" else ""
                            draft.uchastokId != null -> "Участок №${draft.uchastokId}"
                            else -> "Сервер найдёт по кварталу/выделу"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            DropdownMenuItem(text = { Text("Без привязки (найдёт сервер)") }, onClick = { open = false; onPick(null) })
            uchastki.forEach { u ->
                DropdownMenuItem(text = { Text(uchastokLabel(u)) }, onClick = { open = false; onPick(u.id) })
            }
        }
    }
}

private fun uchastokLabel(u: LesokulturyMapDto) =
    listOfNotNull("кв. ${u.kvartal}, выд. ${u.vydel}", u.glavnayaPoroda?.takeIf { it.isNotBlank() }, u.godSozdaniya?.takeIf { it.isNotBlank() })
        .joinToString(" · ")

@Composable
private fun ChoiceChip(text: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun NumberField(label: String, value: String, enabled: Boolean, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(8)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * Азимут ряда по компасу телефона: встал в ряд, направил верх телефона вдоль
 * ряда, нажал «Взять». Ряд в обе стороны одинаков, так что куда именно
 * смотреть — вперёд или назад — не важно.
 */
@Composable
private fun KompasDialog(place: LatLon?, onTake: (Double) -> Unit, onDismiss: () -> Unit) {
    val az by rememberKompas(place)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Азимут ряда по телефону") },
        text = {
            Column {
                Text(
                    "Встань в ряд, держи телефон экраном вверх, ровно, верхним краем вдоль ряда. " +
                        "Отойди от машины и железа — компас врёт рядом с металлом.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val a = az
                Text(
                    if (a == null) "Компас не отвечает…" else "${a.roundToInt()}° · ${bearingName(a)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (a == null) {
                    Text(
                        "Если цифра так и не появилась — в телефоне нет компаса, впиши азимут руками или оставь 0.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        "Если цифра врёт — покрути телефон восьмёркой пару раз, компас откалибруется.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { az?.let(onTake) }, enabled = az != null) { Text("Взять") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ------------------------------------------------------------ карточка пробы ---

/** Тап по пробе на карте: где она, какие учёты на ней были, как до неё дойти. */
@Composable
internal fun ProbaDetails(
    proba: ProbaShape,
    me: LatLon?,
    onNavigate: () -> Unit,
    onMoveHere: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember(proba.id) { mutableStateOf(false) }
    var confirmMove by remember(proba.id) { mutableStateOf(false) }
    val dto = proba.dto
    FloatingCard(modifier = modifier) {
        Box {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    "Проба" + (proba.nomer.takeIf { it.isNotBlank() }?.let { " П$it" } ?: ""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 40.dp),
                )
                val u = dto?.uchastok
                Text(
                    when {
                        u != null -> listOfNotNull("кв. ${u.kvartal.orEmpty()}, выд. ${u.vydel.orEmpty()}", u.glavnayaPoroda, u.godSozdaniya).joinToString(" · ")
                        dto?.kvartal != null -> "кв. ${dto.kvartal}, выд. ${dto.vydel.orEmpty()} · без участка культур"
                        else -> "без участка культур"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SendStatusChip(pending = proba.pending, modifier = Modifier.padding(top = 6.dp))
                dto?.let {
                    Text(
                        it.razmerText() + (it.tochnostM?.let { t -> " · GPS ±${t.roundToInt()} м" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                me?.let {
                    val d = distanceMeters(it, proba.center)
                    Text(
                        if (d < 5) "Вы в центре пробы" else "До центра: ${formatDistance(d)}, на ${bearingName(bearingDegrees(it, proba.center))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (!dto?.etapy.isNullOrEmpty()) {
                    Text("Учёты", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    dto!!.etapy.forEach { e ->
                        val mark = when {
                            !e.vypolneno -> "⏳"
                            e.naProbe -> "✅"
                            else -> "☑️"
                        }
                        Text(
                            "$mark ${e.tip}" + (e.data?.let { " — $it" } ?: "") + (e.prizhivaemostPct?.let { ", ${it.roundToInt()} %" } ?: "") +
                                if (e.vypolneno && !e.naProbe) " (без отметки пробы)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (e.vypolneno) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                dto?.orientir?.takeIf { it.isNotBlank() }?.let {
                    Text("🧭 $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                dto?.photoUrl?.let { AuthPhoto(it, modifier = Modifier.padding(top = 10.dp)) }
                Text(
                    listOfNotNull(dto?.avtorFio, dto?.createdAt?.take(10)?.split("-")?.reversed()?.joinToString("."), formatCoordinates(proba.center))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                PointActions(
                    point = proba.center,
                    label = "Проба П${proba.nomer}" + (u?.let { " (кв. ${it.kvartal.orEmpty()}, выд. ${it.vydel.orEmpty()})" } ?: ""),
                    onNavigate = onNavigate,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (dto?.mozhnoPravit == true && !proba.pending) {
                    OutlinedButton(onClick = { confirmMove = true }, enabled = me != null, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Центр пробы — где я стою", modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text("Убрать с карты", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Убрать пробу П${proba.nomer}?") },
            text = { Text("Проба пропадёт с карты у всех и в QGIS. В базе она останется — её можно вернуть через офис.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Убрать", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            title = { Text("Перенести центр?") },
            text = { Text("Центр пробы П${proba.nomer} встанет туда, где вы сейчас (размеры те же). Делайте это, стоя у колышка в центре пробы.") },
            confirmButton = { TextButton(onClick = { confirmMove = false; onMoveHere() }) { Text("Перенести") } },
            dismissButton = { TextButton(onClick = { confirmMove = false }) { Text("Отмена") } },
        )
    }
}
