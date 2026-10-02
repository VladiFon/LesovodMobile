package com.lesovod.mobile.data.local

import android.content.Context
import com.lesovod.mobile.data.network.dto.MyDelyankaDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Сохранённый ответ сервера и время, когда он был получен. */
@Serializable
data class Cached<T>(val savedAt: Long, val data: T) {
    /** «Данные на ДД.ММ чч:мм» — подпись к данным, показанным без связи. */
    val stamp: String get() = "Данные на " + SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(Date(savedAt))
}

/**
 * Кэш данных для работы в лесу без связи: список «Мои делянки», остатки по каждой делянке и задачи
 * плана работ. JSON-файлы в filesDir (как MapCache — система их не вычищает). Пишется после каждого
 * удачного запроса и разом — кнопкой «Подготовиться к выезду».
 */
class FieldDataCache(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "field_cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun saveMyDelyanki(list: List<MyDelyankaDto>) =
        write("my_delyanki", Cached.serializer(ListSerializer(MyDelyankaDto.serializer())), Cached(now(), list))

    fun loadMyDelyanki(): Cached<List<MyDelyankaDto>>? =
        read("my_delyanki", Cached.serializer(ListSerializer(MyDelyankaDto.serializer())))

    fun saveRemaining(delyankaId: Int, remaining: RemainingResponseDto) =
        write("remaining_$delyankaId", Cached.serializer(RemainingResponseDto.serializer()), Cached(now(), remaining))

    fun loadRemaining(delyankaId: Int): Cached<RemainingResponseDto>? =
        read("remaining_$delyankaId", Cached.serializer(RemainingResponseDto.serializer()))

    fun saveWorkPlan(list: List<WorkPlanItemDto>) =
        write("work_plan", Cached.serializer(ListSerializer(WorkPlanItemDto.serializer())), Cached(now(), list))

    fun loadWorkPlan(): Cached<List<WorkPlanItemDto>>? =
        read("work_plan", Cached.serializer(ListSerializer(WorkPlanItemDto.serializer())))

    private fun now() = System.currentTimeMillis()

    @Synchronized
    private fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        val target = File(dir, "$key.json")
        val tmp = File(dir, "$key.json.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(serializer, value))
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }.onFailure { tmp.delete() }
    }

    @Synchronized
    private fun <T> read(key: String, serializer: KSerializer<T>): T? {
        val file = File(dir, "$key.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }
}
