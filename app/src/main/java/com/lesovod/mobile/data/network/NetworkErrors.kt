package com.lesovod.mobile.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.HttpException

fun extractErrorMessage(e: HttpException, notFoundMessage: String = "Ошибка сервера"): String {
    val body: ResponseBody? = e.response()?.errorBody()
    val raw = body?.string()
    val detail = raw?.let {
        runCatching { detailText(Json.parseToJsonElement(it).jsonObject["detail"]) }.getOrNull()
    }
    return detail ?: "$notFoundMessage (${e.code()})"
}

/**
 * FastAPI отдаёт detail строкой (HTTPException) или списком ошибок проверки (422:
 * [{"loc": [...], "msg": "..."}]) — из списка собираем тексты msg, иначе пользователь
 * видел только «Ошибка сервера (422)».
 */
private fun detailText(detail: JsonElement?): String? = when (detail) {
    is JsonPrimitive -> detail.contentOrNull
    is JsonArray -> detail.mapNotNull { item ->
        (item as? JsonObject)?.get("msg")?.jsonPrimitive?.contentOrNull
            ?.removePrefix("Value error, ")
    }.distinct().joinToString("; ").ifBlank { null }
    else -> null
}

/**
 * Брошено при сбое соединения (нет сети, DNS, таймаут) — в отличие от [HttpException],
 * когда сервер ответил, но с ошибкой. По этому типу вызывающая сторона решает,
 * поставить ли действие в офлайн-очередь вместо показа ошибки.
 */
class ConnectivityException(message: String, cause: Throwable? = null) : Exception(message, cause)
