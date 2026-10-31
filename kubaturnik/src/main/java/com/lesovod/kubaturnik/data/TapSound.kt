package com.lesovod.kubaturnik.data

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Звук нажатия на плитку диаметра: короткий «пик» — бревно добавлено, двойной — убрано.
 * Идёт через громкость медиа (качелька громкости), а не через системные «звуки касания»,
 * которые на большинстве телефонов выключены. Генератор тонов не требует файлов и разрешений.
 */
class TapSound {
    private var tone: ToneGenerator? = null

    private fun generator(): ToneGenerator? =
        tone ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()?.also { tone = it }

    fun added() {
        generator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    fun removed() {
        generator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
    }

    fun release() {
        tone?.release()
        tone = null
    }

    private companion object {
        const val VOLUME = 90 // 0..100 от текущей громкости медиа
    }
}
