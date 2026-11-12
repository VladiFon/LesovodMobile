package com.lesovod.mobile.ui.lesokultury

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.location.CurrentPlace
import com.lesovod.mobile.ui.components.LvBigButton
import com.lesovod.mobile.ui.components.LvSectionLabel
import com.lesovod.mobile.ui.components.LvSoftButton
import com.lesovod.mobile.ui.components.LvTopBar
import com.lesovod.mobile.ui.components.UchastokSelector
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import com.lesovod.mobile.ui.theme.Unbounded
import kotlin.math.roundToInt

private val STEP_TITLES = listOf("выбор участка", "пробы", "считаем саженцы", "проверка")

private val STEP_HELP = listOf(
    "Шаг 1. Выберите участок культур. Если вы стоите на нём, он будет первым в «Рядом с вами».",
    "Шаг 2. Год обследования и пробы. Пробы, поставленные на карте, подставятся сами, останется проверить номера и размеры.",
    "Шаг 3. Идёте по пробе и жмёте: зелёная кнопка — живое растение, красная — погибшее. Ошиблись — «Отменить». Порода переключается сверху.",
    "Шаг 4. Проверьте цифры. Если всё верно, «Отправить». Без сети карточка сохранится на телефоне и уйдёт сама.",
)

/**
 * Инвентаризация лесных культур по шагам (редизайн 12.11.2026, прототипы «Выбор участка», «Кликер»,
 * «Проверка»): 1 участок → 2 пробы → 3 кликер «Живое / Погибло» → 4 проверка и отправка.
 * Вся логика расчёта и отправки прежняя (InventarizatsiyaViewModel), поменялся только путь.
 */
