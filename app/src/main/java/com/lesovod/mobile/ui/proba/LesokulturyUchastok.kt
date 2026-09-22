package com.lesovod.mobile.ui.proba

import com.lesovod.mobile.ui.map.stringValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Участок лесных культур для выбора в пробе (GET /api/lesokultury/uchastki). */
data class LesokulturyUchastok(val id: Int, val label: String)

private fun JsonObject.intValue(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/**
 * Точные имена полей ответа не были даны в задаче (только путь) — читаем несколько разумных
 * вариантов имени id/названия; участок без распознаваемого id просто не попадёт в список,
 * а не уронит разбор остальных.
 */
fun JsonObject.toLesokulturyUchastok(): LesokulturyUchastok? {
    val id = intValue("id") ?: intValue("uchastok_id") ?: intValue("lesokultury_uchastok_id") ?: return null
    val name = stringValue("name") ?: stringValue("nazvanie") ?: stringValue("label")
    val kvartal = stringValue("kvartal")
    val vydel = stringValue("vydel")
    val label = name ?: listOfNotNull(kvartal?.let { "кв. $it" }, vydel?.let { "выд. $it" })
        .joinToString(", ")
        .ifBlank { "Участок №$id" }
    return LesokulturyUchastok(id, label)
}
