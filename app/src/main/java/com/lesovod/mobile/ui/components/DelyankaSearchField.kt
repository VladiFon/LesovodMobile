package com.lesovod.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.network.dto.DelyankaLocationMatchDto

/**
 * Поиск делянки по кварталу/выделу с привязкой к найденной delyanka_item — вместо того,
 * чтобы рабочий вручную подбирал числовой id делянки (см. TrelevkaScreen, WorkReportScreen).
 * Привязка необязательна: если ничего не выбрано, поля кварталa/выдела формы остаются
 * произвольным текстом, как и раньше.
 */
@Composable
fun DelyankaSearchField(
    kvartal: String,
    vydel: String,
    onKvartalChange: (String) -> Unit,
    onVydelChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    matches: List<DelyankaLocationMatchDto>,
    selected: DelyankaLocationMatchDto?,
    onSelect: (DelyankaLocationMatchDto) -> Unit,
    onClearSelection: () -> Unit,
    searchError: String?,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (selected != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Привязано к делянке №${selected.delyankaId}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            selected.nazvanie?.takeIf { it.isNotBlank() } ?: "Кв. $kvartal, выд. $vydel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClearSelection, enabled = enabled) {
                        Icon(Icons.Filled.Close, contentDescription = "Отвязать делянку")
                    }
                }
            }
            return
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = kvartal,
                onValueChange = onKvartalChange,
                label = { Text("Квартал") },
                singleLine = true,
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = vydel,
                onValueChange = onVydelChange,
                label = { Text("Выдел") },
                singleLine = true,
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }

        OutlinedButton(
            onClick = onSearch,
            enabled = enabled && !isSearching && kvartal.isNotBlank() && vydel.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text("Найти делянку")
        }

        if (searchError != null) {
            Text(
                searchError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (matches.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                matches.forEach { match ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                            ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(
                                "Делянка №${match.delyankaId}" + (match.nazvanie?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (match.statusRabot != null) {
                                Text(
                                    "Статус: ${match.statusRabot}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = { onSelect(match) },
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                Text("Выбрать")
                            }
                        }
                    }
                }
            }
        }
    }
}
