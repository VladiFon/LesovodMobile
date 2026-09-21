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

data class BreakdownUiState(
    val detailText: String = "",
    val photoUri: Uri? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

class BreakdownViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

    private val _uiState = MutableStateFlow(BreakdownUiState())
    val uiState = _uiState.asStateFlow()

    fun onDetailTextChange(value: String) {
        _uiState.value = _uiState.value.copy(detailText = value, error = null)
    }

    fun onPhotoPicked(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    fun submit() {
        val state = _uiState.value
        if (state.detailText.isBlank()) {
            _uiState.value = state.copy(error = "Опишите поломку")
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

            val result = repository.submitBreakdown(state.detailText.trim(), photoPath)
            _uiState.value = result.fold(
                onSuccess = { BreakdownUiState(submitted = true) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить") },
            )
        }
    }
}