@Composable
fun InventarizatsiyaScreen(onBack: () -> Unit, viewModel: InventarizatsiyaViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val lv = LocalLv.current
    var step by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(state.selectedUchastok == null) { if (state.selectedUchastok == null) step = 0 }
    val done = state.result != null || state.queuedOffline

    val goBack: () -> Unit = { if (step > 0 && !done) step -= 1 else onBack() }
    BackHandler(enabled = step > 0 && !done) { step -= 1 }

    Column(Modifier.fillMaxSize().background(lv.bg)) {
        LvTopBar(
            title = if (step == 3) "Проверка" else "Инвентаризация",
            onBack = goBack,
            help = STEP_HELP[step],
        )
        if (!done) StepBar(step)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.result != null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    ServerResultCard(result = state.result!!, title = "Инвентаризация сохранена", onNewCard = { viewModel.newCard(); step = 0 })
                }
                state.queuedOffline -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    QueuedOfflineCard(onNewCard = { viewModel.newCard(); step = 0 })
                }
                state.isLoadingReference -> CircularProgressIndicator(color = lv.gT, modifier = Modifier.align(Alignment.Center))
                step == 0 -> StepUchastok(state, viewModel)
                step == 1 -> StepProby(state, viewModel)
                step == 2 -> StepClicker(state, viewModel)
                else -> StepCheck(state, viewModel)
            }
        }

        if (!done && !state.isLoadingReference) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> LvBigButton(
                        text = "Дальше: пробы",
                        onClick = { step = 1 },
                        enabled = state.selectedUchastok != null,
                    )
                    1 -> LvBigButton(text = "Дальше: считать", onClick = { step = 2 })
                    2 -> {
                        LvSoftButton(
                            text = "Отменить",
                            icon = Icons.AutoMirrored.Rounded.Undo,
                            onClick = viewModel::undoLastClick,
                            enabled = state.clicks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        LvBigButton(text = "Закончить", onClick = { step = 3 }, modifier = Modifier.weight(1f))
                    }
                    else -> LvBigButton(
                        text = if (state.isSubmitting) "Отправляю…" else "Отправить",
                        onClick = viewModel::submit,
                        enabled = !state.isSubmitting,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBar(step: Int) {
    val lv = LocalLv.current
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= step) lv.g else lv.sf3),
                )
            }
        }
        Text(
            "Шаг ${step + 1} из 4 · ${STEP_TITLES[step]}",
            color = lv.tx2,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ------------------------------------------------------- шаг 1: участок ---

@Composable
private fun StepUchastok(state: InventarizatsiyaUiState, viewModel: InventarizatsiyaViewModel) {
    val lv = LocalLv.current
    val place = CurrentPlace.place.value
    // «Рядом с вами»: участки в моём выделе, потом в моём квартале
    val nearby = remember(state.uchastki, place) {
        if (place == null) emptyList() else {
            val inVydel = state.uchastki.filter { it.kvartal == place.kvartal && place.vydel != null && it.vydel == place.vydel }
            val inKv = state.uchastki.filter { it.kvartal == place.kvartal && it !in inVydel }
            (inVydel + inKv).take(4)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (nearby.isNotEmpty()) {
            LvSectionLabel("Рядом с вами", icon = Icons.Rounded.MyLocation)
            nearby.forEach { u ->
                val selected = state.selectedUchastok?.id == u.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (selected) lv.gS else lv.sf)
                        .then(if (selected) Modifier.border(2.dp, lv.gT, RoundedCornerShape(20.dp)) else Modifier)
                        .clickable { viewModel.selectUchastok(u) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(lv.gS), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Park, contentDescription = null, tint = lv.gT, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "кв. ${u.kvartal ?: "—"} · выд. ${u.vydel ?: "—"}",
                            color = lv.tx,
                            style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                        )
                        Text(u.label, color = lv.tx2, maxLines = 2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
                    }
                    if (u.kvartal == place?.kvartal && u.vydel == place?.vydel) {
                        Text("вы тут", color = lv.gT, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
                    }
                }
            }
            LvSectionLabel("Все участки", modifier = Modifier.padding(top = 6.dp))
        }
        UchastokSelector(
            uchastki = state.uchastki,
            selected = state.selectedUchastok,
            onSelect = viewModel::selectUchastok,
            enabled = !state.isSubmitting,
        )
        state.error?.let { Text(it, color = lv.rT, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)) }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------- шаг 2: пробы ---

@Composable
private fun StepProby(state: InventarizatsiyaUiState, viewModel: InventarizatsiyaViewModel) {
    val lv = LocalLv.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.selectedUchastok?.let { UchastokHeader(it.label) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(lv.sf).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Год обследования", color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
            GodPicker(god = state.god, onSelect = viewModel::selectGod)
            ProbyList(
                proby = state.proby,
                enabled = !state.isSubmitting,
                onNomerChange = viewModel::onProbaNomerChange,
                onRazmerChange = viewModel::onProbaRazmerChange,
                onRemove = viewModel::removeProba,
                onAdd = viewModel::addProba,
                kartaProby = state.kartaProby,
                onPickKarta = viewModel::pickKartaProba,
            )
        }
        state.selectedUchastok?.let { UchastokPolyaCard(uchastok = it, enabled = !state.isSubmitting, onSaved = viewModel::onPolyaSaved) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UchastokHeader(label: String) {
    val lv = LocalLv.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(lv.sf).padding(14.dp)) {
        Text(label, color = lv.tx, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp))
    }
}

// ------------------------------------------------------- шаг 3: кликер ---

@Composable
private fun StepClicker(state: InventarizatsiyaUiState, viewModel: InventarizatsiyaViewModel) {
    val lv = LocalLv.current
    val haptic = LocalHapticFeedback.current
    val rezultaty = state.rezultatyItog
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    val poroda = current?.takeIf { p -> rezultaty.any { it.poroda == p } } ?: rezultaty.firstOrNull()?.poroda
    val entry = rezultaty.firstOrNull { it.poroda == poroda }
    val alive = entry?.prizhilos ?: 0
    val dead = poroda?.let { state.pogiblo[it] } ?: 0
    val planted = entry?.vysazheno?.trim()?.toIntOrNull()
    val percent = if (planted != null && planted > 0) (alive * 100.0 / planted).roundToInt() else null

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // породы участка — переключатель; «+» добавляет другую
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            rezultaty.forEach { r ->
                val sel = r.poroda == poroda
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) lv.g else lv.sf2)
                        .clickable { current = r.poroda }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${r.poroda} · ${r.prizhilos}",
                        color = if (sel) Color.White else lv.tx,
                        style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                    )
                }
            }
            AddPorodaChip(porody = state.porody.filter { p -> rezultaty.none { it.poroda.equals(p, true) } }) { p ->
                viewModel.addPoroda(p)
                current = p
            }
        }

        if (poroda == null) {
            Text(
                "Нажмите «+ порода», чтобы начать считать.",
                color = lv.tx2,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Приживаемость", color = lv.tx2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                Text(
                    percent?.let { "$it %" } ?: "—",
                    color = lv.gT,
                    style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("высажено", color = lv.tx2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                Text(
                    planted?.toString() ?: "—",
                    color = lv.tx,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                )
            }
        }
        Text(
            listOfNotNull(state.selectedUchastok?.label, poroda).joinToString(" · "),
            color = lv.tx2,
            maxLines = 1,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        )

        CounterButton(
            count = alive,
            label = "Живое",
            icon = Icons.Rounded.Eco,
            bg = lv.g,
            modifier = Modifier.weight(1f),
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.tapPoroda(poroda) },
        )
        CounterButton(
            count = dead,
            label = "Погибло",
            icon = Icons.Rounded.Close,
            bg = lv.r,
            modifier = Modifier.weight(1f),
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.tapPogiblo(poroda) },
        )
    }
}

