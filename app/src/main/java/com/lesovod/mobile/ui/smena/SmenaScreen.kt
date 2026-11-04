package com.lesovod.mobile.ui.smena

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.SmenaDelyankaDto
import com.lesovod.mobile.data.network.dto.SmenaOtchetDto
import com.lesovod.mobile.data.network.dto.SmenaUchastokDto
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.ErrorState
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import java.time.LocalDate

/**
 * «Отчёт за смену» по профессиям (04.11.2026): тракторист — трелёвка/населению/уход/почва/ремонт,
 * харвестерщик — заготовка с остатком по билету, вальщик/лесоруб/лесовод — рубки, рубки ухода с
 * остатком га по выделу, уходы, посадка, другое (+ напарники). Техника из справочника (своя
 * подставляется сама) и топливо: сжёг / заправился. Поиск делянки по кв / выд — точный.
 *
 * Было (03.11.2026) — понятный отчёт тракториста и рабочего: трелёвка (делянка,
 * рейсы, кубы), вывозка населению, уход за культурами и подготовка почвы (участок, сколько га,
 * остаток виден до конца участка), ремонт (часы). Напарники отмечаются здесь же — им отчёт
 * вводить не нужно. Заменил старый экран «Трелёвка».
 */
@Composable
fun SmenaScreen(onBack: () -> Unit, viewModel: SmenaViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Отчёт за смену", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.m))
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Новый отчёт") })
            val n = state.moi?.otchety?.size ?: 0
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (n > 0) "Мои отчёты ($n)" else "Мои отчёты") })
        }
        when {
            state.spravochnik == null && state.loadError != null -> ErrorState(
                title = "Не удалось загрузить делянки и участки",
                subtitle = state.loadError.orEmpty(),
                retryLabel = "Повторить",
                onRetry = viewModel::refresh,
            )
            tab == 1 -> MoiOtchety(state, onDelete = viewModel::delete)
            state.result != null -> {
                val f = state.form
                val o = state.spravochnik?.delyanki?.firstOrNull { it.itemId == f.delyankaItemId }?.osvoenie
                val posle = o?.takeIf { f.vid.ostatokKubov }?.let { "🌲 По делянке теперь осталось ≈ ${fmt(it.ostatok)} м³ из ${fmt(it.limit)}" }
                Gotovo(state.result!!, posle = posle, onNew = viewModel::novyy, onDone = onBack)
            }
            else -> Forma(state, viewModel)
        }
    }
}

