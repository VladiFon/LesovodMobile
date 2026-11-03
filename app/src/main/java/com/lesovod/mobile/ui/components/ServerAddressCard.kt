package com.lesovod.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.lesovod.mobile.data.network.ServerConfig
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import kotlinx.coroutines.launch

/**
 * «Адрес сервера» — основной и запасной (шаг 5 разбора 03.11.2026). Есть в Профиле и на
 * экране входа: если домен недоступен, войти без запасного адреса было бы нельзя.
 */
@Composable
fun ServerAddressCard(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val active by ServerConfig.active.collectAsState()
    var osnovnoy by remember { mutableStateOf(ServerConfig.osnovnoy) }
    var zapasnoy by remember { mutableStateOf(ServerConfig.zapasnoy.orEmpty()) }
    var soobshchenie by remember { mutableStateOf<Pair<String, ChipTone>?>(null) }
    var zanyat by remember { mutableStateOf(false) }

    fun proverit() {
        zanyat = true
        soobshchenie = null
        scope.launch {
            val o = ServerConfig.proverit(osnovnoy)
            val z = if (zapasnoy.isBlank()) null else ServerConfig.proverit(zapasnoy)
            val chasti = mutableListOf("Основной: " + (o ?: "отвечает ✓"))
            if (zapasnoy.isNotBlank()) chasti += "Запасной: " + (z ?: "отвечает ✓")
            val tone = when {
                o == null && (zapasnoy.isBlank() || z == null) -> ChipTone.OK
                o == null || (zapasnoy.isNotBlank() && z == null) -> ChipTone.WARN
                else -> ChipTone.ERROR
            }
            soobshchenie = chasti.joinToString("\n") to tone
            zanyat = false
        }
    }

    Column(modifier = modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("Адрес сервера", style = MaterialTheme.typography.titleSmall)
        Text(
            "Если основной не отвечает, приложение само переключится на запасной " +
                "(например, адрес сервера в конторе: lesovod.local или 192.168.1.10).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        OutlinedTextField(
            value = osnovnoy,
            onValueChange = { osnovnoy = it.trim() },
            label = { Text("Основной адрес") },
            singleLine = true,
            enabled = !zanyat,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
        )
        OutlinedTextField(
            value = zapasnoy,
            onValueChange = { zapasnoy = it.trim() },
            label = { Text("Запасной адрес (необязательно)") },
            singleLine = true,
            enabled = !zanyat,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        )
        Text(
            "Сейчас работает через: $active",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s),
        )
        soobshchenie?.let { (text, tone) ->
            StatusChip(text = text, tone = tone, modifier = Modifier.padding(top = Spacing.s))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
        ) {
            SecondaryButton(text = "Проверить", onClick = { proverit() }, enabled = !zanyat, modifier = Modifier.weight(1f))
            SecondaryButton(
                text = "Сохранить",
                enabled = !zanyat,
                modifier = Modifier.weight(1f),
                onClick = {
                    val err = ServerConfig.save(osnovnoy, zapasnoy)
                    if (err != null) {
                        soobshchenie = err to ChipTone.ERROR
                    } else {
                        osnovnoy = ServerConfig.osnovnoy
                        zapasnoy = ServerConfig.zapasnoy.orEmpty()
                        soobshchenie = "Сохранено" to ChipTone.OK
                    }
                },
            )
        }
    }
}
