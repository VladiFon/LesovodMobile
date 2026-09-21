package com.lesovod.mobile.ui.trelevka

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrelevkaUiState(
    val kvartal: String = "",
    val vydel: String = "",
    val otkuda: String = "",
    val kuda: String = "",
    val obyom: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

class TrelevkaViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

    private val _uiState = MutableStateFlow(TrelevkaUiState())
    val uiState = _uiState.asStateFlow()

    fun onKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(kvartal = value, error = null)
    }

    fun onVydelChange(value: String) {
        _uiState.value = _uiState.value.copy(vydel = value, error = null)
    }

    fun onOtkudaChange(value: String) {
        _uiState.value = _uiState.value.copy(otkuda = value, error = null)
    }

    fun onKudaChange(value: String) {
        _uiState.value = _uiState.value.copy(kuda = value, error = null)
    }

    fun onObyomChange(value: String) {
        _uiState.value = _uiState.value.copy(obyom = value, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.otkuda.isBlank() || state.kuda.isBlank()) {
            _uiState.value = state.copy(error = "Укажите откуда и куда")
            return
        }
        val volume = state.obyom.trim().replace(',', '.').toDoubleOrNull()
        if (volume == null || volume <= 0) {
            _uiState.value = state.copy(error = "Укажите объём числом")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val result = repository.submitTrelevka(
                kvartal = state.kvartal.takeIf { it.isNotBlank() },
                vydel = state.vydel.takeIf { it.isNotBlank() },
                otkuda = state.otkuda.trim(),
                kuda = state.kuda.trim(),
                obyom = volume,
            )
            _uiState.value = result.fold(
                onSuccess = { TrelevkaUiState(submitted = true) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить") },
            )
        }
    }

    fun resetSubmitted() {
        _uiState.value = TrelevkaUiState()
    }
}
