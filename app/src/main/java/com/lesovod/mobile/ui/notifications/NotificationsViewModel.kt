package com.lesovod.mobile.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.notifications.IncomingMarks
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val items: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Короткое сообщение после ответа на метку. */
    val message: String? = null,
)

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val badgeManager = NotificationsBadgeManager.getInstance(application)

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = repository.listNotifications()
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoading = false, items = it) },
                onFailure = { _uiState.value.copy(isLoading = false, error = it.message ?: "Не удалось загрузить уведомления") },
            )
            badgeManager.refresh()
        }
    }

    /** Отмечает прочитанным сразу на экране (не дожидаясь ответа сервера) и подтверждает на сервере. */
    fun markRead(id: Int) {
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        if (item.isRead) return
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map { if (it.id == id) it.copy(isRead = true) else it },
        )
        viewModelScope.launch {
            repository.markNotificationRead(id)
            badgeManager.refresh()
        }
    }

    /** Метка от коллеги: принять (появится на моей карте) или отклонить. */
    fun answerMark(item: NotificationItem, accept: Boolean) {
        val shareId = item.relatedId ?: return
        viewModelScope.launch {
            repository.answerGeoNoteShare(shareId, accept).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items.map { if (it.id == item.id) it.copy(isRead = true, answered = accept) else it },
                        message = if (accept) "Метка добавлена на вашу карту" else "Метка отклонена",
                    )
                    IncomingMarks.markersChanged()
                    badgeManager.refresh()
                },
                onFailure = { _uiState.value = _uiState.value.copy(message = it.message ?: "Не удалось ответить на метку") },
            )
        }
    }

    fun markAllRead() {
        if (_uiState.value.items.all { it.isRead }) return
        _uiState.value = _uiState.value.copy(items = _uiState.value.items.map { it.copy(isRead = true) })
        viewModelScope.launch {
            repository.markAllNotificationsRead()
            badgeManager.refresh()
        }
    }
}
