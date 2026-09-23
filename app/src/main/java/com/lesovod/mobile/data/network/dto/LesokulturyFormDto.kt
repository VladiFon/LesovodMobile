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
