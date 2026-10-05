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
    /** Метка коллеги, принятая мной (сервер 05.10.2026+): id приглашения и от кого. */
    @SerialName("share_id") val shareId: Int? = null,
    @SerialName("shared_from_fio") val sharedFromFio: String? = null,
)

/** GET /api/bot/coworkers — кому можно отправить метку. */
@Serializable
data class CoworkerDto(
    val id: Int,
    val fio: String,
    val dolzhnost: String? = null,
    val uchastok: String? = null,
)

/** POST /api/bot/geo-notes/{id}/share */
@Serializable
data class GeoNoteShareRequest(
    @SerialName("sotrudnik_ids") val sotrudnikIds: List<Int>,
    val komment: String? = null,
)

@Serializable
data class GeoNoteShareResultDto(
    val otpravleno: List<GeoNoteShareSentDto> = emptyList(),
)

@Serializable
data class GeoNoteShareSentDto(
    val fio: String,
    @SerialName("uzhe_prinyata") val uzhePrinyata: Boolean = false,
)

/** GET /api/bot/geo-note-shares/incoming — метки, которые мне отправили и я ещё не принял. */
@Serializable
data class GeoNoteShareDto(
    @SerialName("share_id") val shareId: Int,
    @SerialName("from_fio") val fromFio: String? = null,
    val komment: String? = null,
    val status: String? = null,
    @SerialName("shared_at") val sharedAt: String? = null,
    val note: GeoNoteDto,
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
    /** У участка загружен свой контур (схема-чертёж) — рисуем его, а не весь выдел. */
    @SerialName("has_kontur") val hasKontur: Boolean = false,
    /** Контур участка в WGS84; приходит только в первой записи участка. */
    val geometry: GeoJsonGeometry? = null,
    /** Вид культур (обычные, под пологом, плантационные…) и его цвет — сервер 29.09.2026+. */
    @SerialName("vid_kultur") val vidKultur: String? = null,
    @SerialName("vid_kultur_color") val vidKulturColor: String? = null,
)

/** GET /api/map/lesokultury/{id}/kartochka — карточка участка по тапу на карте (сервер 05.10.2026+). */
@Serializable
data class LesokulturyKartochkaDto(
    val id: Int,
    val lesnichestvo: String? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    val ploshad: Double? = null,
    @SerialName("god_sozdaniya") val godSozdaniya: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("sostav_formula") val sostavFormula: String? = null,
    val status: String? = null,
    @SerialName("vid_kultur") val vidKultur: String? = null,
    @SerialName("vid_kultur_color") val vidKulturColor: String? = null,
    @SerialName("metod_sozdaniya") val metodSozdaniya: String? = null,
    @SerialName("sposob_obrabotki") val sposobObrabotki: String? = null,
    @SerialName("posadochnyy_material") val posadochnyyMaterial: String? = null,
    @SerialName("shema_posadki") val shemaPosadki: String? = null,
    @SerialName("gustota_posadki") val gustotaPosadki: String? = null,
    @SerialName("normativ_perevoda") val normativPerevoda: String? = null,
    val tlu: String? = null,
    @SerialName("kategoriya_ploshadi") val kategoriyaPloshadi: String? = null,
    @SerialName("posl_meropriyatie") val poslMeropriyatie: String? = null,
    @SerialName("prizhivaemost_pct") val prizhivaemostPct: Double? = null,
    @SerialName("kolichestvo_na_ga") val kolichestvoNaGa: Double? = null,
    @SerialName("sostav_fakt") val sostavFakt: String? = null,
    val primechaniya: String? = null,
    @SerialName("has_kontur") val hasKontur: Boolean = false,
    val zhurnal: List<LesokulturyZhurnalDto> = emptyList(),
)

@Serializable
data class LesokulturyZhurnalDto(
    val tip: String? = null,
    val data: String? = null,
    @SerialName("prizhivaemost_pct") val prizhivaemostPct: Double? = null,
    @SerialName("kolichestvo_na_ga") val kolichestvoNaGa: Double? = null,
)

/** Со старым сервером (без /kartochka) — то, что уже пришло для слоя. */
fun LesokulturyMapDto.toKartochka() = LesokulturyKartochkaDto(
    id = id, lesnichestvo = lesnichestvo, kvartal = kvartal, vydel = vydel, ploshad = ploshad,
    godSozdaniya = godSozdaniya, glavnayaPoroda = glavnayaPoroda, status = status, vidKultur = vidKultur,
    vidKulturColor = vidKulturColor, hasKontur = hasKontur,
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
