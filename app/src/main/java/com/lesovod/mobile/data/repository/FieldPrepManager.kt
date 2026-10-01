package com.lesovod.mobile.data.repository

import android.content.Context
import com.lesovod.mobile.data.local.FieldDataCache
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.session.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Состояние кнопки «Подготовиться к выезду». */
data class FieldPrepState(
    val running: Boolean = false,
    /** «Готово: N делянок, данные на чч:мм» — после удачной подготовки. */
    val result: String? = null,
    val error: String? = null,
)

/**
 * «Подготовиться к выезду»: пока есть связь, одним нажатием сохраняет на устройство всё, что нужно
 * в лесу без сети, — список «Мои делянки», остатки по каждой своей делянке (если своих нет — по всем
 * активным), задачи плана работ — и запускает скачивание границ карты лесничества по умолчанию.
 */
class FieldPrepManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(appContext))
    private val cache = FieldDataCache(appContext)
    private val hub = MapDataHub.getInstance(appContext)

    private val _state = MutableStateFlow(FieldPrepState())
    val state: StateFlow<FieldPrepState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Запускает подготовку в своей области — она не прервётся, если уйти с экрана. */
    fun prepare() {
        if (_state.value.running) return
        _state.value = FieldPrepState(running = true)
        scope.launch { run() }
    }

    private suspend fun run() {

        val delyanki = repository.listMyDelyanki().getOrElse {
            _state.value = FieldPrepState(error = it.message ?: "Не удалось загрузить делянки")
            return
        }
        cache.saveMyDelyanki(delyanki)

        val targets = delyanki.filter { it.moya }.ifEmpty { delyanki }
        var saved = 0
        for (d in targets) {
            repository.getRemainingByDelyanka(d.delyankaId).onSuccess {
                cache.saveRemaining(d.delyankaId, it)
                saved++
            }
        }

        // задачи есть только у рабочих учёток; у остальных сервер отвечает 403 — это не ошибка подготовки
        repository.listWorkPlan().onSuccess { cache.saveWorkPlan(it) }

        // границы карты — тем же скачиванием, что и кнопка «Скачать карту» в профиле
        val default = hub.prefs.defaultLesnichestvo
        if (default != null) {
            hub.repository.listLesnichestva().onSuccess { map ->
                map[default]?.let { num -> if (!hub.download.value.running) hub.downloadLesnichestvo(default, num) }
            }
        }

        val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
        _state.value = FieldPrepState(result = "Готово: $saved ${pluralDelyanki(saved)}, данные на $time")
    }

    private fun pluralDelyanki(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> "делянок"
            mod10 == 1 -> "делянка"
            mod10 in 2..4 -> "делянки"
            else -> "делянок"
        }
    }

    companion object {
        @Volatile
        private var instance: FieldPrepManager? = null

        fun getInstance(context: Context): FieldPrepManager =
            instance ?: synchronized(this) {
                instance ?: FieldPrepManager(context).also { instance = it }
            }
    }
}
