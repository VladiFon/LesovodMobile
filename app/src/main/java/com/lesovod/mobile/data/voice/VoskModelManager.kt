package com.lesovod.mobile.data.voice

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Русская модель распознавания речи Vosk (~45 МБ) для голосового ввода в кубатурнике.
 * В APK её не кладём, чтобы обновления не стали тяжелее: качается один раз (лучше по Wi‑Fi)
 * в память приложения и дальше работает полностью без интернета. Если сайт Vosk недоступен,
 * тот же zip можно скачать на компьютере и выбрать файлом ([importFromUri]).
 */
class VoskModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, MODEL_DIR)

    /** Папка с готовой моделью или null, если модель ещё не скачана. */
    val modelPath: String?
        get() = findModelRoot(modelDir)?.absolutePath

    val isReady: Boolean get() = modelPath != null

    /** [onProgress] — доля от 0 до 1 (или -1, если сервер не сообщил размер). */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Сервер модели ответил ${response.code}")
            val body = response.body ?: throw IOException("Пустой ответ сервера модели")
            val total = body.contentLength()
            unzip(CountingInputStream(body.byteStream()) { read ->
                onProgress(if (total > 0) read.toFloat() / total else -1f)
            })
        }
    }

    suspend fun importFromUri(uri: Uri, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val total = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
        input.use { stream ->
            unzip(CountingInputStream(stream) { read -> onProgress(if (total > 0) read.toFloat() / total else -1f) })
        }
    }

    fun delete() {
        modelDir.deleteRecursively()
    }

    /** Распаковываем во временную папку и только потом подменяем — оборванная загрузка не оставит полмодели. */
    private fun unzip(input: InputStream) {
        val tmp = File(appContext.filesDir, "$MODEL_DIR.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(tmp, entry.name)
                if (!target.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                    throw IOException("Битый архив модели")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        if (findModelRoot(tmp) == null) {
            tmp.deleteRecursively()
            throw IOException("В архиве нет модели Vosk (нужен vosk-model-small-ru)")
        }
        modelDir.deleteRecursively()
        if (!tmp.renameTo(modelDir)) throw IOException("Не удалось сохранить модель")
    }

    /** Модель — папка, где лежат «am» и «conf»; в zip она обычно вложена в vosk-model-small-ru-0.22/. */
    private fun findModelRoot(dir: File): File? {
        if (!dir.isDirectory) return null
        if (File(dir, "am").isDirectory && File(dir, "conf").isDirectory) return dir
        return dir.listFiles()?.filter { it.isDirectory }?.firstNotNullOfOrNull { child ->
            child.takeIf { File(it, "am").isDirectory && File(it, "conf").isDirectory }
        }
    }

    private class CountingInputStream(
        private val source: InputStream,
        private val onRead: (Long) -> Unit,
    ) : InputStream() {
        private var read = 0L
        private var lastReported = 0L

        override fun read(): Int = source.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            source.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        override fun close() = source.close()

        private fun advance(n: Long) {
            read += n
            if (read - lastReported >= 256 * 1024) {
                lastReported = read
                onRead(read)
            }
        }
    }

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        const val MODEL_SIZE_MB = 45
        private const val MODEL_DIR = "vosk-model-ru"
    }
}
