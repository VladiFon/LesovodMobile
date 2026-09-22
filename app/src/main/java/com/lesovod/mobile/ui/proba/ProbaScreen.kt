package com.lesovod.mobile.ui.proba

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.ProbaResponse
import com.lesovod.mobile.ui.components.PhotoPickerField
import com.lesovod.mobile.ui.theme.ForestSuccess
import java.util.Calendar
import java.util.Locale

@Composable
fun ProbaScreen(onBack: () -> Unit, viewModel: ProbaViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Проба рубок ухода",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (state.result != null) {
                ProbaResultCard(result = state.result, onNewProba = viewModel::newProba)
            } else {
                ProbaForm(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ProbaResultCard(result: ProbaResponse?, onNewProba: () -> Unit) {
    if (result == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = ForestSuccess.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Проба сохранена", color = ForestSuccess, style = MaterialTheme.typography.titleMedium)

            ResultRow("Запас на укладках", result.obyomSkladTotal)
            ResultRow("Запас пробы", result.zapasProbyTotal)
            ResultRow("Запас на 1 га", result.zapasNa1Ga)
            ResultRow("Запас на лесосеке", result.zapasNaLesoseke)

            if (result.rows.isNotEmpty()) {
                Text(
                    "По укладкам",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                result.rows.forEach { row ->
                    Text(
                        "${row.poroda}: ${row.shirina}×${row.vysota}×${row.dlina}" +
                            (row.obyom?.let { " → %.3f м³".format(Locale.US, it) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(onClick = onNewProba, modifier = Modifier.padding(top = 12.dp)) {
                Text("Новая проба")
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: Double?) {
    if (value == null) return
    Text(
        "$label: %.3f".format(Locale.US, value),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ProbaForm(state: ProbaUiState, viewModel: ProbaViewModel) {
    val context = LocalContext.current

    OutlinedTextField(
        value = state.kvartal,
        onValueChange = viewModel::onKvartalChange,
        label = { Text("Квартал") },
        singleLine = true,
        enabled = !state.isSubmitting,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.vydel,
        onValueChange = viewModel::onVydelChange,
        label = { Text("Выдел") },
        singleLine = true,
        enabled = !state.isSubmitting,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.ploshadVydela,
        onValueChange = viewModel::onPloshadVydelaChange,
        label = { Text("Площадь выдела, га (необязательно)") },
        singleLine = true,
        enabled = !state.isSubmitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.dataZamera,
        onValueChange = {},
        readOnly = true,
        label = { Text("Дата замера") },
        singleLine = true,
        enabled = !state.isSubmitting,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    )
    OutlinedButton(
        onClick = {
            val parts = state.dataZamera.split("-").mapNotNull { it.toIntOrNull() }
            val calendar = Calendar.getInstance()
            if (parts.size == 3) calendar.set(parts[0], parts[1] - 1, parts[2])
            DatePickerDialog(
                context,
                { _, year, month, day -> viewModel.onDataZameraChange("%04d-%02d-%02d".format(year, month + 1, day)) },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
        enabled = !state.isSubmitting,
    ) {
        Text("Изменить дату")
    }

    Text(
        "Укладки",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )

    state.rows.forEach { row ->
        ProbaRowCard(
            row = row,
            canRemove = state.rows.size > 1,
            enabled = !state.isSubmitting,
            onPorodaChange = { viewModel.onRowPorodaChange(row.id, it) },
            onShirinaChange = { viewModel.onRowShirinaChange(row.id, it) },
            onVysotaChange = { viewModel.onRowVysotaChange(row.id, it) },
            onDlinaChange = { viewModel.onRowDlinaChange(row.id, it) },
            onRemove = { viewModel.removeRow(row.id) },
        )
    }

    OutlinedButton(onClick = viewModel::addRow, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
        Text("Добавить укладку")
    }

    Text(
        "Пробные площадки",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    OutlinedTextField(
        value = state.kolPloshadok,
        onValueChange = viewModel::onKolPloshadokChange,
        label = { Text("Количество площадок") },
        singleLine = true,
        enabled = !state.isSubmitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.ploshadPloshadki,
        onValueChange = viewModel::onPloshadPloshadkiChange,
        label = { Text("Площадь одной площадки, га") },
        singleLine = true,
        enabled = !state.isSubmitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        "Обязательные фото",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    PhotoPickerField(
        uri = state.fotoStolbDelyankiUri,
        onPicked = viewModel::onFotoStolbDelyankiChange,
        label = "Фото столба границы делянки",
    )
    PhotoPickerField(
        uri = state.fotoStolbProbyUri,
        onPicked = viewModel::onFotoStolbProbyChange,
        label = "Фото столба пробной площадки",
    )

    if (state.lesokulturyUchastki.isNotEmpty()) {
        Text(
            "Участок лесных культур (если работа велась на нём)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            state.lesokulturyUchastki.forEach { uchastok ->
                FilterChip(
                    selected = uchastok.id in state.selectedLesokulturyIds,
                    onClick = { viewModel.toggleLesokulturyUchastok(uchastok.id) },
                    label = { Text(uchastok.label) },
                )
            }
        }
    }

    if (state.error != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    val canSubmit = !state.isSubmitting && state.fotoStolbDelyankiUri != null && state.fotoStolbProbyUri != null
    Button(
        onClick = viewModel::submit,
        enabled = canSubmit,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text("Рассчитать и сохранить")
        }
    }
}

@Composable
private fun ProbaRowCard(
    row: ProbaRowInput,
    canRemove: Boolean,
    enabled: Boolean,
    onPorodaChange: (String) -> Unit,
    onShirinaChange: (String) -> Unit,
    onVysotaChange: (String) -> Unit,
    onDlinaChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.poroda,
                    onValueChange = onPorodaChange,
                    label = { Text("Порода") },
                    singleLine = true,
                    enabled = enabled,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать укладку")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.shirina,
                    onValueChange = onShirinaChange,
                    label = { Text("Ширина, м") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.vysota,
                    onValueChange = onVysotaChange,
                    label = { Text("Высота, м") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.dlina,
                    onValueChange = onDlinaChange,
                    label = { Text("Длина, м") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
