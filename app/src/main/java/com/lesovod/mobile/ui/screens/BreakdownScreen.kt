package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.bot.BreakdownViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.components.UrgentErrorButton
import com.lesovod.mobile.ui.theme.Spacing

/** Экран 5 редизайна «Поляна» (docs/SCREENS.md) — «Поломка техники». */
@Composable
fun BreakdownScreen(
    onBack: () -> Unit,
    viewModel: BreakdownViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Поломка техники", style = MaterialTheme.typography.titleSmall)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            if (state.submitted) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Сообщение отправлено", tone = ChipTone.OK)
                    Text(
                        "Мастер и лесничий получат уведомление о поломке.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Вернуться к отчёту", onClick = onBack)
                }
            } else if (state.queuedOffline) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Нет сети — сообщение сохранено на устройстве", tone = ChipTone.WARN)
                    Text(
                        "Оно отправится автоматически, как только появится связь.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Вернуться к отчёту", onClick = onBack)
                }
            } else {
                OutlinedTextField(
                    value = state.detailText,
                    onValueChange = viewModel::onDetailTextChange,
                    label = { Text("Что случилось") },
                    enabled = !state.isSubmitting,
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                PhotoPickerField(uri = state.photoUri, onPicked = viewModel::onPhotoPicked)

                if (state.error != null) {
                    StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
                }

                if (state.isSubmitting) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                // Заливка `error`, не `tertiary` — сознательное исключение: это тревога,
                // а не «следующий шаг» (см. docs/COMPONENTS.md редизайна «Поляна»).
                UrgentErrorButton(
                    text = "Сообщить о поломке",
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.padding(bottom = Spacing.xl),
                )
            }
        }
    }
}
