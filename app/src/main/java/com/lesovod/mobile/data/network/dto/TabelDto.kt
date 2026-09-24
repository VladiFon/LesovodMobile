package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Одна строка табеля на день — GET/POST /api/tabel/day отдают и принимают этот же формат. */
@Serializable
data class TabelDayEntryDto(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val fio: String,
    val dolzhnost: String,
    val status: String? = null,
    val kommentariy: String? = null,
    @SerialName("delyanka_item_id") val delyankaItemId: Int? = null,
    @SerialName("d_kvartal") val dKvartal: String? = null,
    @SerialName("d_vydel") val dVydel: String? = null,
    @SerialName("lesokultury_uchastok_id") val lesokulturyUchastokId: Int? = null,
    @SerialName("lku_kvartal") val lkuKvartal: String? = null,
    @SerialName("lku_vydel") val lkuVydel: String? = null,
    @SerialName("lku_glavnaya_poroda") val lkuGlavnayaPoroda: String? = null,
    @SerialName("vid_raboty_id") val vidRabotyId: Int? = null,
    @SerialName("vid_raboty_nazvanie") val vidRabotyNazvanie: String? = null,
    @SerialName("mobile_status") val mobileStatus: String? = null,
)

@Serializable
data class TabelEntryRequest(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val status: String,
    @SerialName("delyanka_item_id") val delyankaItemId: Int? = null,
    @SerialName("lesokultury_uchastok_id") val lesokulturyUchastokId: Int? = null,
    @SerialName("vid_raboty_id") val vidRabotyId: Int? = null,
    val kommentariy: String = "",
)

@Serializable
data class TabelDaySaveRequest(
    val data: String,
    val entries: List<TabelEntryRequest>,
)

@Serializable
data class VidRabotyDto(val id: Int, val nazvanie: String)

@Serializable
data class VidRabotyCreateRequest(val nazvanie: String)

/** GET /api/delyanki/by-location — обычно 0 или 1 элемент, но в базе нет уникальности на кв./выдел. */
@Serializable
data class DelyankaByLocationDto(
    @SerialName("delyanka_id") val delyankaId: Int? = null,
    val nazvanie: String? = null,
    @SerialName("delyanka_status") val delyankaStatus: String? = null,
    @SerialName("item_id") val itemId: Int,
    @SerialName("status_rabot") val statusRabot: String? = null,
)

/** GET /api/tabel/lesokultury-uchastki?search= — свой DTO, а не ui.proba.LesokulturyUchastokDto:
 * тому эндпоинту (api/lesokultury/uchastki) lesnichestvo не нужен, а этому picker'у — нужен. */
@Serializable
data class TabelLesokulturyUchastokDto(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
)
