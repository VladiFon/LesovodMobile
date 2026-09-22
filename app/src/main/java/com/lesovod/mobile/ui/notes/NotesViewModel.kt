package com.lesovod.mobile.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.NoteReminderEntry
import com.lesovod.mobile.data.local.NoteReminderStore
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.NoteDto
import com.lesovod.mobile.data.network.dto.RecipientDto
import com.lesovod.mobile.data.network.dto.SentNoteDto
import com.lesovod.mobile.data.notifications.NoteReminderScheduler
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.data.session.canViewNotesInbox
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotesUiState(
    val noteText: String = "",
    val recipients: List<RecipientDto> = emptyList(),
    val selectedRecipient: RecipientDto? = null,
    val isLoadingRecipients: Boolean = false,
    val recipientsError: String? = null,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val sent: Boolean = false,
    val canViewInbox: Boolean = false,
    val inbox: List<NoteDto> = emptyList(),
    val isLoadingInbox: Boolean = false,
    val inboxError: String? = null,
    val reminderNoteId: Int? = null,
    val sentNotes: List<SentNoteDto> = emptyList(),
    val isLoadingSent: Boolean = false,
    val sentError: String? = null,
    val reminders: List<NoteReminderEntry> = emptyList(),
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val reminderStore = NoteReminderStore(application)

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val canViewInbox = sessionManager.session.value?.role?.canViewNotesInbox == true
        _uiState.value = _uiState.value.copy(canViewInbox = canViewInbox, reminders = reminderStore.list())
        loadRecipients()
        loadSentNotes()
        if (canViewInbox) loadInbox()
    }

    fun loadSentNotes() {
        _uiState.value = _uiState.value.copy(isLoadingSent = true, sentError = null)
        viewModelScope.launch {
            val result = repository.listMyNotes()
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoadingSent = false, sentNotes = it) },
                onFailure = { _uiState.value.copy(isLoadingSent = false, sentError = it.message ?: "Не удалось загрузить отправленные заметки") },
            )
        }
    }

    fun onNoteTextChange(value: String) {
        _uiState.value = _uiState.value.copy(noteText = value, sendError = null)
    }

    fun selectRecipient(recipient: RecipientDto?) {
        _uiState.value = _uiState.value.copy(selectedRecipient = recipient)
    }

    fun loadRecipients() {
        _uiState.value = _uiState.value.copy(isLoadingRecipients = true, recipientsError = null)
        viewModelScope.launch {
            val result = repository.listRecipients()
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoadingRecipients = false, recipients = it, recipientsError = null) },
                onFailure = {
                    _uiState.value.copy(
                        isLoadingRecipients = false,
                        recipientsError = it.message ?: "Не удалось загрузить список получателей",
                    )
                },
            )
        }
    }

    fun sendNote() {
        val state = _uiState.value
        if (state.noteText.isBlank()) {
            _uiState.value = state.copy(sendError = "Напишите текст заметки")
            return
        }

        _uiState.value = state.copy(isSending = true, sendError = null)
        viewModelScope.launch {
            val result = repository.submitNote(state.noteText.trim(), state.selectedRecipient?.id)
            _uiState.value = result.fold(
                onSuccess = { NotesUiState(canViewInbox = state.canViewInbox, recipients = state.recipients, sent = true) },
                onFailure = { _uiState.value.copy(isSending = false, sendError = it.message ?: "Не удалось отправить") },
            )
            if (result.isSuccess) {
                loadSentNotes()
                if (state.canViewInbox) loadInbox()
            }
        }
    }

    fun resetSent() {
        _uiState.value = _uiState.value.copy(sent = false)
    }

    fun loadInbox() {
        _uiState.value = _uiState.value.copy(isLoadingInbox = true, inboxError = null)
        viewModelScope.launch {
            val result = repository.listNotes()
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoadingInbox = false, inbox = it) },
                onFailure = { _uiState.value.copy(isLoadingInbox = false, inboxError = it.message) },
            )
        }
    }

    fun openReminderPicker(noteId: Int) {
        _uiState.value = _uiState.value.copy(reminderNoteId = noteId)
    }

    fun dismissReminderPicker() {
        _uiState.value = _uiState.value.copy(reminderNoteId = null)
    }

    fun scheduleReminderInMinutes(noteId: Int, text: String, minutes: Int) {
        scheduleAt(noteId, text, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun scheduleReminderTomorrowMorning(noteId: Int, text: String) {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        scheduleAt(noteId, text, calendar.timeInMillis)
    }

    fun scheduleReminderAtTime(noteId: Int, text: String, hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        scheduleAt(noteId, text, calendar.timeInMillis)
    }

    private fun scheduleAt(noteId: Int, text: String, triggerAtMillis: Long) {
        NoteReminderScheduler.schedule(getApplication(), noteId, text, triggerAtMillis)
        _uiState.value = _uiState.value.copy(reminderNoteId = null, reminders = reminderStore.list())
    }

    fun cancelReminder(noteId: Int) {
        NoteReminderScheduler.cancel(getApplication(), noteId)
        _uiState.value = _uiState.value.copy(reminders = reminderStore.list())
    }
}
