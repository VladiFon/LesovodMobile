package com.lesovod.mobile.data.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Непрерывное оффлайн-распознавание речи (Vosk) + голосовой ответ (TextToSpeech) для кубатурника.
 * Распознаватель получает короткий словарь (числа и команды), поэтому слышит только их и почти
 * не путает «двадцать четыре» с чем-то ещё. Пока телефон говорит сам, микрофон на паузе —
 * иначе он «услышит» собственный ответ и запишет лишнее бревно.
 */
class KubaturnikVoice(context: Context) {

    interface Listener {
        fun onReady()
        fun onPartial(text: String)
        fun onPhrase(text: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()

    private var model: Model? = null
    private var modelPath: String? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    /** Номер запуска: если пользователь нажал «стоп» раньше, чем модель загрузилась, старт отменяется. */
    private var session = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    val isListening: Boolean get() = speechService != null

    init {
        tts = TextToSpeech(appContext) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val result = engine.setLanguage(Locale("ru", "RU"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { speechService?.setPause(true) }
                    }

                    override fun onDone(utteranceId: String?) = resumeMic()

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = resumeMic()

                    override fun onError(utteranceId: String?, errorCode: Int) = resumeMic()
                })
            }
        }
    }

    private fun resumeMic() {
        // Небольшая задержка — чтобы хвост собственного эха не попал в микрофон.
        mainHandler.postDelayed({ speechService?.setPause(false) }, 250)
    }

    /** [words] — словарь распознавателя; слова, которых нет в модели, Vosk просто пропустит. */
    fun start(path: String, words: Collection<String>, listener: Listener) {
        stop()
        val mySession = ++session
        val grammar = JSONArray().apply {
            words.filter { it.isNotBlank() }.distinct().forEach { put(it) }
            put("[unk]")
        }.toString()

        loader.execute {
            try {
                val loaded = model?.takeIf { modelPath == path } ?: Model(path).also {
                    model?.close()
                    model = it
                    modelPath = path
                }
                val rec = Recognizer(loaded, SAMPLE_RATE, grammar)
                mainHandler.post {
                    if (mySession != session) {
                        rec.close()
                        return@post
                    }
                    try {
                        val service = SpeechService(rec, SAMPLE_RATE)
                        recognizer = rec
                        speechService = service
                        service.startListening(object : RecognitionListener {
                            override fun onPartialResult(hypothesis: String?) {
                                textOf(hypothesis, "partial")?.let(listener::onPartial)
                            }

                            override fun onResult(hypothesis: String?) {
                                textOf(hypothesis, "text")?.let(listener::onPhrase)
                            }

                            override fun onFinalResult(hypothesis: String?) {
                                textOf(hypothesis, "text")?.let(listener::onPhrase)
                            }

                            override fun onError(exception: Exception?) {
                                listener.onError(exception?.message ?: "Ошибка микрофона")
                                stop()
                            }

                            override fun onTimeout() = Unit
                        })
                        listener.onReady()
                    } catch (e: Exception) {
                        rec.close()
                        listener.onError("Микрофон занят или недоступен: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { listener.onError("Не удалось загрузить модель речи: ${e.message}") }
            }
        }
    }

    fun stop() {
        session++
        speechService?.let {
            it.stop()
            it.shutdown()
        }
        speechService = null
        recognizer?.close()
        recognizer = null
    }

    fun speak(text: String) {
        if (!ttsReady) return
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "kub-${System.nanoTime()}")
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        loader.execute {
            model?.close()
            model = null
        }
        loader.shutdown()
    }

    private fun textOf(json: String?, key: String): String? {
        if (json.isNullOrBlank()) return null
        val text = runCatching { JSONObject(json).optString(key) }.getOrNull()
            ?.replace("[unk]", "")
            ?.trim()
        return text?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val SAMPLE_RATE = 16000f
    }
}