@Composable
private fun Forma(state: SmenaUiState, vm: SmenaViewModel) {
    val f = state.form
    val s = state.spravochnik
    val prof = vm.professiya
    var pickPlace by remember { mutableStateOf(false) }
    var pickNaparnik by remember { mutableStateOf(false) }
    var pickMashina by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        state.moi?.segodnya?.takeIf { it.otchetov > 0 }?.let { t ->
            Text(
                "Сегодня уже: " + listOfNotNull(
                    "${t.otchetov} отч.",
                    t.obyom.takeIf { it > 0 }?.let { "${fmt(it)} м³" },
                    t.reysov.takeIf { it > 0 }?.let { "$it рейс." },
                    t.ga.takeIf { it > 0 }?.let { "${fmt(it)} га" },
                    t.chasy.takeIf { it > 0 }?.let { "${fmt(it)} ч" },
                    t.toplivoRashod.takeIf { it > 0 }?.let { "⛽ ${fmt(it)} л" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.spravochnikIzKesha && !state.loading) {
            StatusChip("Нет сети — списки с прошлого раза, отчёт уйдёт позже", ChipTone.WARN)
        }

        if (vm.vidy.size > 1) {
            Label("Что делал")
            // Крупные кнопки в 2 колонки — видно всё сразу, без прокрутки вбок.
            vm.vidy.chunked(2).forEach { para ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    para.forEach { v ->
                        FilterChip(
                            selected = f.vid == v,
                            onClick = { vm.setVid(v) },
                            label = { Text("${v.emoji} ${v.title}", modifier = Modifier.padding(vertical = 10.dp)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (para.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Label("Когда")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            listOf(0 to "Сегодня", 1 to "Вчера", 2 to "Позавчера").forEach { (d, l) ->
                FilterChip(selected = f.dneyNazad == d, onClick = { vm.update { it.copy(dneyNazad = d) } }, label = { Text(l) })
            }
            var more by remember { mutableStateOf(false) }
            FilterChip(
                selected = f.dneyNazad > 2,
                onClick = { more = !more },
                label = { Text(if (f.dneyNazad > 2) data(f.dneyNazad) else "Раньше…") },
            )
            if (more) {
                (3..14).forEach { d ->
                    FilterChip(selected = f.dneyNazad == d, onClick = { vm.update { it.copy(dneyNazad = d) }; more = false }, label = { Text(data(d)) })
                }
            }
        }

        // --- место работы ---
        if (f.vid.mozhnoNaKulturah) {
            Label("Где работал")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(
                    selected = !f.naKulturah,
                    onClick = { vm.update { it.copy(naKulturah = false, proyden = false, ga = "") } },
                    label = { Text("🌲 Делянка") },
                )
                FilterChip(
                    selected = f.naKulturah,
                    onClick = { vm.update { it.copy(naKulturah = true, proyden = false, ga = "") } },
                    label = { Text("🌱 Участок культур") },
                )
            }
        }
        if (f.naDelyanke) {
            val sel = s?.delyanki?.firstOrNull { it.itemId == f.delyankaItemId }
            Label(if (f.vid.gaPoVydelu) "Делянка / выдел" else "Делянка")
            PickerField(text = sel?.label, placeholder = "Выбрать делянку (кв / выд)", onClick = { pickPlace = true })
            if (f.vid.ostatokKubov && sel != null) OstatokKubov(sel)
            if (f.vid.gaPoVydelu && sel != null) VydelOstatok(sel, f.vid.code)
            if (f.vid == SmenaVid.TRELEVKA || f.vid == SmenaVid.NASELENIYU) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    NumField("Рейсов", f.reysov, KeyboardType.Number, Modifier.weight(1f)) { v -> vm.update { it.copy(reysov = v) } }
                    NumField("Кубов, м³", f.obyom, KeyboardType.Decimal, Modifier.weight(1f)) { v -> vm.update { it.copy(obyom = v) } }
                }
            } else if (f.vid.sKubami) {
                NumField(
                    if (f.vid.kubyObyazatelny) "Кубов за смену, м³" else "Кубов, м³ (если считал)",
                    f.obyom, KeyboardType.Decimal, Modifier.fillMaxWidth(),
                ) { v -> vm.update { it.copy(obyom = v) } }
            }
            if (f.vid == SmenaVid.NASELENIYU) {
                Label("Кому везли")
                OutlinedTextField(
                    value = f.poluchatel,
                    onValueChange = { v -> vm.update { it.copy(poluchatel = v) } },
                    placeholder = { Text("ФИО или адрес") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Podskazki(s?.poluchateli.orEmpty()) { v -> vm.update { it.copy(poluchatel = v) } }
            }
        }
        if (f.naUchastke) {
            val sel = s?.uchastki?.firstOrNull { it.id == f.uchastokId }
            Label("Участок культур")
            PickerField(text = sel?.label, placeholder = "Выбрать участок (кв / выд)", onClick = { pickPlace = true })
            sel?.let { UchastokOstatok(it, f.vid.code) }
        }
        if (f.vid.sGa) {
            val ost = if (f.naUchastke) {
                val u = s?.uchastki?.firstOrNull { it.id == f.uchastokId }
                u?.cikl(f.vid.code)?.ostatok ?: u?.takeIf { it.cikl(f.vid.code) == null }?.ploshad
            } else {
                val d = s?.delyanki?.firstOrNull { it.itemId == f.delyankaItemId }
                d?.cikl(f.vid.code)?.ostatok ?: d?.takeIf { it.cikl(f.vid.code) == null }?.ploshad
            }
            NumField(
                if (f.vid == SmenaVid.SANRUBKA) "Сделал за смену, га (если считал)" else "Сделал за смену, га",
                f.ga, KeyboardType.Decimal, Modifier.fillMaxWidth(), enabled = !f.proyden,
            ) { v -> vm.update { it.copy(ga = v) } }
            if (ost != null && ost > 0 && !f.proyden) {
                TextButton(onClick = { vm.update { it.copy(ga = fmt(ost)) } }) { Text("Доделал остаток — ${fmt(ost)} га") }
            }
            val chto = if (f.naUchastke) "Участок" else "Выдел"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.update { it.copy(proyden = !it.proyden) } }) {
                Checkbox(checked = f.proyden, onCheckedChange = { c -> vm.update { it.copy(proyden = c) } })
                Text("$chto пройден полностью", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Не успел за день — не беда: в следующий раз увидишь, сколько осталось.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- часы ---
        Label(if (f.vid == SmenaVid.REMONT) "Сколько часов ремонт" else "Часов отработал")
        NumField("Часы", f.chasy, KeyboardType.Decimal, Modifier.fillMaxWidth()) { v -> vm.update { it.copy(chasy = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            listOf("4", "8", "10", "12").forEach { h ->
                FilterChip(selected = f.chasy == h, onClick = { vm.update { it.copy(chasy = h) } }, label = { Text("$h ч") })
            }
        }

        // --- техника и топливо ---
        val sTehnikoy = prof.tehnikaVsegda || f.sTehnikoy || f.vid == SmenaVid.REMONT
        if (!sTehnikoy) {
            TextButton(onClick = { vm.update { it.copy(sTehnikoy = true) } }) { Text("＋ Был на технике / с пилой — указать технику и топливо") }
        } else {
            val mashiny = s?.mashiny.orEmpty()
            Label(if (f.vid == SmenaVid.REMONT) "Какая техника" else "Техника")
            if (mashiny.isNotEmpty()) {
                val m = mashiny.firstOrNull { it.id == f.tehnikaId }
                PickerField(
                    text = m?.let { it.label + if (it.moya) " (твоя)" else "" },
                    placeholder = "Выбрать технику",
                    onClick = { pickMashina = true },
                )
            } else {
                OutlinedTextField(
                    value = f.tehnika,
                    onValueChange = { v -> vm.update { it.copy(tehnika = v) } },
                    placeholder = { Text("МТЗ-82, ТДТ-55…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Podskazki(s?.tehnika.orEmpty()) { v -> vm.update { it.copy(tehnika = v) } }
            }
            if (f.vid != SmenaVid.REMONT) {
                val top = mashiny.firstOrNull { it.id == f.tehnikaId }?.toplivo
                Label("Топливо" + (top?.let { " ($it)" } ?: ""))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    NumField("Сжёг за смену, л", f.toplivoRashod, KeyboardType.Decimal, Modifier.weight(1f)) { v ->
                        vm.update { it.copy(toplivoRashod = v) }
                    }
                    NumField("Заправился, л", f.zapravka, KeyboardType.Decimal, Modifier.weight(1f)) { v ->
                        vm.update { it.copy(zapravka = v) }
                    }
                }
                Text(
                    "Заправку пиши, только если заправлялся в этот день.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = f.kommentariy,
            onValueChange = { v -> vm.update { it.copy(kommentariy = v) } },
            label = {
                Text(
                    when (f.vid) {
                        SmenaVid.REMONT -> "Что ремонтировали"
                        SmenaVid.DRUGOE -> "Что делал"
                        else -> "Комментарий (необязательно)"
                    },
                )
            },
            minLines = if (f.vid == SmenaVid.REMONT || f.vid == SmenaVid.DRUGOE) 2 else 1,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- напарники (только бригада; трактористов/харвестерщиков сервер в список не даёт) ---
        if (prof.sNaparnikami) {
            Label("С кем работал")
            val vse = s?.naparniki.orEmpty()
            if (f.naparniki.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    f.naparniki.mapNotNull { id -> vse.firstOrNull { it.id == id } }.forEach { n ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().softCard(RoundedCornerShape(12.dp)).padding(start = Spacing.m),
                        ) {
                            Text("👷 ${n.fio}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { vm.toggleNaparnik(n.id) }) { Icon(Icons.Rounded.Close, contentDescription = "Убрать") }
                        }
                    }
                }
            }
            SecondaryButton(
                text = if (f.naparniki.isEmpty()) "Отметить напарника" else "Ещё напарник",
                onClick = { pickNaparnik = true },
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp)) },
            )
            val last = vm.lastNaparniki().filter { id -> vse.any { it.id == id } }
            if (f.naparniki.isEmpty() && last.isNotEmpty()) {
                TextButton(onClick = { vm.update { it.copy(naparniki = last.toSet()) } }) {
                    Text("Как в прошлый раз: " + last.mapNotNull { id -> vse.firstOrNull { it.id == id }?.fio?.substringBefore(' ') }.joinToString(", "))
                }
            }
            if (f.naparniki.isNotEmpty()) {
                Text(
                    "Напарникам вводить отчёт не нужно — он засчитается всем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.error?.let { StatusChip(it, ChipTone.ERROR) }
        PrimaryButton(
            text = "Отправить отчёт",
            onClick = vm::submit,
            enabled = !state.sending,
            modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xl),
            icon = if (state.sending) {
                { CircularProgressIndicator(Modifier.size(18.dp).padding(end = 6.dp), strokeWidth = 2.dp) }
            } else null,
        )
    }

    if (pickPlace && s != null) {
        if (f.naDelyanke) {
            val code = f.vid.code
            PoiskDialog(
                title = "Делянка",
                items = s.delyanki,
                key = { it.itemId },
                label = { it.label },
                sub = { d ->
                    val c = d.cikl(code)
                    val o = d.osvoenie
                    listOfNotNull(
                        if (f.vid.ostatokKubov && o != null) "осталось ≈ ${fmt(o.ostatok)} м³ из ${fmt(o.limit)}" else null,
                        if (f.vid.gaPoVydelu && c != null) "⏳ сделано ${fmt(c.sdelano)} га" + (c.ostatok?.let { ", осталось ${fmt(it)} га" } ?: "") else null,
                        if (f.vid.gaPoVydelu && c == null) d.ploshad?.let { "${fmt(it)} га" } else null,
                        if (d.nedavno != null) "работал ${ruData(d.nedavno)}" else null,
                    ).joinToString(" · ").ifEmpty { null }
                },
                kvVyd = { it.kvartal to it.vydel },
                selected = setOfNotNull(f.delyankaItemId),
                onPick = { d -> vm.update { it.copy(delyankaItemId = d.itemId, proyden = false, ga = "") }; pickPlace = false },
                onDismiss = { pickPlace = false },
            )
        } else {
            val code = f.vid.code
            val sorted = remember(s.uchastki, code) { s.uchastki.sortedBy { if (it.cikl(code) != null) 0 else 1 } }
            PoiskDialog(
                title = "Участок культур",
                items = sorted,
                key = { it.id },
                label = { it.label },
                sub = { u ->
                    val c = u.cikl(code)
                    when {
                        c != null -> "⏳ в работе: сделано ${fmt(c.sdelano)} га" + (c.ostatok?.let { ", осталось ${fmt(it)} га" } ?: "")
                        u.ploshad != null -> "${fmt(u.ploshad)} га"
                        else -> null
                    }
                },
                kvVyd = { it.kvartal to it.vydel },
                selected = setOfNotNull(f.uchastokId),
                onPick = { u -> vm.update { it.copy(uchastokId = u.id, proyden = false, ga = "") }; pickPlace = false },
                onDismiss = { pickPlace = false },
            )
        }
    }
    if (pickMashina && s != null) {
        PoiskDialog(
            title = "Техника",
            items = s.mashiny,
            key = { it.id },
            label = { it.label },
            sub = { m ->
                listOfNotNull(
                    m.tip,
                    m.toplivo,
                    if (m.moya) "🔑 твоя" else m.zakreplen?.let { "за: $it" },
                ).joinToString(" · ").ifEmpty { null }
            },
            selected = setOfNotNull(f.tehnikaId),
            onPick = { m -> vm.update { it.copy(tehnikaId = m.id) }; pickMashina = false },
            onDismiss = { pickMashina = false },
        )
    }
    if (pickNaparnik && s != null) {
        val sorted = remember(s.naparniki) { vm.naparnikiSorted() }
        val chastye = remember(s.naparniki) { vm.chastye() }
        PoiskDialog(
            title = "С кем работал",
            items = sorted,
            key = { it.id },
            label = { it.fio },
            sub = { n -> listOfNotNull(n.dolzhnost, if (n.id in chastye) "⭐ часто вместе" else null).joinToString(" · ").ifEmpty { null } },
            selected = f.naparniki,
            multi = true,
            onPick = { n -> vm.toggleNaparnik(n.id) },
            onDismiss = { pickNaparnik = false },
        )
    }
}

/** Харвестер / валка: сколько осталось по билету. */
@Composable
private fun OstatokKubov(d: SmenaDelyankaDto) {
    val o = d.osvoenie
    if (o == null) {
        StatusChip("По этой делянке нет лимита в базе — остаток не посчитать", ChipTone.NEUTRAL)
        return
    }
    val tone = when {
        o.ostatok <= 0 -> ChipTone.ERROR
        (o.pct ?: 0.0) >= 90 -> ChipTone.WARN
        else -> ChipTone.OK
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatusChip(
            if (o.ostatok > 0) "🌲 Осталось ≈ ${fmt(o.ostatok)} м³ из ${fmt(o.limit)} по билету"
            else "⛔ Лимит выбран (${fmt(o.fakt)} из ${fmt(o.limit)} м³)" + (o.ostatok110?.takeIf { it > 0 }?.let { " — до +10% ещё ${fmt(it)} м³" } ?: ""),
            tone,
        )
        Text(
            "Заготовлено ≈ ${fmt(o.fakt)} м³ — большее из: " + listOfNotNull(
                o.faktNaryad?.let { "наряды ${fmt(it)}" },
                o.faktEgais?.let { "ЕГАИС ${fmt(it)}" },
                o.poOtchetam?.let { "отчёты ${fmt(it)}" },
            ).joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Рубка ухода / очистка / санрубка: сколько га по выделу уже сделано. */
@Composable
private fun VydelOstatok(d: SmenaDelyankaDto, vid: String) {
    val c = d.cikl(vid)
    val text = when {
        c != null -> "⏳ Выдел в работе: сделано ${fmt(c.sdelano)} га" +
            (d.ploshad?.let { " из ${fmt(it)}" } ?: "") + (c.ostatok?.let { " · осталось ${fmt(it)} га" } ?: "") +
            (c.posledniy?.let { " (посл. ${ruData(it)})" } ?: "")
        d.ploshad != null -> "Площадь выдела ${fmt(d.ploshad)} га — начинаешь с нуля"
        else -> null
    } ?: return
    StatusChip(text, if (c != null) ChipTone.WARN else ChipTone.NEUTRAL)
}

@Composable
private fun UchastokOstatok(u: SmenaUchastokDto, vid: String) {
    val c = u.cikl(vid)
    val text = when {
        c != null -> "⏳ Участок в работе: сделано ${fmt(c.sdelano)} га" +
            (u.ploshad?.let { " из ${fmt(it)}" } ?: "") + (c.ostatok?.let { " · осталось ${fmt(it)} га" } ?: "") +
            (c.posledniy?.let { " (посл. ${ruData(it)})" } ?: "")
        u.ploshad != null -> "Площадь участка ${fmt(u.ploshad)} га — начинаешь с нуля"
        else -> null
    } ?: return
    StatusChip(text, if (c != null) ChipTone.WARN else ChipTone.NEUTRAL)
}

@Composable
private fun Gotovo(result: SmenaResult, posle: String?, onNew: () -> Unit, onDone: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
    ) {
        when (result) {
            is SmenaResult.Sent -> {
                StatusChip(if (result.created.povtor) "Этот отчёт уже был отправлен" else "Отправлено ✅", ChipTone.OK)
                Text(result.opisanie, style = MaterialTheme.typography.titleMedium)
                val c = result.created
                when {
                    c.zakrytCikl != null -> Text(
                        "🎉 Пройден целиком (${fmt(c.zakrytCikl.sdelano ?: 0.0)} га) — «${c.zakrytCikl.tip ?: "уход"}» отмечен как сделанный.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    c.ostatok != null && c.ostatok > 0 -> Text(
                        "Сделано ${fmt(c.sdelanoVCikle ?: 0.0)} из ${fmt(c.ploshad ?: 0.0)} га — осталось ${fmt(c.ostatok)} га. " +
                            "В следующий раз остаток будет виден сразу.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            is SmenaResult.Queued -> {
                StatusChip("Нет сети — отчёт сохранён на телефоне", ChipTone.WARN)
                Text(result.opisanie, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Он отправится сам, как только появится связь. Дважды не уйдёт.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (result is SmenaResult.Sent && posle != null) Text(posle, style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(text = "Ещё отчёт", onClick = onNew)
        SecondaryButton(text = "Готово", onClick = onDone)
    }
}

@Composable
private fun MoiOtchety(state: SmenaUiState, onDelete: (Int) -> Unit) {
    val list = state.moi?.otchety.orEmpty()
    var confirm by remember { mutableStateOf<SmenaOtchetDto?>(null) }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
            Text(
                if (state.moi == null) "Загружаю…" else "За 2 недели отчётов нет",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l),
    ) {
        item { Spacer(Modifier.size(Spacing.s)) }
        state.error?.let { e -> item { StatusChip(e, ChipTone.ERROR) } }
        items(list, key = { it.id }) { r ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().softCard(RoundedCornerShape(16.dp)).padding(Spacing.m),
            ) {
                Text(r.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text("${r.vidTitle} · ${ruData(r.data)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    val det = listOfNotNull(
                        r.mesto,
                        r.reysov?.let { "$it рейс." },
                        r.obyom?.let { "${fmt(it)} м³" },
                        r.ga?.let { "${fmt(it)} га" },
                        r.chasy?.let { "${fmt(it)} ч" },
                        r.poluchatel?.let { "кому: $it" },
                        r.tehnika,
                        r.toplivoRashod?.let { "⛽ ${fmt(it)} л" + (r.toplivo?.let { t -> " $t" } ?: "") },
                        r.zapravka?.let { "заправка ${fmt(it)} л" },
                    ).joinToString(" · ")
                    if (det.isNotEmpty()) Text(det, style = MaterialTheme.typography.bodyMedium)
                    val kto = if (!r.moy) "отметил(а) ${r.fio}" else r.naparniki.takeIf { it.isNotEmpty() }?.let { n -> "с " + n.joinToString(", ") { it.fio } }
                    kto?.let { Text("👷 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (r.zakrylCikl) Text("✅ пройден полностью", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (r.mozhnoUdalit) {
                    if (state.deletingId == r.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { confirm = r }) { Icon(Icons.Rounded.Delete, contentDescription = "Удалить") }
                }
            }
        }
        item { Spacer(Modifier.size(Spacing.xl)) }
    }
    confirm?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Удалить отчёт?") },
            text = { Text("${r.emoji} ${r.vidTitle} за ${ruData(r.data)}${r.mesto?.let { " — $it" } ?: ""}") },
            confirmButton = { TextButton(onClick = { onDelete(r.id); confirm = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

/** Список с поиском во весь экран: делянки, участки, напарники. [multi] — не закрывается после выбора. */
@Composable
private fun <T> PoiskDialog(
    title: String,
    items: List<T>,
    key: (T) -> Int,
    label: (T) -> String,
    sub: (T) -> String?,
    selected: Set<Int>,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    multi: Boolean = false,
    /** Квартал и выдел элемента — тогда сверху два поля «Кв» и «Выд» с точным совпадением. */
    kvVyd: ((T) -> Pair<String?, String?>)? = null,
) {
    var q by remember { mutableStateOf("") }
    var kv by remember { mutableStateOf("") }
    var vyd by remember { mutableStateOf("") }
    val words = q.trim().lowercase().split(Regex("[\\s/.,]+")).filter { it.isNotEmpty() }
    val shown = items.filter { x ->
        val t = (label(x) + " " + sub(x).orEmpty()).lowercase()
        val okSlova = words.all { it in t }
        val okKv = kvVyd == null || (kv.isBlank() && vyd.isBlank()) || run {
            val (k, v) = kvVyd(x)
            sovpadaet(k, kv) && sovpadaet(v, vyd)
        }
        okSlova && okKv
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (multi) TextButton(onClick = onDismiss) { Text("Готово (${selected.size})") }
                }
                if (kvVyd != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
                    ) {
                        OutlinedTextField(
                            value = kv,
                            onValueChange = { kv = it.filter { c -> c.isLetterOrDigit() } },
                            label = { Text("Квартал") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = vyd,
                            onValueChange = { vyd = it.filter { c -> c.isLetterOrDigit() } },
                            label = { Text("Выдел") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    placeholder = { Text(if (kvVyd != null) "Или поиск по названию…" else "Поиск: фамилия, квартал, выдел…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                )
                LazyColumn(Modifier.fillMaxSize().padding(top = Spacing.s)) {
                    if (shown.isEmpty()) item {
                        Text("Ничего не нашлось", modifier = Modifier.padding(Spacing.l), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(shown, key = { key(it) }) { x ->
                        val on = key(x) in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onPick(x) }
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background)
                                .padding(horizontal = Spacing.l, vertical = Spacing.s),
                        ) {
                            if (multi) Checkbox(checked = on, onCheckedChange = { onPick(x) })
                            Column(Modifier.weight(1f)) {
                                Text(label(x), style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                sub(x)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PickerField(text: String?, placeholder: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .softCard(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Text(
            text ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (text != null) FontWeight.Bold else FontWeight.Normal,
            color = if (text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    type: KeyboardType,
    modifier: Modifier,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier,
    )
}

@Composable
private fun Podskazki(list: List<String>, onPick: (String) -> Unit) {
    if (list.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        list.take(8).forEach { v -> FilterChip(selected = false, onClick = { onPick(v) }, label = { Text(v) }) }
    }
}

private val MES = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun data(dneyNazad: Int): String {
    val d = LocalDate.now().minusDays(dneyNazad.toLong())
    return "${d.dayOfMonth} ${MES[d.monthValue - 1]}"
}

/**
 * Точное совпадение квартала/выдела: «71» не находит «171». В поле выдела у делянки может
 * быть список («2,8,9») — тогда ищем среди них.
 */
private fun sovpadaet(znachenie: String?, vvod: String): Boolean {
    val v = vvod.trim().lowercase()
    if (v.isEmpty()) return true
    val chasti = znachenie.orEmpty().lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    return chasti.any { it == v }
}

internal fun ruData(iso: String): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    val today = LocalDate.now()
    when (d) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> "${d.dayOfMonth} ${MES[d.monthValue - 1]}"
    }
}.getOrDefault(iso)
