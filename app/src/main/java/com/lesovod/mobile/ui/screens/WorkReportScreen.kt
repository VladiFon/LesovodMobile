package com.lesovod.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.session.canReportBreakdown
import com.lesovod.mobile.data.session.canTrelevka
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.bot.WorkReportViewModel
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestSuccess

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            if (state.submitted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForestSuccess.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Отчёт отправлен", color = ForestSuccess, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Он появится в журнале у лесничего на проверке.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        OutlinedButton(
                            onClick = viewModel::resetSubmitted,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Отправить ещё один")
                        }
                    }
                }
            } else if (state.queuedOffline) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForestAccent.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Нет сети — отчёт сохранён на устройстве", color = ForestAccent, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Он отправится автоматически, как только появится связь.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        OutlinedButton(
                            onClick = viewModel::resetSubmitted,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Отправить ещё один")
                        }
                    }
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

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    OutlinedButton(
                        onClick = viewModel::addVydel,
                        enabled = !state.isSubmitting && state.vydelInput.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Добавить")
                    }
                }

                if (state.vydels.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        state.vydels.forEach { vydel ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeVydel(vydel) },
                                label = { Text(vydel) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Удалить",
                                        modifier = Modifier.size(16.dp),
                                    )
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Отправить отчёт")
                    }
                }
            }

            if (canReportBreakdown) {
                OutlinedButton(
                    onClick = onReportBreakdown,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = if (canTrelevka) 0.dp else 24.dp),
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null)
                    Text("Сообщить о поломке техники", modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (canTrelevka) {
                OutlinedButton(
                    onClick = onReportTrelevka,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                ) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = null)
                    Text("Отметить трелёвку", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
