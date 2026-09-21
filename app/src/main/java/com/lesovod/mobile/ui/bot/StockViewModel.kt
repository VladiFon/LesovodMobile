package com.lesovod.mobile.ui.bot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockUiState(
    val kvartal: String = "",
    val vydel: String = "",
    val lesoseka: String = "",
    val delyanki: List<DelyankaDto> = emptyList(),
    val isLoadingDelyanki: Boolean = false,
    val isLoadingRemaining: Boolean = false,
    val remaining: RemainingResponseDto? = null,
    val error: String? = null,
)

class StockViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

    private val _uiState = MutableStateFlow(StockUiState())
    val uiState = _uiState.asStateFlow()

    fun onKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(kvartal = value, delyanki = emptyList(), error = null)
    }

    fun onVydelChange(value: String) {
        _uiState.value = _uiState.value.copy(vydel = value)
    }

    fun onLesosekaChange(value: String) {
        _uiState.value = _uiState.value.copy(lesoseka = value)
    }

    fun loadDelyanki() {
        val kvartal = _uiState.value.kvartal.trim()
        if (kvartal.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Укажите квартал")
            return
        }

        _uiState.value = _uiState.value.copy(isLoadingDelyanki = true, error = null)
        viewModelScope.launch {
            val result = repository.listDelyanki(kvartal)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoadingDelyanki = false, delyanki = it) },
                onFailure = { _uiState.value.copy(isLoadingDelyanki = false, error = it.message) },
            )
        }
    }

    fun selectDelyanka(delyanka: DelyankaDto) {
        _uiState.value = _uiState.value.copy(
            vydel = delyanka.vydel,
            lesoseka = delyanka.lesosekaNomer.orEmpty(),
        )
        loadRemaining()
    }

    fun loadRemaining() {
        val state = _uiState.value
        val kvartal = state.kvartal.trim()
        val vydel = state.vydel.trim()
        if (kvartal.isEmpty() || vydel.isEmpty()) {
            _uiState.value = state.copy(error = "Укажите квартал и выдел")
            return
        }

        _uiState.value = state.copy(isLoadingRemaining = true, error = null)
        viewModelScope.launch {
            val result = repository.getRemaining(kvartal, vydel, state.lesoseka)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoadingRemaining = false, remaining = it) },
                onFailure = { _uiState.value.copy(isLoadingRemaining = false, error = it.message, remaining = null) },
            )
        }
    }
}
