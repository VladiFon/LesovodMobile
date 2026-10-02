package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import com.lesovod.mobile.data.network.dto.MyDelyankaDto
import com.lesovod.mobile.data.repository.FieldPrepState
import com.lesovod.mobile.ui.bot.StockUiState
import com.lesovod.mobile.ui.bot.filterByQuery
import com.lesovod.mobile.ui.bot.title
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.roundToInt
import com.lesovod.mobile.data.network.dto.PorodaRemainingDto
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import com.lesovod.mobile.ui.bot.StockViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.semanticColors
import com.lesovod.mobile.ui.theme.softCard

/**
 * Экран 7 редизайна «Поляна» (docs/SCREENS.md) — «Остатки». С 0.6.0 начинается со списка
 * «Мои делянки» (свои сверху, у каждой — % освоения и сколько ещё можно до 110%); нажатие на
 * делянку сразу открывает остаток по ней. Без связи — последние сохранённые данные с пометкой.
 */
@Composable
fun StockScreen(viewModel: StockViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val prep by viewModel.prepState.collectAsState()

    BackHandler(enabled = state.selectedId != null) { viewModel.closeDelyanka() }

    if (state.selectedId == null) {
        MyDelyankiList(
            state = state,
            prep = prep,
            onQueryChange = viewModel::onQueryChange,
            onRefresh = viewModel::loadList,
            onOpen = viewModel::openDelyanka,
            onPrepare = viewModel::prepareForTrip,
        )
    } else {
        RemainingDetail(state = state, onBack = viewModel::closeDelyanka, onRetry = viewModel::loadRemaining)
    }
}

@Composable
private fun MyDelyankiList(
    state: StockUiState,
    prep: FieldPrepState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onPrepare: () -> Unit,
) {
    val filtered = state.delyanki.filterByQuery(state.query)
    val moi = filtered.filter { it.moya }
    val vse = filtered.filterNot { it.moya }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item { ScreenTitle("Мои делянки") }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(horizontal = Spacing.l),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Квартал или название") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.listStamp?.let { StatusChip(text = it, tone = ChipTone.WARN, icon = Icons.Filled.CloudOff) }
                PrepareForTripButton(prep = prep, onPrepare = onPrepare)
            }
        }

        when {
            state.isLoadingList && state.delyanki.isEmpty() -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.listError != null && state.delyanki.isEmpty() -> item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(horizontal = Spacing.l),
                ) {
                    StatusChip(text = state.listError.orEmpty(), tone = ChipTone.ERROR)
                    SecondaryButton(text = "Повторить", onClick = onRefresh)
                }
            }
            filtered.isEmpty() -> item {
                Text(
                    if (state.query.isBlank()) "Активных делянок нет" else "Ничего не найдено по «${state.query.trim()}»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
            }
            else -> {
                if (moi.isNotEmpty()) {
                    item { SectionHeader("Мои") }
                    items(moi, key = { "m${it.delyankaId}" }) { MyDelyankaRow(it, onClick = { onOpen(it.delyankaId) }) }
                }
                if (vse.isNotEmpty()) {
                    item { SectionHeader("Все активные") }
                    items(vse, key = { "a${it.delyankaId}" }) { MyDelyankaRow(it, onClick = { onOpen(it.delyankaId) }) }
                }
            }
        }
    }
}

