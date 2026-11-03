package com.lesovod.mobile.ui.igra

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.GeroyDto
import com.lesovod.mobile.data.network.dto.IgraChellendzhDto
import com.lesovod.mobile.data.network.dto.IgraDto
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import kotlin.math.roundToInt

private val Gold = Color(0xFFE0A526)
private val Pine = Color(0xFF1A4331)

private fun n(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(java.util.Locale("ru"), v)

private val CHAR = listOf(
    "sila" to "💪 Сила (кубы)",
    "vynoslivost" to "🫀 Выносливость (дни)",
    "nadezhnost" to "🛡 Надёжность (задачи)",
    "opyt" to "🧭 Опыт (виды работ)",
)

private val RAZBIVKA = mapOf(
    "dni" to "дни в лесу", "kuby" to "кубы", "zadachi" to "задачи", "trelevka" to "трелёвка",
    "universal" to "универсал", "smena" to "отчёты смены", "uhod" to "уходы",
)

/** «🎮 Мой герой»: карточка, рейтинг с соперником, челленджи команды, ачивки. */
@Composable
fun IgraScreen(onBack: () -> Unit, viewModel: IgraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text("🎮 Мой герой", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.m))
        }
        val d = state.data
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                if (state.error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::load) { Text("Повторить") }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Column
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            if (state.error != null) StatusChip("Нет связи — показываю прошлые данные", ChipTone.WARN)
            val g = d.geroy
            if (g != null) GeroyCard(g)
            else Text("Карточка появится, когда ты будешь в табеле.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Reyting(d, state.period, viewModel::setPeriod)
            if (d.chellendzhi.isNotEmpty() || d.zvaniyaKomandy.isNotEmpty()) Chellendzhi(d)
            d.geroy?.let { Achivki(it) }
            d.geroy?.let { Mesta(it) }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
    state.novyyUroven?.let { lvl ->
        AlertDialog(
            onDismissRequest = viewModel::zakrytPozdravlenie,
            title = { Text("🎉 Новый уровень $lvl!") },
            text = { Text("${state.data?.geroy?.zvanie.orEmpty()} — так держать. Каждая смена приближает следующий.") },
            confirmButton = { TextButton(onClick = viewModel::zakrytPozdravlenie) { Text("Ура!") } },
        )
    }
}

