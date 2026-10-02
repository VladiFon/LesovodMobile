package com.lesovod.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Мягкая рассеянная тень вместо голой контурной рамки M3 по умолчанию — примета
 * визуального языка «Поляны». На ярком солнце тень плохо видна, поэтому карточка
 * дополнительно получает тонкую рамку outlineVariant — тень отвечает за «воздух»,
 * рамка подстраховывает читаемость границ.
 *
 * [filled] — залить карточку цветом surface. В тёмной теме тени почти не видно, и без
 * заливки карточка сливалась с фоном; false — когда фон уже задан снаружи (градиент,
 * подсветка непрочитанного), чтобы его не перекрыть.
 */
@Composable
fun Modifier.softCard(shape: Shape = MaterialTheme.shapes.medium, filled: Boolean = true): Modifier {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val base = this
        .shadow(elevation = 10.dp, shape = shape, clip = false)
        .border(width = 1.dp, color = outline, shape = shape)
    return if (filled) base.background(MaterialTheme.colorScheme.surface, shape) else base
}
