package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Таблица проб: «Номер пробы» / «Размер пробы» (единицу сервер не проверяет, хранит как есть). */
@Serializable
data class ProbaRowIn(
    val nomer: String,
    val razmer: Double,
)

/** Результаты обследования — список общий на всю карточку, не привязан к конкретной пробе. */
@Serializable
data class RezultatIn(
    val poroda: String,
    val vysazheno: Int,
    val prizhilos: Int,
    @SerialName("srednyaya_vysota") val srednyayaVysota: Double? = null,
)

/** Тело POST /api/lesokultury/{uchastok_id}/inventarizatsiya. */
@Serializable
data class InventarizatsiyaRequest(
    val data: String? = null,
    val proby: List<ProbaRowIn>,
    val rezultaty: List<RezultatIn>,
    @SerialName("kolichestvo_na_ga") val kolichestvoNaGa: Double? = null,
    @SerialName("sostav_fakt") val sostavFakt: String = "",
    val primechaniya: String = "",
    val god: Int,
)

/** Тело POST /api/lesokultury/{uchastok_id}/perevod — то же плюс решение. */
@Serializable
data class PerevodRequest(
    val data: String? = null,
    val proby: List<ProbaRowIn>,
    val rezultaty: List<RezultatIn>,
    @SerialName("kolichestvo_na_ga") val kolichestvoNaGa: Double? = null,
    @SerialName("sostav_fakt") val sostavFakt: String = "",
    val primechaniya: String = "",
    val god: Int? = null,
    val reshenie: String,
    /** Для «перевести» — графы прил. 4 ведомости текущих изменений. */
    val taksatsiya: TaksatsiyaIn? = null,
    /** Для «доращивание» — до какого года. */
    @SerialName("do_goda") val doGoda: Int? = null,
    /** Для «списать» — обязательно. */
    @SerialName("prichina_spisaniya") val prichinaSpisaniya: String = "",
)

/** Таксация при переводе в покрытые лесом земли (прил. 4 к приказу №130). */
@Serializable
data class TaksatsiyaIn(
    @SerialName("nomer_kartochki") val nomerKartochki: String = "",
    val ploshad: Double? = null,
    val podvydel: String = "",
    val sostav: String = "",
    val vozrast: Int? = null,
    val vysota: Double? = null,
    val diametr: Double? = null,
    val polnota: Double? = null,
)

/** Тело PATCH /api/lesokultury/{uchastok_id}/polya — null = не менять. */
@Serializable
data class UchastokPolyaRequest(
    val podvydel: String? = null,
    @SerialName("metod_sozdaniya") val metodSozdaniya: String? = null,
    @SerialName("sposob_obrabotki") val sposobObrabotki: String? = null,
    @SerialName("shema_mezhdu_ryadami") val shemaMezhduRyadami: Double? = null,
    @SerialName("shema_v_ryadu") val shemaVRyadu: Double? = null,
    @SerialName("gustota_posadki") val gustotaPosadki: Double? = null,
    @SerialName("posadochnyy_material") val posadochnyyMaterial: String? = null,
)

/**
 * Ответ GET /api/uhody/porody — объект с двумя справочниками, а не плоская карта имя→id
 * (как listLesnichestva): {"porody": [...], "vidy_rubki": [...]}.
 */
@Serializable
data class PorodySpravochnikDto(
    val porody: List<String> = emptyList(),
    @SerialName("vidy_rubki") val vidyRubki: List<String> = emptyList(),
)
