package com.lesovod.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import com.lesovod.mobile.ui.theme.Unbounded

/*
 * Общие детали редизайна 12.11.2026 (прототип «Лесовод»): одинаковая шапка с «?»,
 * большая кнопка внизу, подписи секций, статус отправки, плитки, поиск, пустое состояние.
 */

/** Шапка экрана 64 dp: [назад] [заголовок] [доп. кнопки] [«?» — нижняя карточка «Что тут делают»]. */
@Composable
fun LvTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    help: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val lv = LocalLv.current
    var showHelp by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = lv.tx, modifier = Modifier.size(28.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
            color = lv.tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        if (help != null) {
            HelpButton(onClick = { showHelp = true })
        }
    }
    if (showHelp && help != null) {
        HelpSheet(text = help, onDismiss = { showHelp = false })
    }
}

/** Кнопка «?»: круг 32 с обводкой 2.5 в зоне 56. */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lv = LocalLv.current
    Box(
        modifier = modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(32.dp).border(2.5.dp, lv.tx, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp))
        }
    }
}

/** Нижняя карточка «Что тут делают» + «Понятно». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(text: String, onDismiss: () -> Unit, title: String = "Что тут делают") {
    val lv = LocalLv.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = lv.sf,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(title, color = lv.tx, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp))
            Text(
                text,
                color = lv.tx,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            )
            LvBigButton(text = "Понятно", onClick = onDismiss)
        }
    }
}

/** Главная кнопка экрана: высота 64, скругление 20, на всю ширину. */
@Composable
fun LvBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = LocalLv.current.g,
    content: Color = Color.White,
    enabled: Boolean = true,
    height: Int = 64,
) {
    val lv = LocalLv.current
    val bg = if (enabled) container else lv.sf2
    val fg = if (enabled) content else lv.tx2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, maxLines = 2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp))
    }
}

/** Вторая кнопка — на подложке sf2. */
@Composable
fun LvSoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Int = 64,
    enabled: Boolean = true,
) {
    val lv = LocalLv.current
    LvBigButton(text = text, onClick = onClick, modifier = modifier, icon = icon, container = lv.sf2, content = lv.tx, enabled = enabled, height = height)
}

/** Подпись секции капсом: Manrope 700 15, разрядка .04em, цвет tx2. */
@Composable
fun LvSectionLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val lv = LocalLv.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = lv.gT, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            color = lv.tx2,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.04.em),
        )
    }
}

enum class SendKind { SENT, QUEUED, ERROR }

/** Единый статус отправки: Отправлено / Ждёт сети / Ошибка. */
@Composable
fun LvStatusBadge(kind: SendKind, modifier: Modifier = Modifier, text: String? = null) {
    val lv = LocalLv.current
    val (icon, bg, fg, label) = when (kind) {
        SendKind.SENT -> Quad(Icons.Rounded.CloudDone, lv.gS, lv.gT, "Отправлено")
        SendKind.QUEUED -> Quad(Icons.Rounded.Schedule, lv.aS, lv.aT, "Ждёт сети")
        SendKind.ERROR -> Quad(Icons.Rounded.Error, lv.rS, lv.rT, "Ошибка")
    }
    Row(
        modifier = modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text ?: label, color = fg, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp))
    }
}

private data class Quad(val icon: ImageVector, val bg: Color, val fg: Color, val label: String)

/** Плитка раздела: высота 104, значок 44 в квадрате r14, подпись снизу. */
@Composable
fun LvTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconBg: Color = LocalLv.current.gS,
    iconTint: Color = LocalLv.current.gT,
    badge: Int = 0,
) {
    val lv = LocalLv.current
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(lv.sf)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp)) }
            if (badge > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(start = 30.dp).size(24.dp).clip(CircleShape).background(lv.r),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge.coerceAtMost(99).toString(), color = Color.White, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp))
                }
            }
        }
        Text(
            label,
            color = lv.tx,
            maxLines = 3,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Поле поиска 56: значок лупы, подсказка, крестик очистки. */
@Composable
fun LvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val lv = LocalLv.current
    val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = lv.tx)
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(lv.sf2).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = lv.tx2, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = lv.tx2))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(lv.gT),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Очистить", tint = lv.tx2)
            }
        }
    }
}

/** Пустое состояние: значок в круге 96, заголовок, человеческое объяснение, кнопка действия. */
@Composable
fun LvEmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val lv = LocalLv.current
    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(lv.sf2), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = lv.tx2, modifier = Modifier.size(48.dp))
        }
        Text(title, color = lv.tx, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp), modifier = Modifier.padding(top = 16.dp))
        Text(
            text,
            color = lv.tx2,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (action != null) LvBigButton(text = action, onClick = onAction)
    }
}
