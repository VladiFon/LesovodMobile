package com.lesovod.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** true, если сейчас включена тёмная тема — для редких мест, где цвет подбирается вручную (карта, легенды). */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Точка входа темы «Поляна» (редизайн, docs/DESIGN_TOKENS.md). Динамические цвета
 * Material You сознательно не подключаются — палитра фиксирована ради проверенного
 * контраста. С версии 0.6.0 по умолчанию включена тёмная тема независимо от системы;
 * выбор «Тёмная / Светлая / Как в системе» — в Профиле (см. [com.lesovod.mobile.data.local.ThemePrefs]).
 */
@Composable
fun LesovodTheme(
    darkTheme: Boolean = true,
    /** Режим «Солнце»: светлая тема с максимальным контрастом. */
    sun: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        sun -> PolyanaSunColors
        darkTheme -> PolyanaDarkColors
        else -> PolyanaLightColors
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val lv = when {
        sun -> LvSun
        darkTheme -> LvDark
        else -> LvLight
    }

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalIsDarkTheme provides darkTheme,
        LocalLv provides lv,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PolyanaTypography,
            shapes = PolyanaShapes,
            content = content,
        )
    }
}

/** Короткий доступ: MaterialTheme.semanticColors.ok и т.п. */
val MaterialTheme.semanticColors: SemanticColors
    @Composable get() = LocalSemanticColors.current

/** «Солнце»: чистый белый, чёрный текст и тёмные контуры — читается на ярком свету. */
val PolyanaSunColors = PolyanaLightColors.copy(
    primary = androidx.compose.ui.graphics.Color(0xFF145A2E),
    background = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.Black,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDEDED),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    outline = androidx.compose.ui.graphics.Color(0xFF333333),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF666666),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF2F2F2),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF8F8F8),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFEAEAEA),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE2E2E2),
)
