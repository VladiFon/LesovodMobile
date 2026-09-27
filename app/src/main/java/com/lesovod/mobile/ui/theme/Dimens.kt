package com.lesovod.mobile.ui.theme

import androidx.compose.ui.unit.dp

/** Шкала отступов 4-8-12-16-24-32-40 и тап-зоны — docs/DESIGN_TOKENS.md редизайна «Поляна». */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp

    /** Поля экрана слева/справа */
    val screenPadding = l
    /** Между секциями на экране */
    val sectionGap = xl
    /** Между соседними карточками в списке */
    val cardGap = m
    /** Внутренние отступы карточки */
    val cardPadding = l
}

object Dimens {
    /** Основная кнопка экрана — 56dp, единственная заливка `tertiary` на экран */
    val tapMain = 56.dp
    /** Минимальная тап-зона для всего остального интерактивного */
    val tapMin = 48.dp
    /** Высота строки списка */
    val rowHeight = 64.dp
    val rowHeightTall = 68.dp
    /** Минимальный зазор между разрушающим и основным действием */
    val gapDestructive = 16.dp
    /** Плитки быстрых действий */
    val quickActionTile = 88.dp
    /** Круглая кнопка отметки присутствия */
    val attendanceButton = 96.dp
}
