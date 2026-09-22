package com.lesovod.mobile.ui.lesokultury

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Ответ POST /api/lesokultury/{id}/inventarizatsiya|perevod в OpenAPI не типизирован
 * (schema: {}) — вместо того чтобы гадать имена полей приживаемости, показываем то, что
 * реально прислал сервер, подписанное по ключам верхнего уровня как есть.
 */
fun JsonElement.toDisplayRows(): List<Pair<String, String>> = when (this) {
    is JsonObject -> entries.map { (key, value) -> key to value.toDisplayText() }
    else -> listOf("Результат" to toDisplayText())
}

private fun JsonElement.toDisplayText(): String = when (this) {
    is JsonNull -> "—"
    is JsonPrimitive -> content
    is JsonArray -> joinToString(", ") { it.toDisplayText() }
    is JsonObject -> entries.joinToString(", ") { (k, v) -> "$k: ${v.toDisplayText()}" }
}