@Composable
private fun CounterButton(count: Int, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$count", color = Color.White, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 68.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp))
        }
    }
}

@Composable
private fun AddPorodaChip(porody: List<String>, onAdd: (String) -> Unit) {
    val lv = LocalLv.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, lv.ln, RoundedCornerShape(14.dp))
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = lv.gT, modifier = Modifier.size(20.dp))
            Text("порода", color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (porody.isEmpty()) DropdownMenuItem(text = { Text("Все породы уже добавлены") }, onClick = { open = false }, enabled = false)
            porody.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { open = false; onAdd(p) }) }
        }
    }
}

// ------------------------------------------------------ шаг 4: проверка ---

@Composable
private fun StepCheck(state: InventarizatsiyaUiState, viewModel: InventarizatsiyaViewModel) {
    val lv = LocalLv.current
    val rez = state.rezultatyItog
    var editOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.selectedUchastok?.let { UchastokHeader(it.label + " · ${state.god}-й год") }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(lv.sf).padding(vertical = 6.dp)) {
            CheckRow("Порода", "живых", "высаж.", "%", header = true)
            rez.forEach { r ->
                val planted = r.vysazheno.trim().toIntOrNull()
                val pct = if (planted != null && planted > 0) "${(r.prizhilos * 100.0 / planted).roundToInt()}" else "—"
                CheckRow(r.poroda, "${r.prizhilos}", planted?.toString() ?: "—", pct)
            }
            if (rez.isEmpty()) {
                Text("Ничего не насчитано. Вернитесь на шаг «Считаем».", color = lv.tx2, modifier = Modifier.padding(14.dp))
            }
        }

        val total = rez.sumOf { it.prizhilos }
        val totalPlanted = rez.sumOf { it.vysazheno.trim().toIntOrNull() ?: 0 }
        if (totalPlanted > 0) {
            val pct = total * 100.0 / totalPlanted
            val ok = pct >= 85
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (ok) lv.gS else lv.aS).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (ok) "Приживаемость ${pct.roundToInt()} %. Норма, дополнять посадку не нужно."
                    else "Приживаемость ${pct.roundToInt()} %. Ниже 85 %, может понадобиться дополнение.",
                    color = lv.tx,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 21.sp),
                )
            }
        }

        PreviewCard(preview = state.preview)

        state.error?.let { Text(it, color = lv.rT, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp)) }

        LvSoftButton(text = if (editOpen) "Скрыть ручной ввод" else "Поправить цифры вручную", onClick = { editOpen = !editOpen }, height = 52)
        if (editOpen) {
            RezultatySection(
                porody = state.porody,
                rezultaty = state.rezultatyItog,
                proby = state.proby,
                enabled = !state.isSubmitting,
                onTap = viewModel::tapPoroda,
                onUndo = viewModel::undoPoroda,
                onVysazhenoChange = viewModel::onVysazhenoChange,
                onRemove = viewModel::removeRezultat,
                onAdd = viewModel::addPoroda,
                uchastok = state.selectedUchastok,
                onPrizhilosChange = viewModel::onPrizhilosChange,
                onVysazhenoAuto = viewModel::onVysazhenoAuto,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CheckRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val lv = LocalLv.current
    val st = TextStyle(fontFamily = Manrope, fontWeight = if (header) FontWeight.Bold else FontWeight.ExtraBold, fontSize = if (header) 14.sp else 18.sp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a, color = if (header) lv.tx2 else lv.tx, style = st, modifier = Modifier.weight(1.6f))
        Text(b, color = if (header) lv.tx2 else lv.gT, style = st, modifier = Modifier.weight(1f))
        Text(c, color = if (header) lv.tx2 else lv.tx, style = st, modifier = Modifier.weight(1f))
        Text(d, color = if (header) lv.tx2 else lv.tx, style = st, modifier = Modifier.weight(0.8f))
    }
}