/** «Подготовиться к выезду» + строка результата («Готово: N делянок, данные на чч:мм»). */
@Composable
fun PrepareForTripButton(prep: FieldPrepState, onPrepare: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SecondaryButton(
            text = if (prep.running) "Сохраняем данные…" else "Подготовиться к выезду",
            onClick = onPrepare,
            enabled = !prep.running,
            icon = if (prep.running) {
                { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
            } else {
                { Icon(Icons.Filled.CloudDownload, contentDescription = null) }
            },
        )
        prep.result?.let { StatusChip(text = it, tone = ChipTone.OK) }
        prep.error?.let { StatusChip(text = it, tone = ChipTone.ERROR) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
    )
}

/** Строка делянки: название, кв./выд., цветной % освоения и «до 110%: N м³». */
@Composable
private fun MyDelyankaRow(d: MyDelyankaDto, onClick: () -> Unit) {
    val level = levelOfWire(d.level, d.pct)
    val (chipBg, chipFg) = levelColors(level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier
            .padding(horizontal = Spacing.l)
            .fillMaxWidth()
            .softCard()
            .clickable(onClick = onClick)
            .padding(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.title(), style = MaterialTheme.typography.titleSmall)
            val place = listOfNotNull(
                d.kvartal?.takeIf { it.isNotBlank() }?.let { "кв. $it" },
                d.vydel?.takeIf { it.isNotBlank() }?.let { "выд. $it" },
                d.lesosekaNomer?.takeIf { it.isNotBlank() }?.let { "лесосека $it" },
            ).joinToString(" · ")
            if (place.isNotBlank() && place != d.title()) {
                Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            d.mozhnoDo110?.let {
                Text(
                    "до 110%: ${it.fmt()} м³",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (it < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Text(
            d.pct?.let { "${it.pct()}%" } ?: "нет лимита",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = chipFg,
            modifier = Modifier
                .background(chipBg, MaterialTheme.shapes.small)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Остаток по выбранной делянке — как раньше, но без ручного ввода квартала/выдела/лесосеки. */
@Composable
private fun RemainingDetail(state: StockUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Все делянки")
            }
            Text(
                state.remaining?.delyankaNazvanie?.takeIf { it.isNotBlank() } ?: state.selectedTitle ?: "Остаток по делянке",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        ) {
            if (state.isLoadingRemaining) {
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.remainingStamp?.let { StatusChip(text = it, tone = ChipTone.WARN, icon = Icons.Filled.CloudOff) }

            if (state.error != null) {
                StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
                SecondaryButton(text = "Повторить", onClick = onRetry)
            }

            val remaining = state.remaining
            if (remaining != null) {
                if (!remaining.found) {
                    Text(
                        "Делянка не найдена в системе расхода",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val delyankaLabel = remaining.delyankaNazvanie?.takeIf { it.isNotBlank() } ?: state.selectedTitle.orEmpty()
                    val egaisLoaded = remaining.egaisImportedAt != null

                    OsvoenieCard(
                        osvoenie = osvoenieOf(remaining.grouped.orEmpty().values),
                        delyankaLabel = delyankaLabel,
                        vydely = remaining.vydely.orEmpty().filterNotNull(),
                    )

                    val unresolved = remaining.unresolvedSklady.orEmpty()
                    if (unresolved.isNotEmpty()) {
                        WarningBanner(
                            "Не учтено в ЕГАИС: склад не привязан к делянке — " +
                                unresolved.joinToString("; ") { it.sklad } +
                                ". Привяжите в вебе: Расход → Разбор ЕГАИС → Склады ЕГАИС.",
                            tone = BannerTone.WARNING,
                        )
                    }

                    remaining.grouped?.forEach { (poroda, group) ->
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PorodaCard(poroda, group, delyankaLabel, egaisLoaded)
                            SpeciesTotalFooter(group, egaisLoaded)
                        }
                    }

                    Column(modifier = Modifier.padding(bottom = Spacing.xl, top = Spacing.s)) {
                        remaining.lastUpdate?.let {
                            Text(
                                "Наряды обновлены: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        remaining.egaisImportedAt?.let {
                            Text(
                                "ЕГАИС загружен: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Уровень освоения из ответа сервера (level); старый сервер без level — считаем по pct. */
private fun levelOfWire(level: String?, pct: Double?): OsvoenieLevel = when (level) {
    "norma" -> OsvoenieLevel.NORMA
    "vnimanie" -> OsvoenieLevel.VNIMANIE
    "preduprezhdenie" -> OsvoenieLevel.PREDUPREZHDENIE
    "pererub" -> OsvoenieLevel.PERERUB
    "net_limita" -> OsvoenieLevel.NET_LIMITA
    else -> levelOf(pct)
}

private fun Double?.fmt(): String = if (this == null) "—" else String.format(Locale.US, "%.2f", this)

private fun Double.pct(): String = String.format(Locale.US, "%.1f", this)

/** Пороги как на сервере (raskhod_v2.OSVOENIE_POROG_*): % от лимита по большему из фактов наряд/ЕГАИС. */
private const val POROG_VNIMANIE = 90.0
private const val POROG_PREDUPREZHDENIE = 100.0
private const val POROG_PERERUB = 110.0

private enum class OsvoenieLevel(val text: String) {
    NORMA("в пределах лимита"),
    VNIMANIE("подходит к лимиту"),
    PREDUPREZHDENIE("лимит выбран — дальше только в допуске +10%"),
    PERERUB("превышен допуск +10%"),
    NET_LIMITA("лимит не задан"),
}

private fun levelOf(pct: Double?): OsvoenieLevel = when {
    pct == null -> OsvoenieLevel.NET_LIMITA
    pct > POROG_PERERUB + 1e-9 -> OsvoenieLevel.PERERUB
    pct >= POROG_PREDUPREZHDENIE -> OsvoenieLevel.PREDUPREZHDENIE
    pct >= POROG_VNIMANIE -> OsvoenieLevel.VNIMANIE
    else -> OsvoenieLevel.NORMA
}

/** Освоение по всей делянке: все породы, деловая + дрова. faktEgais == null — ЕГАИС не загружен. */
private data class Osvoenie(val limit: Double, val faktNaryad: Double, val faktEgais: Double?) {
    /** Больший из фактов — чтобы не перерубить ни по нарядам, ни по ЕГАИС. */
    val fakt: Double get() = maxOf(faktNaryad, faktEgais ?: 0.0)
    fun pctOf(value: Double): Double? = if (limit > 0) value / limit * 100 else null
    val pct: Double? get() = pctOf(fakt)
    val mozhnoDo100: Double get() = limit - fakt
    val mozhnoDo110: Double get() = limit * 1.1 - fakt
    val level: OsvoenieLevel get() = levelOf(pct)
}

private fun osvoenieOf(groups: Collection<PorodaRemainingDto>): Osvoenie {
    val volumes = groups.flatMap { listOfNotNull(it.delovaya, it.drova) }
    val egaisLoaded = volumes.any { it.faktEgais != null }
    return Osvoenie(
        limit = volumes.sumOf { it.limit ?: 0.0 },
        faktNaryad = volumes.sumOf { it.faktNaryad ?: 0.0 },
        faktEgais = if (egaisLoaded) volumes.sumOf { it.faktEgais ?: 0.0 } else null,
    )
}

private enum class BannerTone { WARNING, ERROR, INFO }

@Composable
private fun levelColors(level: OsvoenieLevel): Pair<Color, Color> = when (level) {
    OsvoenieLevel.PERERUB -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    OsvoenieLevel.PREDUPREZHDENIE -> MaterialTheme.semanticColors.preduprezhdenieContainer to MaterialTheme.semanticColors.onPreduprezhdenie
    OsvoenieLevel.VNIMANIE -> MaterialTheme.semanticColors.vnimanieContainer to MaterialTheme.semanticColors.onVnimanie
    OsvoenieLevel.NORMA -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    OsvoenieLevel.NET_LIMITA -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Карточка «Освоение делянки»: % выбранного лимита по всей делянке, полосы Наряд/ЕГАИС
 * с отметками 90/100/110%, сколько ещё можно заготовить до 100% и 110%, предупреждение
 * и прикидка «если заготовить ещё N м³».
 */
@Composable
private fun OsvoenieCard(osvoenie: Osvoenie, delyankaLabel: String, vydely: List<String>) {
    val level = osvoenie.level
    val fg = levelColors(level).second
    var plan by remember(osvoenie) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .softCard(MaterialTheme.shapes.medium)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Освоение делянки", style = MaterialTheme.typography.titleMedium)
                val subtitle = buildList {
                    if (delyankaLabel.isNotBlank()) add(delyankaLabel)
                    if (vydely.size > 1) add("выделы: ${vydely.joinToString(", ")}")
                }.joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    osvoenie.pct?.let { "${it.pct()}%" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
                Text("лимита выбрано", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OsvoenieBar("Наряд", osvoenie.faktNaryad, osvoenie.limit, MaterialTheme.colorScheme.primary)
        if (osvoenie.faktEgais != null) {
            OsvoenieBar("ЕГАИС", osvoenie.faktEgais, osvoenie.limit, MaterialTheme.colorScheme.tertiary)
        } else {
            Text("ЕГАИС ещё не загружен — процент только по нарядам", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(
            "Лимит ${osvoenie.limit.fmt()} м³  ·  −10%: ${(osvoenie.limit * 0.9).fmt()}  ·  +10%: ${(osvoenie.limit * 1.1).fmt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
            RemainderBox("Можно ещё до 100%", osvoenie.mozhnoDo100, Modifier.weight(1f))
            RemainderBox("Можно ещё до 110%", osvoenie.mozhnoDo110, Modifier.weight(1f))
        }

        when (level) {
            OsvoenieLevel.PERERUB -> WarningBanner(
                "Заготовлено ${osvoenie.pct?.pct()}% лимита — допуск +10% превышен на ${(-osvoenie.mozhnoDo110).fmt()} м³.",
                tone = BannerTone.ERROR,
            )
            OsvoenieLevel.PREDUPREZHDENIE, OsvoenieLevel.VNIMANIE -> WarningBanner(
                "Заготовлено ${osvoenie.pct?.pct()}% лимита. Чтобы не выйти за 110%, можно заготовить не больше " +
                    "${osvoenie.mozhnoDo110.coerceAtLeast(0.0).fmt()} м³.",
                tone = BannerTone.WARNING,
            )
            else -> Unit
        }

        if (osvoenie.limit > 0) {
            OutlinedTextField(
                value = plan,
                onValueChange = { plan = it },
                label = { Text("Прикинуть: заготовить ещё, м³") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
            val planValue = plan.replace(',', '.').toDoubleOrNull()
            if (planValue != null) {
                val newPct = (osvoenie.fakt + planValue) / osvoenie.limit * 100
                val newLevel = levelOf(newPct)
                val (planBg, planFg) = levelColors(newLevel)
                Text(
                    "После этого будет ${newPct.pct()}% лимита — ${newLevel.text}" +
                        if (newLevel == OsvoenieLevel.PERERUB) {
                            ". Лишнее: ${(osvoenie.fakt + planValue - osvoenie.limit * 1.1).fmt()} м³"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = planFg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(planBg, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
        }

        Text(
            "Процент — по большему из фактов (наряд или ЕГАИС), чтобы не перерубить ни по бумагам, ни по ЕГАИС.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Полоса «Наряд»/«ЕГАИС» по шкале до 120% лимита с отметками 90/100/110%. */
@Composable
private fun OsvoenieBar(label: String, value: Double, limit: Double, fillColor: Color) {
    val scale = limit * 1.2
    val fraction = if (scale > 0) (value / scale).toFloat().coerceIn(0f, 1f) else 0f
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label ${value.fmt()} / ${limit.fmt()} м³", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (limit > 0) "${(value / limit * 100).pct()}%" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(fillColor, RoundedCornerShape(4.dp)),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                listOf(0.9f, 1.0f, 1.1f).forEach { k ->
                    val x = size.width * (k / 1.2f)
                    drawLine(
                        color = if (k == 1.1f) errorColor else markColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemainderBox(label: String, value: Double, modifier: Modifier = Modifier) {
    val color = if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text("${value.fmt()} м³", style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun WarningBanner(text: String, tone: BannerTone) {
    val (bg, fg) = when (tone) {
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        BannerTone.WARNING -> MaterialTheme.semanticColors.preduprezhdenieContainer to MaterialTheme.semanticColors.onPreduprezhdenie
        BannerTone.INFO -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Карточка породы: заголовок + секции «Деловая древесина» / «Дрова» (мокап variant-A). */
@Composable
private fun PorodaCard(poroda: String, group: PorodaRemainingDto, delyankaLabel: String, egaisLoaded: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .softCard(MaterialTheme.shapes.medium)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(poroda, style = MaterialTheme.typography.titleMedium)
            if (delyankaLabel.isNotBlank()) {
                Text(
                    delyankaLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        group.delovaya?.let { WoodSection("Деловая древесина", it, egaisLoaded) }
        if (group.delovaya != null && group.drova != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        group.drova?.let { WoodSection("Дрова", it, egaisLoaded) }
    }
}

/**
 * Секция объёма (Деловая древесина / Дрова): остаток по наряду и по ЕГАИС отдельно (как колонки
 * «остаток, наряд» / «остаток, ЕГАИС» на вебе), кольцо % выбранного лимита, полосы Наряд/ЕГАИС,
 * баннер расхождения.
 */
@Composable
private fun WoodSection(label: String, volume: VolumeBreakdownDto, egaisLoaded: Boolean) {
    val limit = volume.limit ?: 0.0
    val naryad = volume.faktNaryad ?: 0.0
    val egais = volume.faktEgais
    val usedPercent = if (limit > 0) ((maxOf(naryad, egais ?: 0.0) / limit) * 100).roundToInt() else null

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            RemainderPanel("Остаток по наряду", limit = limit, fakt = naryad, modifier = Modifier.weight(1f))
            if (egais != null) {
                RemainderPanel("Остаток по ЕГАИС", limit = limit, fakt = egais, modifier = Modifier.weight(1f))
            }
            if (usedPercent != null) {
                UsedRing(percent = usedPercent)
            }
        }
        if (egais == null && !egaisLoaded) {
            Text("ЕГАИС ещё не загружен", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ProgressBarRow("Наряд", current = naryad, limit = limit, fillColor = MaterialTheme.colorScheme.primary)
            if (egais != null) {
                ProgressBarRow("ЕГАИС", current = egais, limit = limit, fillColor = MaterialTheme.colorScheme.tertiary)
            }
        }

        if (egais != null && egais > naryad + 0.005) {
            DiscrepancyBanner(diff = egais - naryad)
        }
    }
}

/**
 * Остаток (лимит − факт) + коридор допуска ±10%. Допуск считается от ЛИМИТА: «−10%» = лимит×0.9 − факт,
 * «+10%» = лимит×1.1 − факт — как на вебе. Раньше здесь было остаток×0.9 / остаток×1.1, и при перерубе
 * (отрицательный остаток) «−10%» получалось больше «+10%».
 */
@Composable
private fun RemainderPanel(title: String, limit: Double, fakt: Double, modifier: Modifier = Modifier) {
    val ostatok = limit - fakt
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                ostatok.fmt(),
                style = MaterialTheme.typography.titleLarge,
                color = if (ostatok < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                "м³",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        Text(
            "−10%: ${(limit * 0.9 - fakt).fmt()}\n+10%: ${(limit * 1.1 - fakt).fmt()} м³",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** Кольцевая диаграмма: сколько % лимита выбрано (по большему из фактов наряд/ЕГАИС). */
@Composable
private fun UsedRing(percent: Int, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = if (percent > POROG_PERERUB) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val textColor = progressColor

    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 7.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

/** Строка прогресс-бара «Наряд» / «ЕГАИС»: текущее/цель м³ + цветная полоса. */
@Composable
private fun ProgressBarRow(label: String, current: Double, limit: Double, fillColor: Color) {
    val fraction = if (limit > 0) (current / limit).toFloat().coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${current.fmt()} / ${limit.fmt()} м³",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(fillColor, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** Баннер расхождения ЕГАИС/Наряда — та же логика (egaisExceedsNaryad), стиль под мокап variant-A. */
@Composable
private fun DiscrepancyBanner(diff: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "ЕГАИС превышает наряд на ${diff.fmt()} м³",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** SpeciesTotalFooter: тёмная (primary) карточка с итогом по породе — остаток по наряду и по ЕГАИС, допуск ±10% от лимита. */
@Composable
private fun SpeciesTotalFooter(group: PorodaRemainingDto, egaisLoaded: Boolean) {
    val volumes = listOfNotNull(group.delovaya, group.drova)
    val limit = volumes.sumOf { it.limit ?: 0.0 }
    val naryad = volumes.sumOf { it.faktNaryad ?: 0.0 }
    val egais = if (egaisLoaded) volumes.sumOf { it.faktEgais ?: 0.0 } else null
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("Итого по породе (лимит ${limit.fmt()} м³)", style = MaterialTheme.typography.labelSmall, color = onPrimary.copy(alpha = 0.75f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            TotalColumn("Остаток по наряду", limit, naryad, onPrimary)
            if (egais != null) TotalColumn("Остаток по ЕГАИС", limit, egais, onPrimary)
        }
    }
}

@Composable
private fun TotalColumn(title: String, limit: Double, fakt: Double, onPrimary: Color) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = onPrimary.copy(alpha = 0.75f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text((limit - fakt).fmt(), style = MaterialTheme.typography.titleLarge, color = onPrimary)
            Text("м³", style = MaterialTheme.typography.labelLarge, color = onPrimary.copy(alpha = 0.85f))
        }
        Text(
            "−10%: ${(limit * 0.9 - fakt).fmt()} · +10%: ${(limit * 1.1 - fakt).fmt()}",
            style = MaterialTheme.typography.labelSmall,
            color = onPrimary.copy(alpha = 0.75f),
        )
    }
}
