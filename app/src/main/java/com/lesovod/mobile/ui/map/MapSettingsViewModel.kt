package com.lesovod.mobile.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.repository.DownloadState
import com.lesovod.mobile.data.repository.MapDataHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapSettingsState(
    val lesnichestva: Map<String, Int> = emptyMap(),
    val defaultLesnichestvo: String? = null,
    val download: DownloadState = DownloadState(),
    /** Когда карта выбранного по умолчанию лесничества скачивалась в последний раз. */
    val lastDownloadAt: Long? = null,
    val error: String? = null,
)

/** Настройки карты в профиле: лесничество по умолчанию и скачивание карты на устройство. */
class MapSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val hub = MapDataHub.getInstance(application)

    private val _state = MutableStateFlow(MapSettingsState(defaultLesnichestvo = hub.prefs.defaultLesnichestvo))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            hub.repository.listLesnichestva().fold(
                onSuccess = { _state.value = _state.value.copy(lesnichestva = it, error = null) },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
        }
        viewModelScope.launch {
            hub.download.collect {
                _state.value = _state.value.copy(download = it, lastDownloadAt = lastDownloadFor(_state.value.defaultLesnichestvo))
            }
        }
        _state.value = _state.value.copy(lastDownloadAt = lastDownloadFor(_state.value.defaultLesnichestvo))
    }

    private fun lastDownloadFor(name: String?): Long? = name?.let(hub.prefs::lastDownloadAt)

    fun setDefault(name: String) {
        hub.prefs.defaultLesnichestvo = name
        _state.value = _state.value.copy(defaultLesnichestvo = name, lastDownloadAt = lastDownloadFor(name))
    }

    fun download() {
        val name = _state.value.defaultLesnichestvo ?: return
        val num = _state.value.lesnichestva[name] ?: return
        hub.downloadLesnichestvo(name, num)
    }

    fun cancelDownload() = hub.cancelDownload()
}
