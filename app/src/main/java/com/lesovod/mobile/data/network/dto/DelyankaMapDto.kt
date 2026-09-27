package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Строка GET /api/delyanki/for-map: где на карте уже заведена делянка (по кварталу и выделу). */
@Serializable
data class DelyankaMapRefDto(
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("delyanka_id") val delyankaId: Int,
    val nazvanie: String? = null,
    @SerialName("status_rabot") val statusRabot: String? = null,
)

/** Ответ GET /api/map/delyanka-location — координаты центроида выдела для поиска на карте. */
@Serializable
data class VydelLocationDto(
    val found: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
)
