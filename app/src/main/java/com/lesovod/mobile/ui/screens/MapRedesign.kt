package com.lesovod.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.data.location.Place
import com.lesovod.mobile.ui.components.HelpSheet
import com.lesovod.mobile.ui.map.MapLayers
import com.lesovod.mobile.ui.map.NearbyItem
import com.lesovod.mobile.ui.map.NearbyKind
import com.lesovod.mobile.ui.map.formatDistance
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import com.lesovod.mobile.ui.theme.Unbounded

/*
 * Детали карты из редизайна 12.11.2026 (прототип «Карта»): строка «Где я», чипы слоёв,
 * кнопка «+» с меню, список «Что рядом». Карта, слои и инструменты — прежние.
 */

internal const val MAP_HELP = "Карта леса. Сверху «Где я»: в каком вы квартале и выделе, нажмите, и откроется таксация. " +
    "Чипы под ней включают и выключают слои. Нажмите на выдел один раз, чтобы выделить, второй раз, чтобы открыть карточку. " +
    "Зажмите палец на карте, чтобы поставить метку. Кнопка «+» внизу: метка, фото, отчёт отсюда и «Что рядом»."

/** Плашка «Где я»: квартал и выдел по GPS крупно, лесничество мелко. */
@Composable
internal fun WhereAmIPill(
    place: Place?,
    hasGps: Boolean,
    lesnichestvo: String?,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(lv.ov)
            .clickable(enabled = place != null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (place != null) Icons.Rounded.LocationOn else Icons.Rounded.LocationSearching,
            contentDescription = null,
            tint = lv.gT,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                place?.label ?: if (hasGps) "Вне кварталов" else "Где я?",
                color = lv.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp),
            )
            Text(
                when {
                    place != null -> place.lesnichestvo ?: lesnichestvo ?: "нажмите — таксация"
                    hasGps -> lesnichestvo ?: "рядом нет выделов"
                    else -> "ищу GPS…"
                },
                color = lv.tx2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
            )
        }
        if (loading) {
            CircularProgressIndicator(color = lv.gT, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
    }
}

/** Круглая «стеклянная» кнопка 56 на карте (поиск, «?»). */
@Composable
internal fun MapGlassButton(
    icon: ImageVector?,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
    text: String? = null,
) {
    val lv = LocalLv.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) lv.g else lv.ov)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Box(Modifier.size(30.dp).border(2.5.dp, lv.tx, CircleShape), contentAlignment = Alignment.Center) {
                Text(text, color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
            }
        } else if (icon != null) {
            Icon(icon, contentDescription = description, tint = if (active) Color.White else lv.tx, modifier = Modifier.size(28.dp))
        }
    }
}

/** «?» на карте с той же нижней карточкой, что и на других экранах. */
@Composable
internal fun MapHelpButton() {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    MapGlassButton(icon = null, description = "Что тут делают", text = "?", onClick = { open = true })
    if (open) HelpSheet(text = MAP_HELP, onDismiss = { open = false })
}

private class LayerChipSpec(val label: String, val on: Boolean, val change: (Boolean) -> MapLayers)

/** Чипы слоёв под «Где я»: зелёный с галочкой — включён. */
@Composable
internal fun LayerChipsRow(layers: MapLayers, onChange: (MapLayers) -> Unit, modifier: Modifier = Modifier) {
    val chips = listOf(
        LayerChipSpec("Делянки", layers.lesoseki) { on -> layers.copy(lesoseki = on) },
        LayerChipSpec("Выдела", layers.vydela) { on -> layers.copy(vydela = on) },
        LayerChipSpec("Культуры", layers.lesokultury) { on -> layers.copy(lesokultury = on) },
        LayerChipSpec("Метки", layers.geoNotes) { on -> layers.copy(geoNotes = on) },
        LayerChipSpec("Пробы", layers.proby) { on -> layers.copy(proby = on) },
        LayerChipSpec("Задачи", layers.tasks) { on -> layers.copy(tasks = on) },
        LayerChipSpec("Спутник", layers.satellite) { on -> layers.copy(satellite = on) },
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { c -> LayerChip(c.label, c.on) { onChange(c.change(!c.on)) } }
    }
}

@Composable
private fun LayerChip(label: String, on: Boolean, onClick: () -> Unit) {
    val lv = LocalLv.current
    Row(
        modifier = Modifier
            .height(44.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) lv.g else lv.ov)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (on) Color.White else lv.tx,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
        )
    }
}

/** Пункт меню «+». */
internal class PlusAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Кнопка «+» справа внизу и раскрывающееся над ней меню (как в прототипе «Меню (+)»). */
@Composable
internal fun MapPlusButton(
    open: Boolean,
    onToggle: () -> Unit,
    actions: List<PlusAction>,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    Column(modifier = modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { a ->
                    Row(
                        modifier = Modifier
                            .height(60.dp)
                            .shadow(6.dp, RoundedCornerShape(30.dp))
                            .clip(RoundedCornerShape(30.dp))
                            .background(lv.sf)
                            .clickable { onToggle(); a.onClick() }
                            .padding(start = 8.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(lv.gS), contentAlignment = Alignment.Center) {
                            Icon(a.icon, contentDescription = null, tint = lv.gT, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(if (open) lv.sf else lv.g)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (open) Icons.Rounded.Close else Icons.Rounded.Add,
                contentDescription = if (open) "Закрыть меню" else "Добавить",
                tint = if (open) lv.tx else Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

internal object PlusIcons {
    val Metka = Icons.Rounded.AddLocationAlt
    val Photo = Icons.Rounded.PhotoCamera
    val Report = Icons.Rounded.Description
    val Nearby = Icons.Rounded.Radar
}

/** Нижняя шторка «Что рядом»: значок, название, пояснение, стрелка «куда идти» и расстояние. */
@Composable
internal fun NearbyPanel(
    items: List<NearbyItem>,
    hasGps: Boolean,
    onPick: (NearbyItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(lv.sf)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(lv.sf3))
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Что рядом",
                color = lv.tx,
                style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(lv.sf2).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = lv.tx) }
        }
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
            if (items.isEmpty()) {
                Text(
                    if (!hasGps) "Пока нет GPS. Выйдите на открытое место, и тут появится всё, что рядом."
                    else "Рядом ничего нет: ни делянок, ни меток, ни проб. Отдалите карту, чтобы подгрузились соседние кварталы.",
                    color = lv.tx2,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            items.forEach { item -> NearbyRow(item, onClick = { onPick(item) }) }
        }
    }
}

@Composable
private fun NearbyRow(item: NearbyItem, onClick: () -> Unit) {
    val lv = LocalLv.current
    val (icon, bg) = when (item.kind) {
        NearbyKind.DELYANKA -> Icons.Rounded.CropSquare to lv.gS
        NearbyKind.KULTURY -> Icons.Rounded.Park to lv.gS
        NearbyKind.PROBA -> Icons.Rounded.Science to lv.aS
        NearbyKind.METKA -> Icons.Rounded.Place to (item.color?.let { Color(it) } ?: lv.aS)
    }
    val tint = if (item.kind == NearbyKind.METKA && item.color != null) Color.White else if (item.kind == NearbyKind.PROBA) lv.aT else lv.gT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = lv.tx,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp),
            )
            item.subtitle?.let {
                Text(it, color = lv.tx2, maxLines = 2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp)) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = null,
                tint = lv.gT,
                modifier = Modifier.size(26.dp).rotate(item.bearing.toFloat()),
            )
            Text(
                if (item.inside) "0 м" else formatDistance(item.distance),
                color = lv.tx,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
            )
        }
    }
}
