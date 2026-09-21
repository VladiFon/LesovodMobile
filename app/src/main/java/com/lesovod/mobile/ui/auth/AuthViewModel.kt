package com.lesovod.mobile.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.repository.AuthRepository
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
    data object Success : LoginUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = AuthRepository(NetworkModule.api, sessionManager)

    val session = sessionManager.session
    val rememberedLogin: String? get() = sessionManager.rememberedLogin

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun login(login: String, pin: String, rememberMe: Boolean) {
        if (login.isBlank() || pin.isBlank()) {
            _uiState.value = LoginUiState.Error("Введите логин и PIN-код")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val trimmedLogin = login.trim()
            val result = repository.login(trimmedLogin, pin.trim())
            result.onSuccess {
                if (rememberMe) sessionManager.rememberLogin(trimmedLogin) else sessionManager.forgetLogin()
            }
            _uiState.value = result.fold(
                onSuccess = { LoginUiState.Success },
                onFailure = { LoginUiState.Error(it.message ?: "Не удалось войти") },
            )
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = LoginUiState.Idle
        }
    }
}