@Composable
private fun GeroyCard(g: GeroyDto) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth().background(Pine, RoundedCornerShape(20.dp)).padding(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.klass.take(2).trim(), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(end = Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(g.fio, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(g.klass.drop(2).trim().ifEmpty { g.dolzhnost.orEmpty() }, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${g.uroven}", style = MaterialTheme.typography.headlineMedium, color = Gold, fontWeight = FontWeight.Black)
                Text("уровень", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Text("«${g.zvanie}»", color = Gold, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { (g.pct / 100.0).toFloat().coerceIn(0f, 1f) },
            color = Gold,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Text(
            "${n(g.ochki)} очков · до ${g.uroven + 1} уровня ещё ${n((g.doOchkov - g.ochki).coerceAtLeast(0.0))}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
        )
        g.seychas?.let { s ->
            Text("📍 Сейчас: ${listOfNotNull(s.vid, s.mesto).joinToString(", ")}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        g.zveno?.let { Text("${it.emoji} Звено: ${it.nazvanie}", color = Color.White, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(Spacing.xs))
        CHAR.forEach { (k, l) ->
            val h = g.harakteristiki[k] ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(170.dp))
                LinearProgressIndicator(
                    progress = { (h.pct / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = Color(0xFF8FD19E),
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.weight(1f).height(6.dp),
                )
                Text(n(h.znach), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.s).width(44.dp))
            }
        }
    }
}

@Composable
private fun Reyting(d: IgraDto, period: String, onPeriod: (String) -> Unit) {
    val r = d.reyting
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("🏆 Рейтинг", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            (r.periody.ifEmpty { mapOf("nedelya" to "Неделя", "mesyac" to "Месяц") }).forEach { (k, l) ->
                FilterChip(selected = period == k, onClick = { onPeriod(k) }, label = { Text(l) })
            }
        }
        val ya = r.ya
        if (ya != null) {
            Text(
                "Ты на ${ya.mesto} месте из ${r.vIgre} · ${n(ya.ochki)} очков ${ya.znachki.joinToString("")}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            ya.sdvig?.takeIf { it != 0.0 }?.let {
                Text(if (it > 0) "▲ поднялся на ${n(it)}" else "▼ опустился на ${n(-it)}", color = if (it > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
            }
            val sop = ya.sopernik
            if (sop != null) Text("🎯 До ${sop.fio} — ${n(sop.otryv)} очков. Догонишь?", style = MaterialTheme.typography.bodyMedium)
            else Text("👑 Ты впереди всех!", style = MaterialTheme.typography.bodyMedium)
            ya.dogonyaet?.let { Text("👣 За тобой ${it.fio}, отрыв ${n(it.otryv)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val po = ya.ochkiPo.filterValues { it > 0 }
            if (po.isNotEmpty()) {
                Text(
                    "Из чего очки: " + po.entries.joinToString(" · ") { (k, v) -> "${RAZBIVKA[k] ?: k} ${n(v)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text("За этот период тебя пока нет в рейтинге — отметься в табеле и отправь отчёт за смену.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Spacing.xs))
        r.top.forEach { p ->
            val me = p.sotrudnikId == r.ya?.sotrudnikId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (me) Gold.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(horizontal = Spacing.s, vertical = 6.dp),
            ) {
                Text(
                    when (p.mesto) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${p.mesto}." },
                    modifier = Modifier.width(32.dp),
                )
                Text(p.fio, modifier = Modifier.weight(1f), fontWeight = if (me) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.znachki.joinToString(""), modifier = Modifier.padding(horizontal = Spacing.xs))
                Text(n(p.ochki), fontWeight = FontWeight.Bold)
            }
        }
        if (r.zvenya.isNotEmpty()) {
            Text("Звенья: " + r.zvenya.joinToString(" · ") { "${it.emoji} ${it.nazvanie} (${n(it.srednee)})" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chellendzhi(d: IgraDto) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("🔥 Челленджи команды", style = MaterialTheme.typography.titleSmall)
        d.chellendzhi.forEach { ChellendzhRow(it) }
        if (d.zvaniyaKomandy.isNotEmpty()) {
            Text("Звания команды: " + d.zvaniyaKomandy.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChellendzhRow(c: IgraChellendzhDto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("${c.emoji.orEmpty()} ${c.nazvanie}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${c.pct.roundToInt()}%", fontWeight = FontWeight.Bold, color = if (c.pct >= 100) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { (c.pct / 100.0).toFloat().coerceIn(0f, 1f) },
            color = if (c.pct >= 100) Color(0xFF2E7D32) else Gold,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        val det = listOfNotNull(
            if (c.fakt != null && c.cel != null) "${n(c.fakt)} из ${n(c.cel)} ${c.ed.orEmpty()}" else null,
            c.doDaty?.let { "до ${it.takeLast(5).split('-').reversed().joinToString(".")}" },
            c.priz?.let { "приз: $it" },
        ).joinToString(" · ")
        if (det.isNotEmpty()) Text(det, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        c.temp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Achivki(g: GeroyDto) {
    if (g.achivki.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("🏅 Ачивки (${g.achivki.count { it.est }} из ${g.achivki.size})", style = MaterialTheme.typography.titleSmall)
        g.achivki.sortedByDescending { if (it.est) 101.0 else it.progress }.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.z, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontWeight = if (a.est) FontWeight.Bold else FontWeight.Normal)
                    Text(a.opis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!a.est) {
                        LinearProgressIndicator(
                            progress = { (a.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
                        )
                    }
                }
                Text(if (a.est) "✅" else "${n(a.tek)}/${n(a.nado)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

@Composable
private fun Mesta(g: GeroyDto) {
    if (g.mesta.isEmpty() && g.navyki.isEmpty() && g.naparnik == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("🗺 Где и что", style = MaterialTheme.typography.titleSmall)
        if (g.navyki.isNotEmpty()) Text("Навыки: " + g.navyki.joinToString(" · ") { "${it.vid} ${n(it.dni)} дн." }, style = MaterialTheme.typography.bodyMedium)
        g.mesta.take(5).forEach { Text("📍 ${it.mesto} — ${n(it.dni)} дн.", style = MaterialTheme.typography.bodyMedium) }
        g.naparnik?.let { Text("🤝 Чаще всего с: ${it.fio} (${n(it.dney)} дн.)", style = MaterialTheme.typography.bodyMedium) }
        Text("За год: ${n(g.god.dni)} дн. · ${n(g.god.kuby)} м³ · ${n(g.god.ochki)} очков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Компактная карточка героя на главном экране — только когда игровой режим включён. */
@Composable
fun HomeIgraCard(onOpen: () -> Unit, viewModel: IgraViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val d = state.data ?: return
    val g = d.geroy
    val ya = d.reyting.ya
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Pine, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g?.klass?.take(2)?.trim() ?: "🎮", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(end = Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    if (g != null) "Уровень ${g.uroven} · ${g.zvanie}" else "Игровой режим",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    ya?.let { "🏆 ${it.mesto} место за неделю · ${n(it.ochki)} очк." } ?: "Отметься и отправь отчёт — попадёшь в рейтинг",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("›", color = Gold, style = MaterialTheme.typography.headlineMedium)
        }
        g?.let {
            LinearProgressIndicator(
                progress = { (it.pct / 100.0).toFloat().coerceIn(0f, 1f) },
                color = Gold,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
        ya?.sopernik?.let { Text("🎯 До ${it.fio} — ${n(it.otryv)} очков", color = Gold, style = MaterialTheme.typography.bodySmall) }
        d.chellendzhi.firstOrNull()?.let { c ->
            Text("🔥 ${c.nazvanie}: ${c.pct.roundToInt()}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}
