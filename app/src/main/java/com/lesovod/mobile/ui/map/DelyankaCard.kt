package com.lesovod.mobile.ui.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Данные именно по этой делянке (лесосеке) из документа МДО — GET /api/delyanki/{id}, поля элемента
 * items. Они могут отличаться от общей таксации выдела (см. [VydelCard]).
 */
data class DelyankaCard(
    val delyankaId: Int,
    val nazvanie: String?,
    val statusRabot: String?,
    val kvartal: String?,
    val vydel: String?,
    val lesosekaNomer: String?,
    val ploshad: String?,
    val zapasNaGa: String?,
    val vyrubaemyyZapas: String?,
    val sostav: String?,
    val vozrast: String?,
    val polnota: String?,
    val tipLesa: String?,
    val bonitet: String?,
    val kategoriyaLesov: String?,
)

/**
 * Делянка может состоять из нескольких выделов (по элементу items на каждый) — берём элемент,
 * который лежит на тапнутом выделе; если такого нет, первый.
 */
fun JsonObject.toDelyankaCard(delyankaId: Int, kvartal: String, vydel: String): DelyankaCard? {
    val header = this["delyanka"] as? JsonObject
    val items = (this["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val item = items.firstOrNull { it.stringValue("kvartal")?.trim() == kvartal.trim() && it.stringValue("vydel")?.trim() == vydel.trim() }
        ?: items.firstOrNull()
        ?: return null
    return DelyankaCard(
        delyankaId = delyankaId,
        nazvanie = header?.stringValue("nazvanie"),
        statusRabot = item.stringValue("status_rabot"),
        kvartal = item.stringValue("kvartal"),
        vydel = item.stringValue("vydel"),
        lesosekaNomer = item.stringValue("lesoseka_nomer"),
        ploshad = item.stringValue("ploshad"),
        zapasNaGa = item.stringValue("zapas_na_ga"),
        vyrubaemyyZapas = item.stringValue("vyrubaemyy_zapas"),
        sostav = item.stringValue("sostav"),
        vozrast = item.stringValue("vozrast"),
        polnota = item.stringValue("polnota"),
        tipLesa = item.stringValue("tip_lesa"),
        bonitet = item.stringValue("bonitet"),
        kategoriyaLesov = item.stringValue("kategoriya_lesov"),
    )
}
