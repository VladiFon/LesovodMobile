package com.lesovod.mobile.ui.bot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.CompletedWorkStore
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TasksUiState(
    val items: List<WorkPlanItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val completingId: Int? = null,
    val error: String? = null,
)

class TasksViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val completedStore = CompletedWorkStore(application)

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    fun loadTasks() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = repository.listWorkPlan()
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoading = false, items = it) },
                onFailure = { _uiState.value.copy(isLoading = false, error = it.message) },
            )
        }
    }

    fun completeTask(id: Int) {
        _uiState.value = _uiState.value.copy(completingId = id, error = null)
        viewModelScope.launch {
            val result = repository.completeWorkPlanItem(id)
            result.onFailure {
                _uiState.value = _uiState.value.copy(
                    completingId = null,
                    error = it.message ?: "Не удалось отметить задачу выполненной",
                )
            }
            if (result.isSuccess) {
                // делянка на карте перекрасится в цвет "выполнено"
                _uiState.value.items.firstOrNull { it.id == id }?.let { item ->
                    val kv = item.kvartal
                    val vd = item.vydel
                    if (!kv.isNullOrBlank() && !vd.isNullOrBlank()) completedStore.add(item.lesnichestvo, kv, vd)
                }
                _uiState.value = _uiState.value.copy(
                    completingId = null,
                    items = _uiState.value.items.filterNot { it.id == id },
                )
            }
        }
    }
}
