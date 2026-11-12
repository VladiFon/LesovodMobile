package com.lesovod.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Токены редизайна 12.11.2026 (прототип «Лесовод», design_handoff_lesovod/README.md).
 * Имена — как в прототипе: bg, sf/sf2/sf3 (поверхности), ln (линии), tx/tx2 (текст),
 * g (зелёные кнопки), gT (зелёный текст), gS (мягкий зелёный фон), a/aT/aS — янтарь,
 * r/rT/rS — красный, ov — плотная подложка поверх карты, nav/ind — нижняя панель.
 * Состояние всегда показывается цветом + значком + словом.
 */
@Immutable
data class LvColors(
    val bg: Color,
    val sf: Color,
    val sf2: Color,
    val sf3: Color,
    val ln: Color,
    val tx: Color,
    val tx2: Color,
    val g: Color,
    val gT: Color,
    val gS: Color,
    val a: Color,
    val aT: Color,
    val aS: Color,
    val r: Color,
    val rT: Color,
    val rS: Color,
    val ov: Color,
    val nav: Color,
    val ind: Color,
    val dark: Boolean,
)

val LvLight = LvColors(
    bg = Color(0xFFFBFAF5),
    sf = Color(0xFFFFFFFF),
    sf2 = Color(0xFFF0EEE5),
    sf3 = Color(0xFFE4E1D6),
    ln = Color(0x241E1D19),
    tx = Color(0xFF1E1D19),
    tx2 = Color(0xFF4A473F),
    g = Color(0xFF2E7D4F),
    gT = Color(0xFF2E7D4F),
    gS = Color(0x1F2E7D4F),
    a = Color(0xFFF2A93B),
    aT = Color(0xFF8F5800),
    aS = Color(0x33F2A93B),
    r = Color(0xFFC33B2A),
    rT = Color(0xFFC33B2A),
    rS = Color(0x1AC33B2A),
    ov = Color(0xF5FBFAF5),
    nav = Color(0xFFF3F1E8),
    ind = Color(0x292E7D4F),
    dark = false,
)

val LvDark = LvColors(
    bg = Color(0xFF1A1916),
    sf = Color(0xFF26251F),
    sf2 = Color(0xFF33312A),
    sf3 = Color(0xFF403E35),
    ln = Color(0x24F3F1EA),
    tx = Color(0xFFF3F1EA),
    tx2 = Color(0xFFCFCABB),
    g = Color(0xFF2E7D4F),
    gT = Color(0xFF6CC490),
    gS = Color(0x296CC490),
    a = Color(0xFFF5B04A),
    aT = Color(0xFFF7BE63),
    aS = Color(0x29F5B04A),
    r = Color(0xFFC33B2A),
    rT = Color(0xFFF07A66),
    rS = Color(0x29F07A66),
    ov = Color(0xF21E1D19),
    nav = Color(0xFF211F1B),
    ind = Color(0x386CC490),
    dark = true,
)

/** Режим «Солнце» (12.11.2026): белый фон, чёрный текст, тёмный зелёный, плотные линии. */
val LvSun = LvColors(
    bg = Color(0xFFFFFFFF),
    sf = Color(0xFFFFFFFF),
    sf2 = Color(0xFFEBEBEB),
    sf3 = Color(0xFFD6D6D6),
    ln = Color(0x73000000),
    tx = Color(0xFF000000),
    tx2 = Color(0xFF1F1F1F),
    g = Color(0xFF145A2E),
    gT = Color(0xFF145A2E),
    gS = Color(0x33145A2E),
    a = Color(0xFFE08A00),
    aT = Color(0xFF5C3700),
    aS = Color(0x40E08A00),
    r = Color(0xFFB0170B),
    rT = Color(0xFFB0170B),
    rS = Color(0x26B0170B),
    ov = Color(0xFFFFFFFF),
    nav = Color(0xFFFFFFFF),
    ind = Color(0x40145A2E),
    dark = false,
)

val LocalLv = staticCompositionLocalOf { LvDark }

/** MaterialTheme.lv.gT и т.п. — токены прототипа. */
val MaterialTheme.lv: LvColors
    @Composable @ReadOnlyComposable get() = LocalLv.current
