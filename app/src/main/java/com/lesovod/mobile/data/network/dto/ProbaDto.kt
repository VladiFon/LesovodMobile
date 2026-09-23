package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProbaRowRequest(
    val poroda: String,
    val shirina: Double,
    val vysota: Double,
    val dlina: Double,
)

@Serializable
data class ProbaFormRequest(
    @SerialName("kol_ploshadok") val kolPloshadok: Int,
    @SerialName("ploshad_ploshadki") val ploshadPloshadki: Double,
)

/** Тело POST /api/uhody/proby — бумажные поля form (lesnichestvo, sostav и т.д.) на телефоне не собираем. */
@Serializable
data class ProbaSaveRequest(
    val kvartal: String,
    val vydel: String,
    @SerialName("ploshad_vydela") val ploshadVydela: Double? = null,
    @SerialName("data_zamera") val dataZamera: String,
    val rows: List<ProbaRowRequest>,
    val form: ProbaFormRequest,
    @SerialName("lesokultury_uchastok_ids") val lesokulturyUchastokIds: List<Int> = emptyList(),
    @SerialName("foto_stolb_delyanki") val fotoStolbDelyanki: String? = null,
    @SerialName("foto_stolb_proby") val fotoStolbProby: String? = null,
)

@Serializable
data class ProbaRowResponse(
    val poroda: String,
    val shirina: Double,
    val vysota: Double,
    val dlina: Double,
    @SerialName("obyom_sklad") val obyom: Double? = null,
)

@Serializable
data class ProbaResponse(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    @SerialName("ploshad_vydela") val ploshadVydela: Double? = null,
    @SerialName("data_zamera") val dataZamera: String? = null,
    val rows: List<ProbaRowResponse> = emptyList(),
    @SerialName("obyom_sklad_total") val obyomSkladTotal: Double? = null,
    @SerialName("zapas_proby_total") val zapasProbyTotal: Double? = null,
    @SerialName("zapas_na_1ga") val zapasNa1Ga: Double? = null,
    @SerialName("zapas_na_lesoseke") val zapasNaLesoseke: Double? = null,
)
