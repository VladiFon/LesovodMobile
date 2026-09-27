package com.lesovod.mobile.ui.kubaturnik

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.data.local.TilesSide
import com.lesovod.mobile.ui.theme.LesovodTheme

/**
 * Границы «ходового» диапазона диаметров (зона большого пальца, средние ряды сетки) — вынесены
 * в константы по требованию ТЗ, чтобы при необходимости их можно было сделать настраиваемыми.
 */
const val HOT_MIN = 24
const val HOT_MAX = 40

private val TileGap = 5.dp
private val TileMinSize = 48.dp
private val TileCorner = 14.dp

/**
 * Тело вкладки «Счёт»: 50/50 — сетка плиток диаметра и справочная панель. [tilesSide] определяет,
 * какая половина стоит у края экрана; порядок половин собирается в одном месте, чтобы не дублировать
 * код грид/панели для LEFT и RIGHT.
 */
@Composable
fun ScoreBody(
    tilesSide: TilesSide,
    diameters: List<Int>,
    counts: Map<Int, Int>,
    lastTapped: Int?,
    onTap: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    panel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid: @Composable () -> Unit = {
        DiameterGrid(
            diameters = diameters,
            counts = counts,
            lastTapped = lastTapped,
            onTap = onTap,
            onUndo = onUndo,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Row(modifier = modifier.fillMaxSize()) {
        val half = Modifier.weight(1f).fillMaxHeight()
        if (tilesSide == TilesSide.LEFT) {
            Box(half) { grid() }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(half) { panel() }
        } else {
            Box(half) { panel() }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(half) { grid() }
        }
    }
}

/**
 * Сетка плиток 3 колонки, по возрастанию диаметра слева направо, сверху вниз. Когда доступной
 * высоты хватает, чтобы каждый ряд был не меньше 48dp, ряды занимают всю высоту без прокрутки
 * (весовая раскладка, как в макете). Если диаметров слишком много (например, шаг 1 см), сетка
 * переходит на прокручиваемый `LazyVerticalGrid`, но плитки не становятся меньше 48×48dp.
 */
@Composable
fun DiameterGrid(
    diameters: List<Int>,
    counts: Map<Int, Int>,
    lastTapped: Int?,
    onTap: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowCount = (diameters.size + 2) / 3
    BoxWithConstraints(modifier = modifier) {
        val gapTotal = TileGap * (rowCount - 1).coerceAtLeast(0)
        val available = maxHeight - 12.dp - gapTotal
        val rowHeight = if (rowCount > 0) available / rowCount else TileMinSize

        if (rowCount == 0 || rowHeight >= TileMinSize) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                diameters.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TileGap),
                    ) {
                        row.forEach { diameter ->
                            DiameterTile(
                                diameter = diameter,
                                count = counts[diameter] ?: 0,
                                isLast = diameter == lastTapped,
                                onTap = { onTap(diameter) },
                                onUndo = { onUndo(diameter) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
                verticalArrangement = Arrangement.spacedBy(TileGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(diameters, key = { it }) { diameter ->
                    DiameterTile(
                        diameter = diameter,
                        count = counts[diameter] ?: 0,
                        isLast = diameter == lastTapped,
                        onTap = { onTap(diameter) },
                        onUndo = { onUndo(diameter) },
                        modifier = Modifier
                            .heightIn(min = TileMinSize)
                            .aspectRatio(1f),
                    )
                }
            }
        }
    }
}

/**
 * Одна плитка диаметра. Тап = +1 (лёгкий тактильный отклик), долгий тап (~550мс, через
 * `combinedClickable`) = отмена последнего бревна этого диаметра (более сильный отклик).
 * Ходовые диаметры (см. [HOT_MIN]/[HOT_MAX]) крупнее и заметнее, редкие — приглушены.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiameterTile(
    diameter: Int,
    count: Int,
    isLast: Boolean,
    onTap: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val filled = count > 0
    val hot = diameter in HOT_MIN..HOT_MAX

    val backgroundColor = when {
        filled -> MaterialTheme.colorScheme.primary
        hot -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        filled -> MaterialTheme.colorScheme.onPrimary
        hot -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.95f else 1f, label = "diameterTileScale")

    val shape = RoundedCornerShape(TileCorner)
    var tileModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    if (isLast) {
        // Двойное кольцо: снаружи primary (2dp), внутри — контрастная обводка на фоне заливки (3dp).
        tileModifier = tileModifier
            .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = shape)
            .padding(2.dp)
            .border(width = 3.dp, color = MaterialTheme.colorScheme.onPrimary, shape = shape)
    }
    tileModifier = tileModifier
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap()
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUndo()
            },
        )
        .semantics { contentDescription = "Диаметр $diameter, счёт $count" }

    Surface(
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        modifier = tileModifier,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$diameter",
                    fontSize = if (filled) 14.sp else if (hot) 24.sp else 18.sp,
                    fontWeight = if (filled) FontWeight.Medium else if (hot) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                )
                if (filled) {
                    val counterText = "×$count"
                    Text(
                        text = counterText,
                        fontSize = if (counterText.length >= 4) 26.sp else 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

/**
 * Справочная панель: последний тап, список введённых диаметров и «Комбо». Не интерактивна —
 * только для сверки глазами, весь ввод идёт через сетку плиток.
 */
@Composable
fun ReferencePanel(
    lastTap: Pair<Int, Double>?,
    rows: List<KubaturnikRow>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LastTapCard(lastTap = lastTap)
        DiameterListCard(
            rows = rows,
            lastDiameter = lastTap?.first,
            modifier = Modifier.weight(1f),
        )
        ComboRow(rows = rows)
    }
}

@Composable
private fun LastTapCard(lastTap: Pair<Int, Double>?, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "Последний тап",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = if (lastTap != null) "Ø ${lastTap.first}" else "—",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (lastTap != null) "${lastTap.second.fmt(4)} м³" else "— м³",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DiameterListCard(rows: List<KubaturnikRow>, lastDiameter: Int?, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    "Ø см",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.34f),
                )
                Text(
                    "шт",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.3f),
                )
                Text(
                    "м³",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.36f),
                )
            }

            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Пока пусто",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(rows, key = { it.diameter }) { row ->
                        val highlighted = row.diameter == lastDiameter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                "${row.diameter}",
                                fontSize = 12.sp,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(0.34f),
                            )
                            Text(
                                "${row.count}",
                                fontSize = 12.sp,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.3f),
                            )
                            Text(
                                row.totalVolume.fmt(2),
                                fontSize = 12.sp,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.36f),
                            )
                        }
                    }
                }
            }

            Text(
                "Удерж. плитку: отмена −1",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ComboRow(rows: List<KubaturnikRow>, modifier: Modifier = Modifier) {
    val count = rows.sumOf { it.count }
    val volume = rows.sumOf { it.totalVolume }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Комбо: $count шт · ${volume.fmt(3)} м³",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// ---- Previews: обе стороны плиток × светлая/тёмная тема (критерий готовности №7) ----

private val PreviewDiameters = (12..60 step 2).toList()
private val PreviewCounts = mapOf(16 to 1, 22 to 1, 24 to 2, 26 to 3, 28 to 7, 30 to 5, 32 to 4, 34 to 2, 40 to 1, 50 to 1)
private val PreviewRows = PreviewCounts.entries.sortedBy { it.key }.map { (d, n) ->
    val volumePerLog = (Math.PI / 4) * (d / 100.0) * (d / 100.0) * 4.0
    KubaturnikRow(d, n, volumePerLog, volumePerLog * n)
}

@Composable
private fun ScoreBodyPreview(side: TilesSide, darkTheme: Boolean) {
    LesovodTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScoreBody(
                tilesSide = side,
                diameters = PreviewDiameters,
                counts = PreviewCounts,
                lastTapped = 28,
                onTap = {},
                onUndo = {},
                panel = {
                    ReferencePanel(
                        lastTap = 28 to PreviewRows.first { it.diameter == 28 }.volumePerLog,
                        rows = PreviewRows,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Плитки слева — светлая", widthDp = 390, heightDp = 560, showBackground = true)
@Composable
private fun ScoreBodyPreviewLeftLight() {
    ScoreBodyPreview(side = TilesSide.LEFT, darkTheme = false)
}

@Preview(
    name = "Плитки слева — тёмная",
    widthDp = 390,
    heightDp = 560,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ScoreBodyPreviewLeftDark() {
    ScoreBodyPreview(side = TilesSide.LEFT, darkTheme = true)
}

@Preview(name = "Плитки справа — светлая", widthDp = 390, heightDp = 560, showBackground = true)
@Composable
private fun ScoreBodyPreviewRightLight() {
    ScoreBodyPreview(side = TilesSide.RIGHT, darkTheme = false)
}

@Preview(
    name = "Плитки справа — тёмная",
    widthDp = 390,
    heightDp = 560,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ScoreBodyPreviewRightDark() {
    ScoreBodyPreview(side = TilesSide.RIGHT, darkTheme = true)
}
