package com.lesovod.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.session.canReportBreakdown
import com.lesovod.mobile.data.session.canTrelevka
import com.lesovod.mobile.ui.bot.WorkReportViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing

/** Экран 4 редизайна «Поляна» (docs/SCREENS.md) — «Отчёт о работе». */
@Composable
fun WorkReportScreen(
    onReportBreakdown: () -> Unit,
    onReportTrelevka: () -> Unit = {},
    viewModel: WorkReportViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val session by viewModel.session.collectAsState()
    val canReportBreakdown = session?.role?.canReportBreakdown == true
    val canTrelevka = session?.role?.canTrelevka == true

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* отчёт всё равно отправится — геометка просто не приложится без разрешения */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Отчёт о работе")

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        ) {
            if (state.submitted) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Отчёт отправлен", tone = ChipTone.OK)
                    Text(
                        "Он появится в журнале у лесничего на проверке.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Отправить ещё один", onClick = viewModel::resetSubmitted)
                }
            } else if (state.queuedOffline) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Нет сети — отчёт сохранён на устройстве", tone = ChipTone.WARN)
                    Text(
                        "Он отправится автоматически, как только появится связь.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Отправить ещё один", onClick = viewModel::resetSubmitted)
                }
            } else {
                OutlinedTextField(
                    value = state.tipRaboty,
                    onValueChange = viewModel::onTipRabotyChange,
                    label = { Text("Тип работы") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.kvartal,
                    onValueChange = viewModel::onKvartalChange,
                    label = { Text("Квартал (необязательно)") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedTextField(
                        value = state.vydelInput,
                        onValueChange = viewModel::onVydelInputChange,
                        label = { Text("Выдел") },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Добавить",
                        onClick = viewModel::addVydel,
                        enabled = !state.isSubmitting && state.vydelInput.isNotBlank(),
                        modifier = Modifier.weight(0.6f),
                    )
                }

                if (state.vydels.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        state.vydels.forEach { vydel ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeVydel(vydel) },
                                label = { Text(vydel) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.opisanie,
                    onValueChange = viewModel::onOpisanieChange,
                    label = { Text("Что сделано (необязательно)") },
                    enabled = !state.isSubmitting,
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                PhotoPickerField(uri = state.photoUri, onPicked = viewModel::onPhotoPicked)

                if (state.error != null) {
                    StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
                }

                PrimaryButton(
                    text = "Отправить отчёт",
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    icon = if (state.isSubmitting) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                )
            }

            if (canReportBreakdown) {
                SecondaryButton(
                    text = "Сообщить о поломке техники",
                    onClick = onReportBreakdown,
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                )
            }

            if (canTrelevka) {
                SecondaryButton(
                    text = "Отметить трелёвку",
                    onClick = onReportTrelevka,
                    icon = { Icon(Icons.Filled.LocalShipping, contentDescription = null) },
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
