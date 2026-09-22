package com.lesovod.mobile.ui.lesokultury

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestSuccess
import kotlinx.serialization.json.JsonElement
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UchastokPicker(uchastki: List<LesokulturyUchastok>, selected: LesokulturyUchastok?, onSelect: (LesokulturyUchastok) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "Выберите участок",
            onValueChange = {},
            readOnly = true,
            label = { Text("Участок лесных культур") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uchastki.forEach { uchastok ->
                DropdownMenuItem(
                    text = { Text(uchastok.label) },
                    onClick = { onSelect(uchastok); expanded = false },
                )
            }
        }
    }
}

@Composable
fun GodPicker(god: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3).forEach { value ->
            FilterChip(
                selected = god == value,
                onClick = { onSelect(value) },
                label = { Text("$value-й год") },
            )
        }
    }
}

@Composable
fun ProbyList(
    proby: List<ProbaEntry>,
    enabled: Boolean,
    onNomerChange: (id: String, value: String) -> Unit,
    onRazmerChange: (id: String, value: String) -> Unit,
    onRemove: (id: String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Пробы", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        proby.forEach { proba ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = proba.nomer,
                    onValueChange = { onNomerChange(proba.id, it) },
                    label = { Text("Номер пробы") },
                    singleLine = true,
                    enabled = enabled,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = proba.razmer,
                    onValueChange = { onRazmerChange(proba.id, it) },
                    label = { Text("Размер, м²") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                if (proby.size > 1) {
                    IconButton(onClick = { onRemove(proba.id) }, enabled = enabled) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать пробу")
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Добавить пробу")
        }
    }
}

@Composable
fun RezultatySection(
    porody: List<String>,
    rezultaty: List<RezultatEntry>,
    enabled: Boolean,
    onTap: (String) -> Unit,
    onUndo: (String) -> Unit,
    onVysazhenoChange: (poroda: String, value: String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Результаты обследования",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Тап по породе — «прижилось» +1, долгое нажатие — отменить последний тап",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            porody.forEach { poroda ->
                PorodaChip(
                    poroda = poroda,
                    count = rezultaty.firstOrNull { it.poroda == poroda }?.prizhilos ?: 0,
                    enabled = enabled,
                    onTap = { onTap(poroda) },
                    onUndo = { onUndo(poroda) },
                )
            }
        }

        if (rezultaty.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                rezultaty.forEach { rezultat ->
                    RezultatRow(
                        rezultat = rezultat,
                        enabled = enabled,
                        onVysazhenoChange = { onVysazhenoChange(rezultat.poroda, it) },
                        onRemove = { onRemove(rezultat.poroda) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PorodaChip(poroda: String, count: Int, enabled: Boolean, onTap: () -> Unit, onUndo: () -> Unit) {
    val hasCount = count > 0
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (hasCount) ForestPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (hasCount) ForestPrimary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.combinedClickable(enabled = enabled, onClick = onTap, onLongClick = onUndo),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(poroda, style = MaterialTheme.typography.bodyMedium)
            if (hasCount) {
                Text(
                    " ×$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = ForestPrimary,
                )
            }
        }
    }
}

@Composable
private fun RezultatRow(rezultat: RezultatEntry, enabled: Boolean, onVysazhenoChange: (String) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rezultat.poroda, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Прижилось: ${rezultat.prizhilos}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = rezultat.vysazheno,
            onValueChange = onVysazhenoChange,
            label = { Text("Высажено") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Filled.Close, contentDescription = "Убрать породу")
        }
    }
}

@Composable
fun PreviewCard(preview: LesokulturyPreview) {
    if (preview.shtNaGa == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Предварительный расчёт — не отправляется на сервер",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Штук на 1 га: %.0f".format(Locale.US, preview.shtNaGa),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            preview.naPloshad?.let {
                Text(
                    "На всю площадь участка: %.0f шт.".format(Locale.US, it),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** Ответ сервера после сохранения — схема на сервере не типизирована, показываем то, что реально пришло. */
@Composable
fun ServerResultCard(result: JsonElement, title: String, onNewCard: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ForestSuccess.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = ForestSuccess, style = MaterialTheme.typography.titleMedium)
            result.toDisplayRows().forEach { (key, value) ->
                Text(
                    "$key: $value",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            OutlinedButton(onClick = onNewCard, modifier = Modifier.padding(top = 12.dp)) {
                Text("Новая карточка")
            }
        }
    }
}
