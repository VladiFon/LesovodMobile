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

/** Сохранённый расчёт партии — попадает сюда при «Новой партии», а не стирается. */
@Serializable
data class KubaturnikCalculation(
    val id: Int,
    val label: String,
    val savedAt: Long,
    val snapshot: KubaturnikSnapshot,
)

/**
 * Текущая партия (черновик) переживает перезапуск приложения, как и раньше. «Новая партия» теперь
 * не стирает подсчитанное, а сохраняет его в список расчётов ("Расчёт #N") и только потом
 * очищает черновик — расчёт остаётся доступен для просмотра. Всё — офлайн, на сервер не уходит.
 */
class KubaturnikBatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kubaturnik_batch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadDraft(): KubaturnikSnapshot? {
        val raw = prefs.getString(KEY_DRAFT, null) ?: return null
        return runCatching { json.decodeFromString<KubaturnikSnapshot>(raw) }.getOrNull()
    }

    fun saveDraft(snapshot: KubaturnikSnapshot) {
        prefs.edit { putString(KEY_DRAFT, json.encodeToString(snapshot)) }
    }

    fun clearDraft() {
        prefs.edit { remove(KEY_DRAFT) }
    }

    fun listCalculations(): List<KubaturnikCalculation> {
        val raw = prefs.getString(KEY_CALCULATIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<KubaturnikCalculation>>(raw) }.getOrDefault(emptyList())
            .sortedByDescending { it.savedAt }
    }

    /** Сохраняет текущий черновик в историю под следующим номером и возвращает его. */
    fun archiveDraft(snapshot: KubaturnikSnapshot): KubaturnikCalculation {
        val id = nextId()
        val calculation = KubaturnikCalculation(id, "Расчёт #$id", System.currentTimeMillis(), snapshot)
        val updated = (listCalculations() + calculation)
        prefs.edit { putString(KEY_CALCULATIONS, json.encodeToString(updated)) }
        return calculation
    }

    fun deleteCalculation(id: Int) {
        val updated = listCalculations().filterNot { it.id == id }
        prefs.edit { putString(KEY_CALCULATIONS, json.encodeToString(updated)) }
    }

    private fun nextId(): Int {
        val next = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit { putInt(KEY_NEXT_ID, next + 1) }
        return next
    }

    private companion object {
        const val KEY_DRAFT = "draft"
        const val KEY_CALCULATIONS = "calculations"
        const val KEY_NEXT_ID = "next_id"
    }
}
