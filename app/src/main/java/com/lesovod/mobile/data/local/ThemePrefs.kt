package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Выбор темы в профиле. По умолчанию — тёмная: светлая в поле читалась хуже (просьба пользователей). */
enum class ThemeMode(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    SYSTEM("Как в системе"),
    /** Белый фон, чёрные буквы, сочные цвета — для яркого солнца и снега. */
    SUN("Солнце"),
}

/** Хранит выбранную тему в SharedPreferences и отдаёт её потоком — MainActivity перерисуется сразу. */
class ThemePrefs private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        prefs.getString(KEY_MODE, null)?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.DARK,
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Режим «крупно» (12.11.2026): всё в приложении ×1.2 — для тех, кто плохо видит. */
    private val _large = MutableStateFlow(prefs.getBoolean(KEY_LARGE, false))
    val large: StateFlow<Boolean> = _large.asStateFlow()

    fun setLarge(on: Boolean) {
        prefs.edit { putBoolean(KEY_LARGE, on) }
        _large.value = on
    }

    /** Кнопка-солнышко на карте: включить «Солнце» и вернуть прежнюю тему тем же нажатием. */
    fun toggleSun() {
        if (_mode.value == ThemeMode.SUN) {
            val back = prefs.getString(KEY_BEFORE_SUN, null)?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
            setMode(back?.takeIf { it != ThemeMode.SUN } ?: ThemeMode.DARK)
        } else {
            prefs.edit { putString(KEY_BEFORE_SUN, _mode.value.name) }
            setMode(ThemeMode.SUN)
        }
    }

    fun setMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _mode.value = mode
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_LARGE = "large"
        private const val KEY_BEFORE_SUN = "before_sun"

        @Volatile
        private var instance: ThemePrefs? = null

        fun getInstance(context: Context): ThemePrefs =
            instance ?: synchronized(this) {
                instance ?: ThemePrefs(context).also { instance = it }
            }
    }
}
