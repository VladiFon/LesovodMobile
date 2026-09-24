package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit

/** Сторона плиток диаметра на вкладке «Счёт»: где сетка, а где справочная панель. */
enum class TilesSide { LEFT, RIGHT }

/**
 * Настройка стороны плиток — не часть партии (см. [KubaturnikBatchStore]), а отдельная настройка
 * приложения: живёт своей жизнью, не сбрасывается вместе со счётчиками и «Новой партией».
 */
class KubaturnikPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var tilesSide: TilesSide
        get() = if (prefs.getString(KEY_TILES_SIDE, null) == TilesSide.RIGHT.name) TilesSide.RIGHT else TilesSide.LEFT
        set(value) = prefs.edit { putString(KEY_TILES_SIDE, value.name) }

    private companion object {
        const val PREFS_NAME = "kubaturnik_prefs"
        const val KEY_TILES_SIDE = "tiles_side"
    }
}
