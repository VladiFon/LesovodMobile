package com.lesovod.mobile.ui.lesokultury

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import kotlinx.serialization.json.JsonElement
import java.util.Locale

/** Живой фильтр списка участков по подписи (в неё уже входят квартал/выдел/порода/делянка) — регистронезависимо. */
fun filterUchastki(uchastki: List<LesokulturyUchastok>, query: String): List<LesokulturyUchastok> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return uchastki
    return uchastki.filter { it.label.contains(trimmed, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UchastokPicker(uchastki: List<LesokulturyUchastok>, selected: LesokulturyUchastok?, onSelect: (LesokulturyUchastok) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(uchastki, query) { filterUchastki(uchastki, query) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            if (!it) query = ""
        },
    ) {
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
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; query = "" },
        ) {
            if (uchastki.size > 1) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск: квартал, выдел, порода…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                )
            }
            if (filtered.isEmpty()) {
                DropdownMenuItem(text = { Text("Ничего не найдено") }, onClick = {}, enabled = false)
            }
            filtered.forEach { uchastok ->
                DropdownMenuItem(
                    text = { Text(uchastok.label) },
                    onClick = { onSelect(uchastok); expanded = false; query = "" },
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
        SecondaryButton(text = "Добавить пробу", onClick = onAdd, enabled = enabled)
    }
}

/** Вкладки «Ввод» / «Итог» — только переключение отображения, считает всё та же ViewModel-логика. */
private enum class PorodyTab(val label: String) { VVOD("Ввод"), ITOG("Итог") }

/** Компактный вид числа — без «.0» для целых, один знак после запятой иначе. */
private fun formatCompact(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) {
        value.toLong().toString()
    } else {
        "%.1f".format(Locale.US, value)
    }

@Composable
fun RezultatySection(
    porody: List<String>,
    rezultaty: List<RezultatEntry>,
    proby: List<ProbaEntry>,
    enabled: Boolean,
    onTap: (String) -> Unit,
    onUndo: (String) -> Unit,
    onVysazhenoChange: (poroda: String, value: String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(PorodyTab.VVOD) }
    val totalRazmer = remember(proby) { totalRazmerM2(proby) }
    val multiplier = remember(proby) { perHectareMultiplier(proby) }

    Column(
        modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Результаты обследования",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (multiplier != null) {
                    Text(
                        "Считаем на ${formatCompact(totalRazmer)} м² (×${formatCompact(multiplier)} = шт/га)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PorodyTab.entries.forEach { option ->
                    FilterChip(
                        selected = tab == option,
                        onClick = { tab = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        when (tab) {
            PorodyTab.VVOD -> PorodyVvodTab(
                porody = porody,
                rezultaty = rezultaty,
                enabled = enabled,
                onTap = onTap,
                onUndo = onUndo,
                onVysazhenoChange = onVysazhenoChange,
                onRemove = onRemove,
            )
            PorodyTab.ITOG -> PorodyItogTab(rezultaty = rezultaty, multiplier = multiplier)
        }
    }
}

@Composable
private fun PorodyVvodTab(
    porody: List<String>,
    rezultaty: List<RezultatEntry>,
    enabled: Boolean,
    onTap: (String) -> Unit,
    onUndo: (String) -> Unit,
    onVysazhenoChange: (poroda: String, value: String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            "«+» — прижилось ещё одно, «−» — отменить последнее",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        porody.forEach { poroda ->
            val rezultat = rezultaty.firstOrNull { it.poroda == poroda }
            PorodaInputRow(
                poroda = poroda,
                rezultat = rezultat,
                enabled = enabled,
                onIncrement = { onTap(poroda) },
                onDecrement = { onUndo(poroda) },
                onVysazhenoChange = { onVysazhenoChange(poroda, it) },
                onRemove = { onRemove(poroda) },
            )
        }
    }
}

@Composable
private fun PorodaInputRow(
    poroda: String,
    rezultat: RezultatEntry?,
    enabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onVysazhenoChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val count = rezultat?.prizhilos ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softCard()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(poroda, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

            OutlinedIconButton(onClick = onDecrement, enabled = enabled && count > 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Уменьшить «прижилось», $poroda")
            }
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
            OutlinedIconButton(onClick = onIncrement, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Увеличить «прижилось», $poroda")
            }
            if (rezultat != null) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Filled.Close, contentDescription = "Убрать породу «$poroda»")
                }
            }
        }
        if (rezultat != null) {
            OutlinedTextField(
                value = rezultat.vysazheno,
                onValueChange = onVysazhenoChange,
                label = { Text("Высажено") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PorodyItogTab(rezultaty: List<RezultatEntry>, multiplier: Double?) {
    if (rezultaty.isEmpty()) {
        Text(
            "Пока нет отмеченных пород — переключитесь на «Ввод»",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rezultaty.forEach { rezultat ->
            val shtNaGa = multiplier?.let { rezultat.prizhilos * it }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(rezultat.poroda, style = MaterialTheme.typography.bodyLarge)
                Text(
                    shtNaGa?.let { "${formatCompact(it)} шт/га" } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val totalPrizhilos = rezultaty.sumOf { it.prizhilos }
        val totalShtNaGa = multiplier?.let { totalPrizhilos * it }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
        ) {
            Text("Всего", style = MaterialTheme.typography.titleMedium)
            Text(
                totalShtNaGa?.let { "${formatCompact(it)} шт/га" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun PreviewCard(preview: LesokulturyPreview) {
    if (preview.shtNaGa == null) return
    Column(modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m)) {
        Text(
            "Предварительный расчёт — не отправляется на сервер",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Штук на 1 га: %.0f".format(Locale.US, preview.shtNaGa),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        preview.naPloshad?.let {
            Text(
                "На всю площадь участка: %.0f шт.".format(Locale.US, it),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Ответ сервера после сохранения — схема на сервере не типизирована, показываем то, что реально пришло. */
@Composable
fun ServerResultCard(result: JsonElement, title: String, onNewCard: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        StatusChip(text = title, tone = ChipTone.OK)
        result.toDisplayRows().forEach { (key, value) ->
            Text("$key: $value", style = MaterialTheme.typography.bodyMedium)
        }
        SecondaryButton(text = "Новая карточка", onClick = onNewCard)
    }
}
