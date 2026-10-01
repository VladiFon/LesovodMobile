package com.lesovod.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Статус отправки своей записи (заметка, метка, отчёт): «✓ отправлено» — запись уже на сервере,
 * «⏳ ждёт связи» — лежит в офлайн-очереди и уйдёт сама, когда появится сеть.
 */
@Composable
fun SendStatusChip(pending: Boolean, modifier: Modifier = Modifier) {
    if (pending) {
        StatusChip(text = "⏳ ждёт связи", tone = ChipTone.WARN, modifier = modifier)
    } else {
        StatusChip(text = "✓ отправлено", tone = ChipTone.OK, modifier = modifier)
    }
}
