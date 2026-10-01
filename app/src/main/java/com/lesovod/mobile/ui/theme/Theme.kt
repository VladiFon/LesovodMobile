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
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) PolyanaDarkColors else PolyanaLightColors
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalIsDarkTheme provides darkTheme,
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
