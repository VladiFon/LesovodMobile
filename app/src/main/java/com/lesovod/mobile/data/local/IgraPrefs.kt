package com.lesovod.mobile.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Игровой режим (03.11.2026): по умолчанию ВЫКЛЮЧЕН — приложение обычное. Включается
 * в Профиле: тогда на главном экране карточка героя с уровнем, в «Ещё» — рейтинг и челленджи.
 */
class IgraPrefs private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("igra_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    /** Уровень, который человек уже видел, — чтобы один раз поздравить с новым. */
    var lastSeenLevel: Int
        get() = prefs.getInt(KEY_LEVEL, 0)
        set(value) = prefs.edit { putInt(KEY_LEVEL, value) }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEVEL = "last_level"

        @Volatile
        private var instance: IgraPrefs? = null

        fun getInstance(context: Context): IgraPrefs =
            instance ?: synchronized(this) {
                instance ?: IgraPrefs(context).also { instance = it }
            }
    }
}

/**
 * С кем человек работает чаще — запоминаем на телефоне (сервер тоже считает, но без сети
 * и сразу после отправки локальный счёт точнее). Ключ — id сотрудника, значение — сколько раз
 * отмечали напарником.
 */
class NaparnikiStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("naparniki", Context.MODE_PRIVATE)

    fun count(id: Int): Int = prefs.getInt(id.toString(), 0)

    fun remember(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        prefs.edit { ids.forEach { putInt(it.toString(), count(it) + 1) } }
    }

    /** Последние отмеченные — чтобы «как вчера» ставилось одной кнопкой. */
    var last: List<Int>
        get() = prefs.getString(KEY_LAST, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        set(value) = prefs.edit { putString(KEY_LAST, value.joinToString(",")) }

    companion object {
        private const val KEY_LAST = "_last"

        @Volatile
        private var instance: NaparnikiStore? = null

        fun getInstance(context: Context): NaparnikiStore =
            instance ?: synchronized(this) {
                instance ?: NaparnikiStore(context).also { instance = it }
            }
    }
}
