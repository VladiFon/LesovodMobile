package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class KubaturnikCountEntry(val sort: String, val destination: String, val diameter: Int, val count: Int)

@Serializable
data class KubaturnikSnapshot(
    val blockId: String? = null,
    val lengthIndex: Int = -1,
    val lengthValue: Double = 0.0,
    val stepCm: Int = 2,
    val destination: String = "MACHINE",
    val sorts: List<String> = listOf("Осн."),
    val activeSort: String = "Осн.",
    val counts: List<KubaturnikCountEntry> = emptyList(),
)

/** Партия — офлайн-инструмент: данные подсчёта живут только на телефоне, на сервер не уходят. */
class KubaturnikBatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kubaturnik_batch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): KubaturnikSnapshot? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching { json.decodeFromString<KubaturnikSnapshot>(raw) }.getOrNull()
    }

    fun save(snapshot: KubaturnikSnapshot) {
        prefs.edit { putString(KEY, json.encodeToString(snapshot)) }
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "snapshot"
    }
}
