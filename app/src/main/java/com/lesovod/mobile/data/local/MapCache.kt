package com.lesovod.mobile.data.local

import android.content.Context
import java.io.File
import java.io.OutputStream

/**
 * Дисковый кэш слоёв карты в компактном бинарном виде (см. ShapeCodec). Лежит в filesDir, а не
 * в cacheDir: система не должна вычищать его при нехватке места — по нему карта работает без интернета.
 */
class MapCache(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "map_cache_v2").apply { mkdirs() }

    init {
        // прежний JSON-кэш больше не читается — освобождаем место
        File(context.applicationContext.filesDir, "map_cache").deleteRecursively()
    }

    fun file(key: String): File = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".bin")

    /** Пишем во временный файл и переименовываем — недописанный файл в кэше не появится. */
    fun write(key: String, block: (OutputStream) -> Unit) {
        val target = file(key)
        val tmp = File(dir, target.name + ".tmp")
        try {
            tmp.outputStream().buffered().use(block)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
            tmp.delete()
        }
    }
}
