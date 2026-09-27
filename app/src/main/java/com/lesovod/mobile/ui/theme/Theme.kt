package com.lesovod.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Точка входа темы «Поляна» (редизайн, docs/DESIGN_TOKENS.md). Динамические цвета
 * Material You сознательно не подключаются — палитра фиксирована ради проверенного
 * контраста. Экраны, ещё не переписанные под новые компоненты, продолжат
 * компилироваться и работать: они получат новые цвета/шрифты через
 * MaterialTheme.colorScheme/typography автоматически, но места, где использованы
 * литералы Forest* напрямую, сохранят старый вид до переезда на «Поляну».
 */
@Composable
fun LesovodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) PolyanaDarkColors else PolyanaLightColors
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
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
