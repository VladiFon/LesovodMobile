package com.lesovod.mobile.ui.map

import kotlinx.serialization.json.JsonObject

/**
 * Упрощённая витрина ответа GET /api/taxation/vydel (legacy/db.py:get_vydel_card) —
 * из ~27 полей карточки показываем то, что реально нужно рабочему на месте,
 * а не весь сырой набор колонок таксационной базы.
 */
data class VydelCard(
    val kvartal: String?,
    val vydel: String?,
    val lesnichestvo: String?,
    val ploshad: String?,
    val kategoriyaLesov: String?,
    val tipLesa: String?,
    val tlu: String?,
    val bonitet: String?,
    val polnota: String?,
    val zapasNaGa: String?,
    val zapasNaVydele: String?,
    val formulaSostava: String?,
    val tselevayaPoroda: String?,
    val primechaniya: String?,
)

fun JsonObject.toVydelCard(): VydelCard = VydelCard(
    kvartal = stringValue("kvartal_nomer"),
    vydel = stringValue("nomer"),
    lesnichestvo = stringValue("lesnichestvo"),
    ploshad = stringValue("ploshad"),
    kategoriyaLesov = stringValue("kategoriya_lesov"),
    tipLesa = stringValue("tip_lesa"),
    tlu = stringValue("tlu"),
    bonitet = stringValue("bonitet"),
    polnota = stringValue("polnota"),
    zapasNaGa = stringValue("zapas_na_ga_display") ?: stringValue("zapas_na_ga"),
    zapasNaVydele = stringValue("zapas_na_vydele"),
    formulaSostava = stringValue("formula_sostava"),
    tselevayaPoroda = stringValue("tselevaya_poroda"),
    primechaniya = stringValue("primechaniya"),
)
