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
    /** Площадь, на которую считается «запас на лесосеке» (без неё сервер считает 0). */
    @SerialName("ploshad_lesoseki") val ploshadLesoseki: Double? = null,
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

/** Расчёт пробы — сервер кладёт его во вложенный объект data (uhody_proby.data_json). */
@Serializable
data class ProbaDataDto(
    val rows: List<ProbaRowResponse> = emptyList(),
    @SerialName("obyom_sklad_total") val obyomSkladTotal: Double? = null,
    @SerialName("zapas_proby_total") val zapasProbyTotal: Double? = null,
    @SerialName("zapas_na_1ga") val zapasNa1Ga: Double? = null,
    @SerialName("zapas_na_lesoseke") val zapasNaLesoseke: Double? = null,
    @SerialName("ploshad_lesoseki") val ploshadLesoseki: Double? = null,
)

/**
 * Ответ POST /api/uhody/proby и элементы GET /api/uhody/proby/mine. Итоги расчёта сервер отдаёт
 * внутри [data], а не на верхнем уровне — раньше приложение искало их сверху, получало пустоту и
 * показывало только «Проба сохранена». Верхние поля оставлены на случай, если сервер их добавит.
 */
@Serializable
data class ProbaResponse(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    @SerialName("ploshad_vydela") val ploshadVydela: Double? = null,
    @SerialName("ploshad_proby") val ploshadProby: Double? = null,
    @SerialName("data_zamera") val dataZamera: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val data: ProbaDataDto? = null,
    @SerialName("obyom_sklad_total") private val obyomSkladTotalTop: Double? = null,
    @SerialName("zapas_proby_total") private val zapasProbyTotalTop: Double? = null,
    @SerialName("zapas_na_1ga") private val zapasNa1GaTop: Double? = null,
    @SerialName("zapas_na_lesoseke") private val zapasNaLesosekeTop: Double? = null,
    @SerialName("rows") private val rowsTop: List<ProbaRowResponse> = emptyList(),
) {
    val obyomSkladTotal: Double? get() = obyomSkladTotalTop ?: data?.obyomSkladTotal
    val zapasProbyTotal: Double? get() = zapasProbyTotalTop ?: data?.zapasProbyTotal
    val zapasNa1Ga: Double? get() = zapasNa1GaTop ?: data?.zapasNa1Ga
    val zapasNaLesoseke: Double? get() = zapasNaLesosekeTop ?: data?.zapasNaLesoseke
    val ploshadLesoseki: Double? get() = data?.ploshadLesoseki ?: ploshadVydela
    val rows: List<ProbaRowResponse> get() = rowsTop.ifEmpty { data?.rows.orEmpty() }
}

/**
 * Элемент GET /api/uhody/lesokultury-dannye — что известно о культурах участка и что сервер
 * сам подставит в шапку пробы (состав, полнота, возраст, площадь). istochniki: поле → откуда взято.
 */
@Serializable
data class LesokulturyDannyeDto(
    val id: Int,
    val status: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("god_sozdaniya") val godSozdaniya: String? = null,
    val sostav: String? = null,
    val polnota: Double? = null,
    val vozrast: Int? = null,
    val ploshad: Double? = null,
    @SerialName("kolichestvo_na_ga") val kolichestvoNaGa: Double? = null,
    @SerialName("posledn_uhod") val poslednUhod: String? = null,
    val istochniki: Map<String, String> = emptyMap(),
)
