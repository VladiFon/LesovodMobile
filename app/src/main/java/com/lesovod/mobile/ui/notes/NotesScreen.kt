package com.lesovod.mobile.ui.notes

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.local.NoteReminderEntry
import com.lesovod.mobile.data.network.dto.NoteDto
import com.lesovod.mobile.data.network.dto.SentNoteDto
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.ForestSuccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* напоминание всё равно планируется — просто может не показаться без разрешения */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Заметки")

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            SendNoteSection(state = state, viewModel = viewModel)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SentNotesSection(state = state)

            if (state.canViewInbox) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                InboxSection(state = state, viewModel = viewModel)

                if (state.reminders.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    RemindersSection(state = state, viewModel = viewModel)
                }
            } else {
                Spacer24()
            }
        }
    }
}

@Composable
private fun Spacer24() {
    Spacer(Modifier.height(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendNoteSection(state: NotesUiState, viewModel: NotesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.sent) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ForestSuccess.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Заметка отправлена", color = ForestSuccess, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = viewModel::resetSent, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Написать ещё одну")
                    }
                }
            }
        } else {
            Text("Новая заметка", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = state.noteText,
                onValueChange = viewModel::onNoteTextChange,
                label = { Text("Текст заметки") },
                enabled = !state.isSending,
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.selectedRecipient?.fio ?: "Всем",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Получатель") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Всем") },
                        onClick = { viewModel.selectRecipient(null); expanded = false },
                    )
                    state.recipients.forEach { recipient ->
                        DropdownMenuItem(
                            text = { Text(recipient.fio) },
                            onClick = { viewModel.selectRecipient(recipient); expanded = false },
                        )
                    }
                }
            }

            if (state.recipientsError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Не удалось загрузить получателей: ${state.recipientsError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::loadRecipients) { Text("Повторить") }
                }
            }

            if (state.sendError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.sendError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = viewModel::sendNote,
                enabled = !state.isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Отправить")
                }
            }
        }
    }
}

@Composable
private fun SentNotesSection(state: NotesUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Мои заметки", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        if (state.isLoadingSent) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (state.sentError != null) {
            Text(state.sentError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else if (state.sentNotes.isEmpty()) {
            Text("Вы ещё не отправляли заметок", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            state.sentNotes.forEach { note -> SentNoteCard(note) }
        }
    }
}

@Composable
private fun SentNoteCard(note: SentNoteDto) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(note.recipientFio ?: "Всем", note.createdAt).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun InboxSection(state: NotesUiState, viewModel: NotesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Входящие", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        if (state.isLoadingInbox) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (state.inboxError != null) {
            Text(state.inboxError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else if (state.inbox.isEmpty()) {
            Text("Заметок нет", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            state.inbox.forEach { note ->
                NoteCard(note = note, onRemind = { viewModel.openReminderPicker(note.id) })
                if (state.reminderNoteId == note.id) {
                    ReminderPicker(
                        onPick15 = { viewModel.scheduleReminderInMinutes(note.id, note.text, 15) },
                        onPick60 = { viewModel.scheduleReminderInMinutes(note.id, note.text, 60) },
                        onPickTomorrow = { viewModel.scheduleReminderTomorrowMorning(note.id, note.text) },
                        onPickCustom = { hour, minute -> viewModel.scheduleReminderAtTime(note.id, note.text, hour, minute) },
                        onDismiss = viewModel::dismissReminderPicker,
                    )
                }
            }
        }

        Spacer24()
    }
}

@Composable
private fun NoteCard(note: NoteDto, onRemind: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(note.authorFio, note.createdAt).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemind) { Text("Напомнить через…") }
            }
        }
    }
}

@Composable
private fun RemindersSection(state: NotesUiState, viewModel: NotesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Напоминания", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        state.reminders.forEach { reminder ->
            ReminderEntryCard(reminder = reminder, onCancel = { viewModel.cancelReminder(reminder.noteId) })
        }

        Spacer24()
    }
}

@Composable
private fun ReminderEntryCard(reminder: NoteReminderEntry, onCancel: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.noteText, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    formatReminderTime(reminder.triggerAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onCancel) { Text("Отменить") }
        }
    }
}

private fun formatReminderTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))

@Composable
private fun ReminderPicker(
    onPick15: () -> Unit,
    onPick60: () -> Unit,
    onPickTomorrow: () -> Unit,
    onPickCustom: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onPick15, label = { Text("Через 15 мин") })
                AssistChip(onClick = onPick60, label = { Text("Через 1 час") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onPickTomorrow, label = { Text("Завтра утром") })
                AssistChip(
                    onClick = {
                        val now = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onPickCustom(hour, minute) },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    label = { Text("Своё время") },
                )
            }
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    }
}
