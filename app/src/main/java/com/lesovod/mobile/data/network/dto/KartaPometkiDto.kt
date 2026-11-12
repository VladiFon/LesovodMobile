package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Пометки лесосек на картах (сервер vse83+, app/karta_pometki.py): «Скрыть с карт» —
 * лесосека не рисуется ни в телефоне, ни в QGIS; вернуть — на сайте (Настройки → Карты)
 * или в QGIS. Ставить могут мастер, помощник лесничего, лесничий и контора.
 */
@Serializable
data class KartaPometkaRequest(
    val istochnik: String,
    val klyuchi: List<String>,
    val skryta: Boolean? = null,
)

/** GET /api/karta/pometki — телефону нужен только флаг «можно скрывать». */
@Serializable
data class KartaPometkiDto(
    val mozhno: Boolean = false,
)
