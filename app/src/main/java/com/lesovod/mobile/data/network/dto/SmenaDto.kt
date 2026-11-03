package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * «Отчёт за смену» (сервер: routers/smena.py, 03.11.2026) — трелёвка, вывозка населению,
 * уход за культурами, подготовка почвы, ремонт. Один отчёт — один вид работы; напарники
 * отмечаются галочками, им вводить не нужно.
 */
@Serializable
data class SmenaVidDto(
    val title: String,
    val emoji: String = "",
    val meropriyatie: String? = null,
)

@Serializable
data class SmenaDelyankaDto(
    @SerialName("item_id") val itemId: Int,
    @SerialName("delyanka_id") val delyankaId: Int? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    val label: String,
    val nedavno: String? = null,
)

/** Незакрытый цикл по участку: сколько уже сделано, сколько осталось. */
@Serializable
data class SmenaCiklDto(
    val sdelano: Double = 0.0,
    val ostatok: Double? = null,
    val otchetov: Int = 0,
    val posledniy: String? = null,
)

@Serializable
data class SmenaUchastokDto(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    val ploshad: Double? = null,
    val label: String,
    val nedavno: String? = null,
    val uhod: SmenaCiklDto? = null,
    val pochva: SmenaCiklDto? = null,
) {
    fun cikl(vid: String): SmenaCiklDto? = when (vid) {
        "uhod" -> uhod
        "pochva" -> pochva
        else -> null
    }
}

@Serializable
data class SmenaNaparnikDto(
    val id: Int,
    val fio: String,
    val dolzhnost: String? = null,
    val raz: Int = 0,
)

@Serializable
data class SmenaSpravochnikDto(
    val vidy: Map<String, SmenaVidDto> = emptyMap(),
    val delyanki: List<SmenaDelyankaDto> = emptyList(),
    val uchastki: List<SmenaUchastokDto> = emptyList(),
    val naparniki: List<SmenaNaparnikDto> = emptyList(),
    val poluchateli: List<String> = emptyList(),
    val tehnika: List<String> = emptyList(),
)

@Serializable
data class SmenaRequest(
    val vid: String,
    val data: String? = null,
    @SerialName("delyanka_item_id") val delyankaItemId: Int? = null,
    @SerialName("lesokultury_uchastok_id") val lesokulturyUchastokId: Int? = null,
    val reysov: Int? = null,
    val obyom: Double? = null,
    val ga: Double? = null,
    val chasy: Double? = null,
    val poluchatel: String? = null,
    val tehnika: String? = null,
    val kommentariy: String? = null,
    @SerialName("uchastok_proyden") val uchastokProyden: Boolean = false,
    val naparniki: List<Int> = emptyList(),
    @SerialName("client_id") val clientId: String? = null,
)

@Serializable
data class SmenaZakrytDto(
    val tip: String? = null,
    val sdelano: Double? = null,
    val ploshad: Double? = null,
)

@Serializable
data class SmenaCreatedDto(
    val id: Int,
    val povtor: Boolean = false,
    val ostatok: Double? = null,
    @SerialName("sdelano_v_cikle") val sdelanoVCikle: Double? = null,
    val ploshad: Double? = null,
    @SerialName("zakryt_cikl") val zakrytCikl: SmenaZakrytDto? = null,
)

@Serializable
data class SmenaFioDto(val id: Int, val fio: String)

@Serializable
data class SmenaOtchetDto(
    val id: Int,
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val fio: String? = null,
    val data: String,
    val vid: String,
    @SerialName("vid_title") val vidTitle: String = "",
    val emoji: String = "",
    val mesto: String? = null,
    @SerialName("ploshad_uchastka") val ploshadUchastka: Double? = null,
    val reysov: Int? = null,
    val obyom: Double? = null,
    val ga: Double? = null,
    val chasy: Double? = null,
    val poluchatel: String? = null,
    val tehnika: String? = null,
    val kommentariy: String? = null,
    @SerialName("zakryl_cikl") val zakrylCikl: Boolean = false,
    val naparniki: List<SmenaFioDto> = emptyList(),
    val moy: Boolean = true,
    @SerialName("mozhno_udalit") val mozhnoUdalit: Boolean = false,
)

@Serializable
data class SmenaSegodnyaDto(
    val chasy: Double = 0.0,
    val obyom: Double = 0.0,
    val reysov: Int = 0,
    val ga: Double = 0.0,
    val otchetov: Int = 0,
)

@Serializable
data class SmenaMoiDto(
    val otchety: List<SmenaOtchetDto> = emptyList(),
    val segodnya: SmenaSegodnyaDto = SmenaSegodnyaDto(),
)
