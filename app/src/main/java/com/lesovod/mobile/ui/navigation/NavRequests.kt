package com.lesovod.mobile.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Открой остатки этой делянки» — из карточки делянки на карте или с главного экрана. Маршрут
 * «stock» остаётся без аргументов (его же подсвечивает нижняя панель), а id передаётся здесь:
 * экран остатков забирает его через [consume] и сразу открывает детали делянки.
 */
object StockNavRequest {
    private val _delyankaId = MutableStateFlow<Int?>(null)
    val delyankaId: StateFlow<Int?> = _delyankaId.asStateFlow()

    fun open(delyankaId: Int) {
        _delyankaId.value = delyankaId
    }

    fun consume(): Int? = _delyankaId.value.also { _delyankaId.value = null }
}

/** Что подставить в отчёт о работе при открытии (кнопка «Добавить в отчёт» в Кубатурнике). */
data class WorkReportPrefill(
    /** Объём, м³ — итог партии из Кубатурника. */
    val obyom: Double,
    /** Порода/сорта и длина — уходят в описание отчёта. */
    val opisanie: String,
)

object WorkReportPrefillRequest {
    private val _prefill = MutableStateFlow<WorkReportPrefill?>(null)
    val prefill: StateFlow<WorkReportPrefill?> = _prefill.asStateFlow()

    fun set(prefill: WorkReportPrefill) {
        _prefill.value = prefill
    }

    fun consume(): WorkReportPrefill? = _prefill.value.also { _prefill.value = null }
}
