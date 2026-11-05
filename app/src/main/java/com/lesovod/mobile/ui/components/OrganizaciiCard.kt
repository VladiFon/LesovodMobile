package com.lesovod.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.izdanie.Izdanie
import com.lesovod.mobile.data.izdanie.Organizacii
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import kotlinx.coroutines.launch

/**
 * «Организации» (Влад, 05.11.2026): своё лесничество и лесопункт в одном телефоне.
 * Переключение одним нажатием — приложение перезапустится уже в другой организации,
 * вход в каждую запоминается.
 */
@Composable
fun OrganizaciiCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val izdanie by Izdanie.state.collectAsState()
    var spisok by remember { mutableStateOf(Organizacii.spisok(context)) }
    var noviy by remember { mutableStateOf("") }
    var soobshchenie by remember { mutableStateOf<Pair<String, ChipTone>?>(null) }
    var zanyat by remember { mutableStateOf(false) }

    // Узнать у сервера, кто он, и подписать текущую организацию.
    LaunchedEffect(Unit) {
        Izdanie.obnovit()?.let {
            Organizacii.nazvatTekushchuyu(context, it.podpis)
            spisok = Organizacii.spisok(context)
        }
    }

    Column(modifier = modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("Организации", style = MaterialTheme.typography.titleSmall)
        Text(
            "Сейчас: ${izdanie.podpis}. Если работаете и в лесничестве, и в лесопункте — " +
                "добавьте второй адрес и переключайтесь здесь.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        spisok.forEachIndexed { i, org ->
            val tekushchaya = i == 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        org.nazvanie ?: Izdanie.izvestno(org.osnovnoy)?.podpis ?: "Организация",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        org.osnovnoy,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tekushchaya) {
                    StatusChip(text = "сейчас", tone = ChipTone.OK)
                } else {
                    TextButton(
                        enabled = !zanyat,
                        onClick = {
                            Organizacii.pereklyuchit(context, org)?.let { soobshchenie = it to ChipTone.WARN }
                        },
                    ) { Text("Перейти") }
                    TextButton(
                        enabled = !zanyat,
                        onClick = {
                            Organizacii.udalit(context, org)
                            spisok = Organizacii.spisok(context)
                        },
                        modifier = Modifier.width(48.dp),
                    ) { Text("✕") }
                }
            }
        }
        OutlinedTextField(
            value = noviy,
            onValueChange = { noviy = it.trim() },
            label = { Text("Адрес ещё одной организации") },
            placeholder = { Text("lp.lesovodapipom.store") },
            singleLine = true,
            enabled = !zanyat,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
        )
        soobshchenie?.let { (text, tone) ->
            StatusChip(text = text, tone = tone, modifier = Modifier.padding(top = Spacing.s))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        ) {
            SecondaryButton(
                text = if (zanyat) "Проверяю…" else "Добавить",
                enabled = !zanyat && noviy.isNotBlank(),
                onClick = {
                    zanyat = true
                    soobshchenie = null
                    scope.launch {
                        val err = Organizacii.dobavit(context, noviy)
                        soobshchenie = if (err == null) {
                            noviy = ""
                            spisok = Organizacii.spisok(context)
                            "Добавлено — нажмите «Перейти»" to ChipTone.OK
                        } else {
                            err to ChipTone.ERROR
                        }
                        zanyat = false
                    }
                },
            )
        }
    }
}
