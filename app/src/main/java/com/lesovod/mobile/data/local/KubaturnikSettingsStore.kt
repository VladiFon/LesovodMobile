package com.lesovod.mobile.data.local

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

    private companion object {
        const val KEY_TILES_SIDE = "tiles_side"
    }
}
