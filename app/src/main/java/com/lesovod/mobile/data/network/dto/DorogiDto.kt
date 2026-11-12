package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Лесная дорога (сервер vse81+, GET /api/dorogi). Видят все, рисуют мастер, помощник
 * лесничего, лесничий и контора — флаг [DorogiListDto.mozhnoRisovat].
 * Точки — [[lon, lat], …], как в GeoJSON.
 */
@Serializable
data class DorogaDto(
    val id: Int,
    val nazvanie: String? = null,
    val tip: String = "gruntovka",
    @SerialName("tip_label") val tipLabel: String? = null,
    val proezd: List<String> = emptyList(),
    @SerialName("proezd_labels") val proezdLabels: List<String> = emptyList(),
    val tochki: List<List<Double>> = emptyList(),
    @SerialName("dlina_m") val dlinaM: Double? = null,
    val lesnichestvo: String? = null,
    val kommentariy: String? = null,
    @SerialName("sozdal_fio") val sozdalFio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("izmenil_fio") val izmenilFio: String? = null,
)

@Serializable
data class DorogiListDto(
    val dorogi: List<DorogaDto> = emptyList(),
    @SerialName("mozhno_risovat") val mozhnoRisovat: Boolean = false,
)

@Serializable
data class DorogaCreateRequest(
    val nazvanie: String? = null,
    val tip: String,
    val proezd: List<String>,
    val tochki: List<List<Double>>,
    val lesnichestvo: String? = null,
    val kommentariy: String? = null,
)
