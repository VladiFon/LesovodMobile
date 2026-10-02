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
}

/** Хранит выбранную тему в SharedPreferences и отдаёт её потоком — MainActivity перерисуется сразу. */
class ThemePrefs private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        prefs.getString(KEY_MODE, null)?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.DARK,
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _mode.value = mode
    }

    companion object {
        private const val KEY_MODE = "mode"

        @Volatile
        private var instance: ThemePrefs? = null

        fun getInstance(context: Context): ThemePrefs =
            instance ?: synchronized(this) {
                instance ?: ThemePrefs(context).also { instance = it }
            }
    }
}
