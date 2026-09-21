package com.lesovod.mobile.ui.bot

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.repository.BotRepository
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
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

class WorkReportViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

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

    fun submit() {
        val state = _uiState.value
        if (state.tipRaboty.isBlank()) {
            _uiState.value = state.copy(error = "Укажите тип работы")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            var photoPath: String? = null
            val uri = state.photoUri
            if (uri != null) {
                val uploadResult = repository.uploadPhoto(getApplication<Application>(), uri)
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
            )
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
