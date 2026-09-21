package com.lesovod.mobile.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.HttpException

fun extractErrorMessage(e: HttpException, notFoundMessage: String = "Ошибка сервера"): String {
    val body: ResponseBody? = e.response()?.errorBody()
    val raw = body?.string()
    val detail = raw?.let {
        runCatching {
            Json.parseToJsonElement(it).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
    }
    return detail ?: "$notFoundMessage (${e.code()})"
}
