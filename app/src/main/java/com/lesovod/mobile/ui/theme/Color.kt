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

private val SecondaryLight = Color(0xFF4F6354)
private val SecondaryContainerLight = Color(0xFFD3E8D7)
private val ErrorContainerLight = Color(0xFFFFDAD4)
private val OnErrorContainerLight = Color(0xFF410002)

private val OkLight = Color(0xFF0E7A4B)
private val WarnContainerLight = Color(0xFFFCE3A8)
private val WarnOnContainerLight = Color(0xFF241A02)
private val SkyLight = Color(0xFF2F6493)

// ---- Тёмная тема («Поляна вечером») — тема по умолчанию с версии 0.6.0 ----
// Карточки (surface) заметно светлее фона, а рамки (outlineVariant) различимы — иначе
// в тёмной теме границы карточек терялись и экран выглядел «кривым».
private val DuskDark = Color(0xFF1A1916)
private val SurfaceDark = Color(0xFF26251F)
private val SurfaceVariantDark = Color(0xFF312F29)
private val InkDark = Color(0xFFF2EFE8)
private val MutedDark = Color(0xFFBDB8A9)
private val OutlineDark = Color(0xFF6B695E)
private val OutlineVariantDark = Color(0xFF45433A)
private val SecondaryDark = Color(0xFFA9C7B0)
private val OnSecondaryDark = Color(0xFF15291C)
private val SecondaryContainerDark = Color(0xFF2F4A38)
private val OnSecondaryContainerDark = Color(0xFFDCEFE2)
private val TertiaryContainerDark = Color(0xFF4A3A14)
private val OnTertiaryContainerDark = Color(0xFFFFDDA0)
private val ErrorContainerDark = Color(0xFF4D1D16)
private val OnErrorContainerDark = Color(0xFFFFDAD3)
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
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = InkLight,
    tertiary = SunbeamLight,
    onTertiary = OnSunbeamLight,
    tertiaryContainer = WarnContainerLight,
    onTertiaryContainer = WarnOnContainerLight,
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
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F6F0),
    surfaceContainer = Color(0xFFF2F1EA),
    surfaceContainerHigh = Color(0xFFECEBE4),
    surfaceContainerHighest = Color(0xFFE6E5DE),
    surfaceBright = PaperLight,
    surfaceDim = Color(0xFFE0DFD8),
    inverseSurface = Color(0xFF2A2925),
    inverseOnSurface = Color(0xFFF2EFE8),
    inversePrimary = LeafDark,
)

val PolyanaDarkColors = darkColorScheme(
    primary = LeafDark,
    onPrimary = OnLeafDark,
    primaryContainer = LeafContainerDark,
    onPrimaryContainer = InkDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = SunbeamDark,
    onTertiary = OnSunbeamDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
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
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    // Контейнеры M3 (нижняя панель, диалоги, листы, меню) — без них брались базовые
    // фиолетовые оттенки Material и тёмная тема выглядела чужой.
    surfaceContainerLowest = Color(0xFF141310),
    surfaceContainerLow = Color(0xFF201F1A),
    surfaceContainer = Color(0xFF26251F),
    surfaceContainerHigh = Color(0xFF302E28),
    surfaceContainerHighest = Color(0xFF3A3832),
    surfaceBright = Color(0xFF403E37),
    surfaceDim = DuskDark,
    surfaceTint = SurfaceDark,
    inverseSurface = InkDark,
    inverseOnSurface = Color(0xFF2A2925),
    inversePrimary = LeafLight,
)

/**
 * Цвета, которых нет в стандартной M3 ColorScheme: успех, контейнер предупреждения, «небо» (офлайн)
 * и уровни освоения делянки (90% — «внимание», 100% — «лимит выбран»; переруб >110% — error).
 */
@Immutable
data class SemanticColors(
    val ok: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val sky: Color,
    val onSky: Color,
    val vnimanieContainer: Color,
    val onVnimanie: Color,
    val preduprezhdenieContainer: Color,
    val onPreduprezhdenie: Color,
)

val LightSemanticColors = SemanticColors(
    ok = OkLight,
    warnContainer = WarnContainerLight,
    onWarnContainer = WarnOnContainerLight,
    sky = SkyLight,
    onSky = Color.White,
    vnimanieContainer = Color(0xFFFDF1D6),
    onVnimanie = Color(0xFF7A5200),
    preduprezhdenieContainer = Color(0xFFFDE2CC),
    onPreduprezhdenie = Color(0xFF8A3F00),
)

val DarkSemanticColors = SemanticColors(
    ok = OkDark,
    warnContainer = WarnContainerDark,
    onWarnContainer = WarnOnContainerDark,
    sky = SkyDark,
    onSky = OnSkyDark,
    vnimanieContainer = Color(0xFF3F3413),
    onVnimanie = Color(0xFFFFD679),
    preduprezhdenieContainer = Color(0xFF4A2B10),
    onPreduprezhdenie = Color(0xFFFFB46B),
)

val LocalSemanticColors = staticCompositionLocalOf { DarkSemanticColors }
