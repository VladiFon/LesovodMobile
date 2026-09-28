package com.lesovod.mobile.ui.lesokultury

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.local.PendingLesokulturyPayload
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.UchastokPolyaRequest
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.ui.proba.UchastokPolya
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Значения — как в графах прил. 7 ведомости текущих изменений (приказ №130). */
private val SPOSOB_OBRABOTKI = listOf("сплошная", "полосами", "бороздами", "площадками", "без обработки")
private val METOD_SOZDANIYA = listOf(
    "посадка механизированная", "посадка ручная", "посев механизированный", "посев ручной",
    "аэросев", "созданы ЗКС", "селекционным материалом",
)

sealed interface PolyaSaveState {
    data object Idle : PolyaSaveState
    data object Saving : PolyaSaveState
    data object Saved : PolyaSaveState
    data object QueuedOffline : PolyaSaveState
    data class Error(val message: String) : PolyaSaveState
}

class UchastokPolyaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))
    private val queueManager = OfflineQueueManager.getInstance(application)

    private val _state = MutableStateFlow<PolyaSaveState>(PolyaSaveState.Idle)
    val state = _state.asStateFlow()

    fun save(uchastokId: Int, request: UchastokPolyaRequest) {
        _state.value = PolyaSaveState.Saving
        viewModelScope.launch {
            val result = repository.updateUchastokPolya(uchastokId, request)
            if (result.exceptionOrNull() is ConnectivityException) {
                queueManager.enqueueLesokultury(PendingLesokulturyPayload(uchastokId, polya = request))
                _state.value = PolyaSaveState.QueuedOffline
                return@launch
            }
            _state.value = result.fold(
                onSuccess = { PolyaSaveState.Saved },
                onFailure = { PolyaSaveState.Error(it.message ?: "Не удалось сохранить") },
            )
        }
    }

    fun edited() {
        if (_state.value != PolyaSaveState.Saving) _state.value = PolyaSaveState.Idle
    }
}

private fun Double?.asInput(): String = when {
    this == null -> ""
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}

private fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Поля участка для ведомостей текущих изменений: видны после выбора участка, их можно дописать
 * в лесу (подвыдел, обработка почвы, схема посадки, материал). Без сети правка ждёт на телефоне.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UchastokPolyaCard(uchastok: LesokulturyUchastok, enabled: Boolean = true) {
    val viewModel: UchastokPolyaViewModel = viewModel(key = "polya-${uchastok.id}")
    val saveState by viewModel.state.collectAsState()
    val polya: UchastokPolya = uchastok.polya
    var expanded by rememberSaveable(uchastok.id) { mutableStateOf(false) }

    var podvydel by remember(uchastok.id) { mutableStateOf(polya.podvydel.orEmpty()) }
    var sposob by remember(uchastok.id) { mutableStateOf(polya.sposobObrabotki.orEmpty()) }
    var metod by remember(uchastok.id) { mutableStateOf(polya.metodSozdaniya.orEmpty()) }
    var mezhdu by remember(uchastok.id) { mutableStateOf(polya.shemaMezhduRyadami.asInput()) }
    var vRyadu by remember(uchastok.id) { mutableStateOf(polya.shemaVRyadu.asInput()) }
    var gustota by remember(uchastok.id) { mutableStateOf(polya.gustotaPosadki.asInput()) }
    var material by remember(uchastok.id) { mutableStateOf(polya.posadochnyyMaterial.orEmpty()) }

    val a = mezhdu.toNumberOrNull()
    val b = vRyadu.toNumberOrNull()
    val gustotaPoShema = if (a != null && b != null && a > 0 && b > 0) Math.round(10_000 / (a * b)) else null
    val saving = saveState == PolyaSaveState.Saving
    val fieldsEnabled = enabled && !saving
    val onEdit = { viewModel.edited() }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.m),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Данные участка", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                val summary = listOfNotNull(
                    polya.vydelStaryy?.let { "старый выдел $it" },
                    polya.sposobObrabotki,
                    polya.sostavFormula,
                    if (polya.shemaMezhduRyadami != null && polya.shemaVRyadu != null) {
                        "схема ${polya.shemaMezhduRyadami.asInput()}×${polya.shemaVRyadu.asInput()}"
                    } else null,
                ).joinToString(" · ")
                Text(
                    summary.ifBlank { "Подвыдел, обработка почвы, схема посадки — нажмите, чтобы заполнить" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
            )
        }
        if (!expanded) return@Column

        PolyaTextField("Подвыдел", podvydel, fieldsEnabled, placeholder = "например, 15.1") { podvydel = it; onEdit() }

        Text("Способ обработки почвы", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SPOSOB_OBRABOTKI.forEach { option ->
                FilterChip(
                    selected = sposob == option,
                    onClick = { sposob = if (sposob == option) "" else option; onEdit() },
                    label = { Text(option) },
                    enabled = fieldsEnabled,
                )
            }
        }

        Text("Метод создания", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (metod.isNotBlank() && metod !in METOD_SOZDANIYA) listOf(metod) + METOD_SOZDANIYA else METOD_SOZDANIYA).forEach { option ->
                FilterChip(
                    selected = metod == option,
                    onClick = { metod = if (metod == option) "" else option; onEdit() },
                    label = { Text(option) },
                    enabled = fieldsEnabled,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PolyaTextField("Между рядами, м", mezhdu, fieldsEnabled, numeric = true, modifier = Modifier.weight(1f)) { mezhdu = it; onEdit() }
            PolyaTextField("В ряду, м", vRyadu, fieldsEnabled, numeric = true, modifier = Modifier.weight(1f)) { vRyadu = it; onEdit() }
        }
        PolyaTextField(
            "Густота посадки, шт/га",
            gustota,
            fieldsEnabled,
            numeric = true,
            placeholder = gustotaPoShema?.let { "по схеме $it" },
        ) { gustota = it; onEdit() }
        PolyaTextField("Посадочный материал", material, fieldsEnabled, placeholder = "например, Е сн ЗКС") { material = it; onEdit() }

        when (val s = saveState) {
            PolyaSaveState.Saved -> StatusChip(text = "Сохранено", tone = ChipTone.OK)
            PolyaSaveState.QueuedOffline -> StatusChip(text = "Нет сети — уйдёт на сервер, когда появится связь", tone = ChipTone.WARN)
            is PolyaSaveState.Error -> StatusChip(text = s.message, tone = ChipTone.ERROR)
            else -> Unit
        }

        SecondaryButton(
            text = if (saving) "Сохраняю…" else "Сохранить данные участка",
            enabled = fieldsEnabled,
            onClick = {
                viewModel.save(
                    uchastok.id,
                    UchastokPolyaRequest(
                        podvydel = podvydel.trim().takeIf { it.isNotEmpty() },
                        metodSozdaniya = metod.takeIf { it.isNotEmpty() },
                        sposobObrabotki = sposob.takeIf { it.isNotEmpty() },
                        shemaMezhduRyadami = mezhdu.toNumberOrNull()?.takeIf { it > 0 },
                        shemaVRyadu = vRyadu.toNumberOrNull()?.takeIf { it > 0 },
                        gustotaPosadki = (gustota.toNumberOrNull() ?: gustotaPoShema?.toDouble())?.takeIf { it > 0 },
                        posadochnyyMaterial = material.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            },
        )
    }
}

@Composable
private fun PolyaTextField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    numeric: Boolean = false,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
        modifier = modifier,
    )
}
