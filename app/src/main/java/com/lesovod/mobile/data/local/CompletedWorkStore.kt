package com.lesovod.mobile.data.local

import android.content.Context

/**
 * Делянки (квартал + выдел), по которым рабочий отметил работу выполненной.
 * Хранится на устройстве: сервер отдаёт цвет выдела по виду работ и признака
 * "выполнено" в слое карты не присылает, а из списка задач такие пункты исчезают.
 */
class CompletedWorkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("completed_work", Context.MODE_PRIVATE)

    fun add(lesnichestvo: String?, kvartal: String, vydel: String) {
        prefs.edit().putStringSet(KEY, all() + encode(lesnichestvo, kvartal, vydel)).apply()
    }

    /** Ключи "квартал|выдел" выполненных делянок выбранного лесничества (записи без лесничества подходят любому). */
    fun keysFor(lesnichestvo: String?): Set<String> = all().mapNotNull { entry ->
        val parts = entry.split(SEP)
        if (parts.size != 3) return@mapNotNull null
        val lesn = parts[0]
        if (lesn.isEmpty() || lesnichestvo == null || lesn.equals(lesnichestvo.trim(), ignoreCase = true)) key(parts[1], parts[2]) else null
    }.toSet()

    private fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()

    private fun encode(lesnichestvo: String?, kv: String, vd: String) =
        listOf(lesnichestvo?.trim().orEmpty(), kv.trim(), vd.trim()).joinToString(SEP)

    companion object {
        private const val KEY = "done"
        private const val SEP = "|"

        fun key(kvartal: String, vydel: String) = "${kvartal.trim()}|${vydel.trim()}"
    }
}
