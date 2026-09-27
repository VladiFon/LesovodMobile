package com.lesovod.mobile.ui.trelevka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing

/** Экран 6 редизайна «Поляна» (docs/SCREENS.md) — «Трелёвка». */
@Composable
fun TrelevkaScreen(onBack: () -> Unit, viewModel: TrelevkaViewModel = viewModel()) {
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
            Text("Трелёвка", style = MaterialTheme.typography.titleSmall)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        ) {
            if (state.submitted) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Отправлено", tone = ChipTone.OK)
                    SecondaryButton(text = "Отправить ещё одну", onClick = viewModel::resetSubmitted)
                }
            } else if (state.queuedOffline) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatusChip(text = "Нет сети — трелёвка сохранена на устройстве", tone = ChipTone.WARN)
                    Text(
                        "Она отправится автоматически, как только появится связь.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(text = "Отправить ещё одну", onClick = viewModel::resetSubmitted)
                }
            } else {
                OutlinedTextField(
                    value = state.delyankaItemId,
                    onValueChange = viewModel::onDelyankaItemIdChange,
                    label = { Text("ID делянки (необязательно)") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.otkuda,
                    onValueChange = viewModel::onOtkudaChange,
                    label = { Text("Откуда") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.kuda,
                    onValueChange = viewModel::onKudaChange,
                    label = { Text("Куда") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.obyom,
                    onValueChange = viewModel::onObyomChange,
                    label = { Text("Объём, м³") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.error != null) {
                    StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
                }

                PrimaryButton(
                    text = "Отправить",
                    onClick = viewModel::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.padding(bottom = Spacing.xl),
                    icon = if (state.isSubmitting) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                )
            }
        }
    }
}
