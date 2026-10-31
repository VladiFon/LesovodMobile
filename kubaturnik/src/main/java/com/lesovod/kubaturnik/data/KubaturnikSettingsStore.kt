package com.lesovod.kubaturnik.data

import android.content.Context
import androidx.core.content.edit

/** Какая половина экрана «Счёт» отдана под сетку плиток диаметра: LEFT — у левого края, RIGHT — у правого. */
enum class TilesSide { LEFT, RIGHT }

/**
 * Настройки вкладки «Счёт» кубатурника, не относящиеся к самой партии (в отличие от
 * [KubaturnikBatchStore]) — переживают «Новую партию» и перезапуск приложения.
 */
class KubaturnikSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kubaturnik_settings", Context.MODE_PRIVATE)

    var tilesSide: TilesSide
        get() = prefs.getString(KEY_TILES_SIDE, null)
            ?.let { raw -> runCatching { TilesSide.valueOf(raw) }.getOrNull() }
            ?: TilesSide.LEFT
        set(value) = prefs.edit { putString(KEY_TILES_SIDE, value.name) }

    /** Голосовой ввод: повторять вслух, что услышал («двадцать четыре»), чтобы не смотреть в экран. */
    var voiceSpeakBack: Boolean
        get() = prefs.getBoolean(KEY_VOICE_SPEAK_BACK, true)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_SPEAK_BACK, value) }

    /** Тёмная тема (по умолчанию, как в Лесоводе) или светлая — переключатель в заголовке. */
    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit { putBoolean(KEY_DARK_THEME, value) }

    /** Звук нажатия на плитку диаметра (включён по умолчанию). */
    var tapSound: Boolean
        get() = prefs.getBoolean(KEY_TAP_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_TAP_SOUND, value) }

    private companion object {
        const val KEY_TAP_SOUND = "tap_sound"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_TILES_SIDE = "tiles_side"
        const val KEY_VOICE_SPEAK_BACK = "voice_speak_back"
    }
}
