package com.lesovod.mobile.ui.igra

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.IgraPrefs
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.IgraDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class IgraUiState(
    val loading: Boolean = true,
    val data: IgraDto? = null,
    val error: String? = null,
    val period: String = "nedelya",
    /** Новый уровень с прошлого раза — показать поздравление один раз. */
    val novyyUroven: Int? = null,
)

/** Игровой режим: грузит /api/bot/igra/moy, держит копию на телефоне (без сети покажет прошлую). */
class IgraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))
    private val prefs = IgraPrefs.getInstance(application)
    private val cache = application.getSharedPreferences("igra_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(IgraUiState())
    val state = _state.asStateFlow()

    init {
        cache.getString("data", null)?.let { raw ->
            runCatching { json.decodeFromString<IgraDto>(raw) }.getOrNull()?.let { _state.value = _state.value.copy(data = it) }
        }
        load()
    }

    fun setPeriod(p: String) {
        if (p == _state.value.period) return
        _state.value = _state.value.copy(period = p)
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.igraMoy(_state.value.period).fold(
                onSuccess = { d ->
                    cache.edit { putString("data", json.encodeToString(d)) }
                    val lvl = d.geroy?.uroven
                    val seen = prefs.lastSeenLevel
                    val novyy = if (lvl != null && seen in 1 until lvl) lvl else null
                    if (lvl != null && lvl != seen) prefs.lastSeenLevel = lvl
                    _state.value = _state.value.copy(loading = false, data = d, novyyUroven = novyy ?: _state.value.novyyUroven)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Не удалось загрузить")
                },
            )
        }
    }

    fun zakrytPozdravlenie() {
        _state.value = _state.value.copy(novyyUroven = null)
    }
}
