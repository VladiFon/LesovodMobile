package com.lesovod.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LesovodColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = ForestOnPrimary,
    secondary = ForestAccent,
    onSecondary = ForestOnPrimary,
    tertiary = ForestSuccess,
    onTertiary = ForestOnPrimary,
    error = ForestError,
    onError = ForestOnPrimary,
    background = ForestBackground,
    onBackground = ForestOnBackground,
    surface = ForestSurface,
    onSurface = ForestOnSurface,
    surfaceVariant = ForestBackground,
    onSurfaceVariant = ForestOnBackground,
    outline = ForestOutline,
)

@Composable
fun LesovodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LesovodColorScheme,
        typography = LesovodTypography,
        content = content,
    )
}
