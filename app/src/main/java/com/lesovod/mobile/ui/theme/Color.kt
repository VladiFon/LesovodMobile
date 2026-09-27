package com.lesovod.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cyber Forester palette — старая тема, оставлена ради экранов, ещё не переведённых
// на «Поляну» (см. docs/MIGRATION_PLAN.md редизайна); удаляется в фазе уборки, когда
// на неё не останется ссылок.
val ForestBackground = Color(0xFFFCF9F2)
val ForestSurface = Color(0xFFFFFFFF)
val ForestPrimary = Color(0xFF1A4331)
val ForestAccent = Color(0xFFDF964E)
val ForestSuccess = Color(0xFF3A6847)
val ForestError = Color(0xFFBA1A1A)

val ForestOnPrimary = Color(0xFFFFFFFF)
val ForestOnBackground = Color(0xFF1A1C19)
val ForestOnSurface = Color(0xFF1A1C19)
val ForestOutline = Color(0xFFDDD6C7)

/*
 * Палитра «Поляна» (новый редизайн). Значения и обоснование контраста —
 * docs/DESIGN_TOKENS.md в пакете передачи дизайна. Динамические цвета
 * Material You сознательно не используются — палитра фиксирована ради
 * проверенного контраста в поле.
 */

// ---- Светлая тема ----
private val PaperLight = Color(0xFFFBFAF5)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceVariantLight = Color(0xFFF1F6EC)
private val InkLight = Color(0xFF1B2A1E)
private val MutedLight = Color(0xFF5B6B5E)
private val OutlineLight = Color(0xFF8FA391)
private val OutlineVariantLight = Color(0xFFDCE5D6)
private val LeafLight = Color(0xFF2E7D4F)
private val LeafContainerLight = Color(0xFFDCF0E1)
private val SunbeamLight = Color(0xFFF2A93B)
private val OnSunbeamLight = Color(0xFF241A02)
private val ErrorLight = Color(0xFFC33B2A)

private val OkLight = Color(0xFF0E7A4B)
private val WarnContainerLight = Color(0xFFFCE3A8)
private val WarnOnContainerLight = Color(0xFF241A02)
private val SkyLight = Color(0xFF2F6493)

// ---- Тёмная тема («Поляна вечером») ----
private val DuskDark = Color(0xFF1C1B17)
private val SurfaceDark = Color(0xFF242320)
private val SurfaceVariantDark = Color(0xFF2E2C27)
private val InkDark = Color(0xFFF2EFE8)
private val MutedDark = Color(0xFFB7B2A3)
private val OutlineDark = Color(0xFF5B5A50)
private val OutlineVariantDark = Color(0xFF3A382F)
private val LeafDark = Color(0xFF6FCB93)
private val OnLeafDark = Color(0xFF0E2415)
private val LeafContainerDark = Color(0xFF25402F)
private val SunbeamDark = Color(0xFFFFC966)
private val OnSunbeamDark = Color(0xFF2A1B00)
private val ErrorDark = Color(0xFFFF8A75)
private val OnErrorDark = Color(0xFF2A0D08)

private val OkDark = Color(0xFF4FD6A6)
private val OnOkDark = Color(0xFF052B1C)
private val WarnContainerDark = Color(0xFF4A3A14)
private val WarnOnContainerDark = Color(0xFFFFC966)
private val SkyDark = Color(0xFF7FB8EA)
private val OnSkyDark = Color(0xFF04182B)

val PolyanaLightColors = lightColorScheme(
    primary = LeafLight,
    onPrimary = Color.White,
    primaryContainer = LeafContainerLight,
    onPrimaryContainer = InkLight,
    tertiary = SunbeamLight,
    onTertiary = OnSunbeamLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = MutedLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = Color.White,
)

val PolyanaDarkColors = darkColorScheme(
    primary = LeafDark,
    onPrimary = OnLeafDark,
    primaryContainer = LeafContainerDark,
    onPrimaryContainer = InkDark,
    tertiary = SunbeamDark,
    onTertiary = OnSunbeamDark,
    background = DuskDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = MutedDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
)

/** Цвета, которых нет в стандартной M3 ColorScheme: успех, контейнер предупреждения, «небо» (офлайн). */
@Immutable
data class SemanticColors(
    val ok: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val sky: Color,
    val onSky: Color,
)

val LightSemanticColors = SemanticColors(
    ok = OkLight,
    warnContainer = WarnContainerLight,
    onWarnContainer = WarnOnContainerLight,
    sky = SkyLight,
    onSky = Color.White,
)

val DarkSemanticColors = SemanticColors(
    ok = OkDark,
    warnContainer = WarnContainerDark,
    onWarnContainer = WarnOnContainerDark,
    sky = SkyDark,
    onSky = OnSkyDark,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
