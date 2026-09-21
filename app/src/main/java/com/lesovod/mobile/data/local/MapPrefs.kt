package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit

/** Настройки карты: лесничество по умолчанию (из профиля) и время последней загрузки карты. */
class MapPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)

    var defaultLesnichestvo: String?
        get() = prefs.getString(KEY_DEFAULT, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_DEFAULT) else putString(KEY_DEFAULT, value) }

    fun lastDownloadAt(lesnichestvo: String): Long? =
        prefs.getLong(KEY_DOWNLOAD + lesnichestvo, 0L).takeIf { it > 0 }

    fun setLastDownloadAt(lesnichestvo: String, time: Long) = prefs.edit { putLong(KEY_DOWNLOAD + lesnichestvo, time) }

    private companion object {
        const val KEY_DEFAULT = "default_lesnichestvo"
        const val KEY_DOWNLOAD = "download_at_"
    }
}
