package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/bot/geo-notes — метки текущего рабочего (сервер отдаёт только свои). */
@Serializable
data class GeoNoteDto(
    val id: Int,
    val lat: Double,
    val lon: Double,
    @SerialName("note_text") val noteText: String? = null,
    val kategoriya: String? = null,
    @SerialName("author_fio") val authorFio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
)

/** GET /api/map/work-colors — цвет выдела по видам выполненных работ. */
@Serializable
data class WorkColorsDto(
    val items: List<WorkColorItemDto> = emptyList(),
)

@Serializable
data class WorkColorItemDto(
    val kvartal: String,
    val vydel: String,
    val color: String,
    val label: String? = null,
    val types: List<String> = emptyList(),
    @SerialName("last_date") val lastDate: String? = null,
)

/** GET /api/map/search */
@Serializable
data class MapSearchResultDto(
    val type: String,
    val title: String,
    val subtitle: String? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("delyanka_id") val delyankaId: Int? = null,
    @SerialName("uchastok_id") val uchastokId: Int? = null,
    @SerialName("status_rabot") val statusRabot: String? = null,
)

/** GET /api/map/lesokultury — участки лесных культур для подсветки выделов. */
@Serializable
data class LesokulturyMapDto(
    val id: Int,
    val kvartal: String,
    val vydel: String,
    val lesnichestvo: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("god_sozdaniya") val godSozdaniya: String? = null,
    val ploshad: Double? = null,
    val status: String? = null,
)

/** GET /api/map/delyanka-location — центр выдела. */
@Serializable
data class VydelLocationDto(
    val found: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** GET /api/map/vydel-history */
@Serializable
data class VydelHistoryDto(
    val kvartal: String? = null,
    val vydel: String? = null,
    val works: List<VydelWorkDto> = emptyList(),
    val lesokultury: List<LesokulturyMapDto> = emptyList(),
    val meropriyatiya: List<LesokulturyMeropriyatieDto> = emptyList(),
    val delyanki: List<VydelDelyankaDto> = emptyList(),
)

@Serializable
data class VydelWorkDto(
    val id: Int,
    @SerialName("tip_raboty") val tipRaboty: String? = null,
    @SerialName("ispolnitel_fio") val ispolnitelFio: String? = null,
    @SerialName("data_vypolneniya") val dataVypolneniya: String? = null,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
)

@Serializable
data class LesokulturyMeropriyatieDto(
    @SerialName("uchastok_id") val uchastokId: Int,
    val tip: String? = null,
    val data: String? = null,
    @SerialName("prizhivaemost_pct") val prizhivaemostPct: Double? = null,
)

@Serializable
data class VydelDelyankaDto(
    @SerialName("delyanka_id") val delyankaId: Int,
    val nazvanie: String? = null,
    @SerialName("status_rabot") val statusRabot: String? = null,
)

/** GET /api/map/sklady */
@Serializable
data class SkladDto(
    val id: Int,
    val nazvanie: String,
    val lat: Double,
    val lon: Double,
    val comment: String? = null,
)

/** POST /api/bot/tracks — контур, обмеренный обходом; точки [lon, lat]. */
@Serializable
data class TrackCreateRequest(
    val nazvanie: String? = null,
    val points: List<List<Double>>,
    val closed: Boolean = true,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("note_text") val noteText: String? = null,
)

@Serializable
data class TrackCreatedDto(
    val id: Int,
    @SerialName("ploshad_ga") val ploshadGa: Double? = null,
    @SerialName("perimetr_m") val perimetrM: Double? = null,
)
