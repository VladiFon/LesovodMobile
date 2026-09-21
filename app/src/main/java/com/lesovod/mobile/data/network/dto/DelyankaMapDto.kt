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
