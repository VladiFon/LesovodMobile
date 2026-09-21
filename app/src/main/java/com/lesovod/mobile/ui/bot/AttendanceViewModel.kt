package com.lesovod.mobile.ui.bot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.PendingActionType
import com.lesovod.mobile.data.location.getCurrentLocationOrNull
import com.lesovod.mobile.data.location.hasLocationPermission
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.AttendanceMarkDto
import com.lesovod.mobile.data.network.dto.AttendanceStatus
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val latest: AttendanceMarkDto? = null,
    val isLoadingLatest: Boolean = false,
    val isSubmitting: Boolean = false,
    val submittingStatus: AttendanceStatus? = null,
    val error: String? = null,
    val queuedOffline: AttendanceStatus? = null,
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val queueManager = OfflineQueueManager.getInstance(application)

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadLatest()
        viewModelScope.launch {
            queueManager.completed.collect { action ->
                if (action.type == PendingActionType.ATTENDANCE) {
                    _uiState.value = _uiState.value.copy(queuedOffline = null)
                    loadLatest()
                }
            }
        }
    }

    fun loadLatest() {
        _uiState.value = _uiState.value.copy(isLoadingLatest = true, error = null)
        viewModelScope.launch {
            val result = repository.getLatestAttendance()
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoadingLatest = false, latest = it) },
                onFailure = { _uiState.value.copy(isLoadingLatest = false, error = it.message) },
            )
        }
    }

    fun mark(status: AttendanceStatus) {
        _uiState.value = _uiState.value.copy(isSubmitting = true, submittingStatus = status, error = null, queuedOffline = null)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val location = if (hasLocationPermission(context)) getCurrentLocationOrNull(context) else null

            val result = repository.submitAttendance(status, location?.latitude, location?.longitude)
            val error = result.exceptionOrNull()
            if (error is ConnectivityException) {
                queueManager.enqueueAttendance(status, location?.latitude, location?.longitude)
                _uiState.value = _uiState.value.copy(isSubmitting = false, submittingStatus = null, queuedOffline = status)
                return@launch
            }
            result.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submittingStatus = null,
                    error = it.message ?: "Не удалось отправить отметку",
                )
            }
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, submittingStatus = null)
                loadLatest()
            }
        }
    }
}
