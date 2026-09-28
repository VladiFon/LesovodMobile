package com.lesovod.mobile.data.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Где рабочий сейчас по GPS: квартал и (если выделы подгружены) выдел лесничества. */
data class Place(
    val lesnichestvo: String?,
    val kvartal: String,
    val vydel: String?,
    val at: Long = System.currentTimeMillis(),
) {
    val label: String get() = "кв. $kvartal" + (vydel?.let { ", выд. $it" } ?: "")
}

/**
 * Последнее место, определённое картой. Формы (отчёт о работе и т.п.) подставляют отсюда
 * квартал/выдел, если человек сам их не ввёл — меньше ошибок в номерах, из-за которых работа
 * потом не красит выдел на карте.
 */
object CurrentPlace {
    private const val FRESH_MS = 15 * 60 * 1000L

    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()

    fun update(place: Place?) {
        _place.value = place
    }

    /** Место, если оно определено недавно (рабочий не успел уйти далеко). */
    fun fresh(): Place? = _place.value?.takeIf { System.currentTimeMillis() - it.at < FRESH_MS }
}
