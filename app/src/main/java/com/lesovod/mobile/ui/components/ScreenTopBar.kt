package com.lesovod.mobile.ui.components

import androidx.compose.runtime.Composable

/**
 * Заголовок экрана без кнопки «назад». С 12.11.2026 — та же шапка, что и в редизайне
 * ([LvTopBar]): 64 dp, Unbounded 20 и кнопка «?», если передан текст подсказки.
 */
@Composable
fun ScreenTitle(title: String, help: String? = null, onBack: (() -> Unit)? = null) {
    LvTopBar(title = title, help = help, onBack = onBack)
}
