package com.lesovod.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Сколько участков показываем в списке сразу — дальше просим уточнить поиск (список внутри прокручиваемой формы). */
private const val MAX_VISIBLE_UCHASTKI = 40

/** Живой фильтр участков: подстрока по подписи (квартал/выдел/порода/делянка/лесничество) + год создания. */
fun filterUchastkiByQueryAndGod(
    uchastki: List<LesokulturyUchastok>,
    query: String,
    god: String?,
): List<LesokulturyUchastok> {
    val q = query.trim()
    return uchastki.filter { u ->
        (god == null || u.god == god) && (q.isEmpty() || u.label.contains(q, ignoreCase = true))
    }
}

/**
 * Выбор участка лесных культур: поиск + фильтр по году создания культур (годы берутся из самих
 * участков) + список. Выбранный участок показан сверху карточкой; «Изменить» снова открывает список.
 */
@Composable
fun UchastokSelector(
    uchastki: List<LesokulturyUchastok>,
    selected: LesokulturyUchastok?,
    onSelect: (LesokulturyUchastok?) -> Unit,
    enabled: Boolean = true,
    title: String = "Участок лесных культур",
    allowClear: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var god by remember { mutableStateOf<String?>(null) }
    var expanded by remember(selected) { mutableStateOf(selected == null) }
    val gody = remember(uchastki) { uchastki.mapNotNull { it.god }.distinct().sortedDescending() }
    val filtered = remember(uchastki, query, god) { filterUchastkiByQueryAndGod(uchastki, query, god) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        if (selected != null && !expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    selected.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.s),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = "Изменить", onClick = { expanded = true }, enabled = enabled, modifier = Modifier.weight(1f))
                if (allowClear) {
                    SecondaryButton(text = "Убрать", onClick = { onSelect(null) }, enabled = enabled, modifier = Modifier.weight(1f))
                }
            }
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск: квартал, выдел, порода, делянка…") },
            singleLine = true,
            enabled = enabled,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (gody.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(selected = god == null, onClick = { god = null }, label = { Text("Все годы") }, enabled = enabled)
                gody.forEach { g ->
                    FilterChip(selected = god == g, onClick = { god = if (god == g) null else g }, label = { Text(g) }, enabled = enabled)
                }
            }
        }

        when {
            uchastki.isEmpty() -> Text(
                "Список участков пуст или не загрузился",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            filtered.isEmpty() -> Text(
                "Ничего не найдено",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filtered.take(MAX_VISIBLE_UCHASTKI).forEach { u ->
                    Text(
                        u.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (u.id == selected?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .softCard()
                            .clickable(enabled = enabled) {
                                onSelect(u)
                                expanded = false
                            }
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
                if (filtered.size > MAX_VISIBLE_UCHASTKI) {
                    Text(
                        "Показаны ${MAX_VISIBLE_UCHASTKI} из ${filtered.size} — уточните поиск или год",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Порода из справочника (GET /api/uhody/porody) — выпадающий список с поиском по мере ввода,
 * как на вебе. Если справочник не загрузился (нет сети), поле остаётся обычным вводом текста.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PorodaDropdown(
    value: String,
    porody: List<String>,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    label: String = "Порода",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(porody, value) {
        val q = value.trim()
        if (q.isEmpty() || porody.any { it.equals(q, ignoreCase = true) }) porody
        else porody.filter { it.contains(q, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && porody.isNotEmpty(),
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { if (porody.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && porody.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            if (suggestions.isEmpty()) {
                DropdownMenuItem(text = { Text("Нет такой породы в справочнике") }, onClick = {}, enabled = false)
            }
            suggestions.forEach { poroda ->
                DropdownMenuItem(
                    text = { Text(poroda) },
                    onClick = {
                        onValueChange(poroda)
                        expanded = false
                    },
                )
            }
        }
    }
}
