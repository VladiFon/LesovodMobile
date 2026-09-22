package com.lesovod.mobile.ui.trelevka

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrelevkaUiState(
    val delyankaItemId: String = "",
    val otkuda: String = "",
    val kuda: String = "",
    val obyom: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val queuedOffline: Boolean = false,
)

class TrelevkaViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val queueManager = OfflineQueueManager.getInstance(application)

    private val _uiState = MutableStateFlow(TrelevkaUiState())
    val uiState = _uiState.asStateFlow()

    fun onDelyankaItemIdChange(value: String) {
        _uiState.value = _uiState.value.copy(delyankaItemId = value, error = null)
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
        val delyankaItemId = state.delyankaItemId.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (state.delyankaItemId.isNotBlank() && delyankaItemId == null) {
            _uiState.value = state.copy(error = "ID делянки указывается числом")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val result = repository.submitTrelevka(
                otkuda = state.otkuda.trim(),
                kuda = state.kuda.trim(),
                obyom = volume,
                delyankaItemId = delyankaItemId,
            )
            val error = result.exceptionOrNull()
            if (error is ConnectivityException) {
                queueManager.enqueueTrelevka(state.otkuda.trim(), state.kuda.trim(), volume, delyankaItemId)
                _uiState.value = TrelevkaUiState(queuedOffline = true)
                return@launch
            }
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
