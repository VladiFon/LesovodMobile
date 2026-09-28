package com.lesovod.mobile.data.local

import android.content.Context
import com.lesovod.mobile.data.network.dto.ProbaResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * «Мои пробы» на устройстве — последние отправленные пробы рубок ухода с расчётом, чтобы рабочий
 * мог посмотреть их и без сети. Заполняется ответом сервера при отправке и списком
 * GET /api/uhody/proby/mine, когда он доступен.
 */
class ProbaHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("proba_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ProbaResponse.serializer())

    fun list(): List<ProbaResponse> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** Добавляет/обновляет одну пробу (по id) — самые новые сверху. */
    fun add(proba: ProbaResponse) = save(listOf(proba) + list().filterNot { it.id == proba.id })

    /** Список с сервера — источник правды; локальные записи, которых там нет (например, удалённые), уходят. */
    fun replaceAll(items: List<ProbaResponse>) = save(items)

    private fun save(items: List<ProbaResponse>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, items.sortedByDescending { it.id }.take(MAX))).apply()
    }

    private companion object {
        const val KEY = "items"
        const val MAX = 100
    }
}
