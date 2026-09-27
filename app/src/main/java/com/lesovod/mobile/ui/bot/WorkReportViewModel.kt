package com.lesovod.mobile.ui.bot

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.location.getCurrentLocationOrNull
import com.lesovod.mobile.data.location.hasLocationPermission
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.DelyankaLocationMatchDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkReportUiState(
    val tipRaboty: String = "",
    val kvartal: String = "",
    val vydelInput: String = "",
    val vydels: List<String> = emptyList(),
    val opisanie: String = "",
    val photoUri: Uri? = null,
    val delyankaSearchKvartal: String = "",
    val delyankaSearchVydel: String = "",
    val isSearchingDelyanka: Boolean = false,
    val delyankaSearchError: String? = null,
    val delyankaMatches: List<DelyankaLocationMatchDto> = emptyList(),
    val selectedDelyanka: DelyankaLocationMatchDto? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val queuedOffline: Boolean = false,
)

class WorkReportViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val queueManager = OfflineQueueManager.getInstance(application)

    val session = sessionManager.session

    private val _uiState = MutableStateFlow(WorkReportUiState())
    val uiState = _uiState.asStateFlow()

    fun onTipRabotyChange(value: String) {
        _uiState.value = _uiState.value.copy(tipRaboty = value, error = null)
    }

    fun onKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(kvartal = value, error = null)
    }

    fun onVydelInputChange(value: String) {
        _uiState.value = _uiState.value.copy(vydelInput = value)
    }

    fun addVydel() {
        val value = _uiState.value.vydelInput.trim()
        if (value.isEmpty() || _uiState.value.vydels.contains(value)) return
        _uiState.value = _uiState.value.copy(
            vydels = _uiState.value.vydels + value,
            vydelInput = "",
        )
    }

    fun removeVydel(value: String) {
        _uiState.value = _uiState.value.copy(vydels = _uiState.value.vydels - value)
    }

    fun onOpisanieChange(value: String) {
        _uiState.value = _uiState.value.copy(opisanie = value)
    }

    fun onPhotoPicked(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    fun onDelyankaSearchKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(delyankaSearchKvartal = value, delyankaSearchError = null)
    }

    fun onDelyankaSearchVydelChange(value: String) {
        _uiState.value = _uiState.value.copy(delyankaSearchVydel = value, delyankaSearchError = null)
    }

    fun searchDelyanka() {
        val state = _uiState.value
        val kvartal = state.delyankaSearchKvartal.trim()
        val vydel = state.delyankaSearchVydel.trim()
        if (kvartal.isEmpty() || vydel.isEmpty()) return

        _uiState.value = state.copy(isSearchingDelyanka = true, delyankaSearchError = null, delyankaMatches = emptyList())
        viewModelScope.launch {
            val result = repository.findDelyankaByLocation(kvartal, vydel)
            _uiState.value = result.fold(
                onSuccess = { matches ->
                    _uiState.value.copy(
                        isSearchingDelyanka = false,
                        delyankaMatches = matches,
                        delyankaSearchError = if (matches.isEmpty()) "Делянка с таким кварталом и выделом не найдена" else null,
                    )
                },
                onFailure = {
                    _uiState.value.copy(isSearchingDelyanka = false, delyankaSearchError = it.message ?: "Не удалось найти делянку")
                },
            )
        }
    }

    fun selectDelyanka(match: DelyankaLocationMatchDto) {
        _uiState.value = _uiState.value.copy(selectedDelyanka = match, delyankaMatches = emptyList())
    }

    fun clearSelectedDelyanka() {
        _uiState.value = _uiState.value.copy(selectedDelyanka = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.tipRaboty.isBlank()) {
            _uiState.value = state.copy(error = "Укажите тип работы")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val location = if (hasLocationPermission(context)) getCurrentLocationOrNull(context) else null
            val lat = location?.latitude
            val lon = location?.longitude

            var photoPath: String? = null
            val uri = state.photoUri
            if (uri != null) {
                val uploadResult = repository.uploadPhoto(context, uri)
                val uploadError = uploadResult.exceptionOrNull()
                if (uploadError is ConnectivityException) {
                    queueManager.enqueueReport(
                        tipRaboty = state.tipRaboty.trim(),
                        kvartal = state.kvartal,
                        vydels = state.vydels,
                        opisanie = state.opisanie,
                        photoUri = uri,
                        uploadedPhotoPath = null,
                        lat = lat,
                        lon = lon,
                        delyankaItemId = state.selectedDelyanka?.itemId,
                    )
                    _uiState.value = WorkReportUiState(queuedOffline = true)
                    return@launch
                }
                uploadResult.onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = it.message ?: "Не удалось загрузить фото",
                    )
                    return@launch
                }
                photoPath = uploadResult.getOrNull()
            }

            val result = repository.submitReport(
                tipRaboty = state.tipRaboty.trim(),
                kvartal = state.kvartal,
                vydels = state.vydels,
                opisanie = state.opisanie,
                photoPath = photoPath,
                lat = lat,
                lon = lon,
                delyankaItemId = state.selectedDelyanka?.itemId,
            )
            val error = result.exceptionOrNull()
            if (error is ConnectivityException) {
                queueManager.enqueueReport(
                    tipRaboty = state.tipRaboty.trim(),
                    kvartal = state.kvartal,
                    vydels = state.vydels,
                    opisanie = state.opisanie,
                    photoUri = null,
                    uploadedPhotoPath = photoPath,
                    lat = lat,
                    lon = lon,
                    delyankaItemId = state.selectedDelyanka?.itemId,
                )
                _uiState.value = WorkReportUiState(queuedOffline = true)
                return@launch
            }
            _uiState.value = result.fold(
                onSuccess = { WorkReportUiState(submitted = true) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить отчёт") },
            )
        }
    }

    fun resetSubmitted() {
        _uiState.value = WorkReportUiState()
    }
}
