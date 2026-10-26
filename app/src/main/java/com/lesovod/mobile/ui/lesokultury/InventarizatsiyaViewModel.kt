package com.lesovod.mobile.ui.lesokultury

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.PendingLesokulturyPayload
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.KartaProbaDto
import com.lesovod.mobile.data.network.dto.UchastokPolyaRequest
import com.lesovod.mobile.data.network.dto.InventarizatsiyaRequest
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

data class InventarizatsiyaUiState(
    val uchastki: List<LesokulturyUchastok> = emptyList(),
    val selectedUchastok: LesokulturyUchastok? = null,
    val god: Int = 1,
    val porody: List<String> = emptyList(),
    val proby: List<ProbaEntry> = listOf(ProbaEntry()),
    /** Пробы выбранного участка, поставленные на карте (сервер 26.10.2026+). */
    val kartaProby: List<KartaProbaDto> = emptyList(),
    val rezultaty: List<RezultatEntry> = emptyList(),
    val isLoadingReference: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: JsonElement? = null,
    /** Нет сети — карточка сохранена на телефоне и уйдёт на сервер сама, когда появится связь. */
    val queuedOffline: Boolean = false,
) {
    /** Результаты с «высажено», посчитанным по составу и размеру проб, — их видит человек и их же отправляем. */
    val rezultatyItog: List<RezultatEntry> get() = rezultaty.withAutoVysazheno(selectedUchastok, proby)
    val preview: LesokulturyPreview get() = computePreview(proby, rezultaty, selectedUchastok?.ploshad)
}

class InventarizatsiyaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))
    private val queueManager = OfflineQueueManager.getInstance(application)

    private val _uiState = MutableStateFlow(InventarizatsiyaUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadReference()
    }

    private fun loadReference() {
        _uiState.value = _uiState.value.copy(isLoadingReference = true)
        viewModelScope.launch {
            val uchastkiResult = repository.listLesokulturyUchastki()
            val porodyResult = repository.listPorody()
            _uiState.value = _uiState.value.copy(
                isLoadingReference = false,
                uchastki = uchastkiResult.getOrDefault(emptyList()),
                porody = porodyResult.getOrDefault(emptyList()),
                error = uchastkiResult.exceptionOrNull()?.message ?: porodyResult.exceptionOrNull()?.message,
            )
        }
    }

    /** Участок выбран — породы из его состава (8Е2С → Ель, Сосна) сразу строками, если в карточке ещё ничего не насчитано. */
    fun selectUchastok(uchastok: LesokulturyUchastok?) {
        val state = _uiState.value
        val rezultaty = if (uchastok != null && state.rezultaty.all { it.prizhilos == 0 && !it.vysazhenoVruchnuyu }) rezultatyIzSostava(uchastok, state.porody) else state.rezultaty
        _uiState.value = state.copy(selectedUchastok = uchastok, rezultaty = rezultaty, error = null, kartaProby = emptyList())
        if (uchastok != null) loadKartaProby(uchastok.id)
    }

    /**
     * Пробы участка с карты: тех. приёмка, 1-й и 3-й год — на одних и тех же пробах. Если в
     * карточке ещё пусто — сразу подставляем их (лишние человек уберёт крестиком).
     * Нет связи / старый сервер — просто без подсказки.
     */
    private fun loadKartaProby(uchastokId: Int) {
        viewModelScope.launch {
            val list = repository.listKartaProbyUchastka(uchastokId).getOrNull().orEmpty()
            val state = _uiState.value
            if (state.selectedUchastok?.id != uchastokId) return@launch
            val proby = if (list.isNotEmpty() && state.proby.all { it.isBlank() }) list.map { it.toProbaEntry() } else state.proby
            _uiState.value = state.copy(kartaProby = list, proby = proby)
        }
    }

    fun pickKartaProba(proba: KartaProbaDto) {
        _uiState.value = _uiState.value.copy(proby = _uiState.value.proby.withKartaProba(proba), error = null)
    }

    fun selectGod(god: Int) {
        _uiState.value = _uiState.value.copy(god = god)
    }

    fun addProba() {
        _uiState.value = _uiState.value.copy(proby = _uiState.value.proby + ProbaEntry())
    }

    fun removeProba(id: String) {
        val proby = _uiState.value.proby
        if (proby.size <= 1) return
        _uiState.value = _uiState.value.copy(proby = proby.filterNot { it.id == id })
    }

    fun onProbaNomerChange(id: String, value: String) = updateProba(id) { it.copy(nomer = value) }
    fun onProbaRazmerChange(id: String, value: String) = updateProba(id) { it.copy(razmer = value) }

    private fun updateProba(id: String, transform: (ProbaEntry) -> ProbaEntry) {
        _uiState.value = _uiState.value.copy(
            proby = _uiState.value.proby.map { if (it.id == id) transform(it) else it },
            error = null,
        )
    }

    /** Тап по чипу породы — +1 «прижилось»; если породы ещё нет в результатах, добавляет строку. */
    fun tapPoroda(poroda: String) {
        val rezultaty = _uiState.value.rezultaty
        val updated = if (rezultaty.any { it.poroda == poroda }) {
            rezultaty.map { if (it.poroda == poroda) it.copy(prizhilos = it.prizhilos + 1) else it }
        } else {
            rezultaty + RezultatEntry(poroda = poroda, prizhilos = 1)
        }
        _uiState.value = _uiState.value.copy(rezultaty = updated, error = null)
    }

    /** Долгое нажатие — отменить последний тап по породе (симметрично кубатурнику). */
    fun undoPoroda(poroda: String) {
        val entry = _uiState.value.rezultaty.firstOrNull { it.poroda == poroda } ?: return
        if (entry.prizhilos <= 0) return
        _uiState.value = _uiState.value.copy(
            rezultaty = _uiState.value.rezultaty.map { if (it.poroda == poroda) it.copy(prizhilos = it.prizhilos - 1) else it },
        )
    }

    /** «Высажено» поправили руками — дальше авторасчёт эту породу не трогает. */
    fun onVysazhenoChange(poroda: String, value: String) {
        _uiState.value = _uiState.value.copy(
            rezultaty = _uiState.value.rezultaty.map {
                if (it.poroda == poroda) it.copy(vysazheno = value.filter(Char::isDigit), vysazhenoVruchnuyu = true) else it
            },
            error = null,
        )
    }

    /** Вернуть «высажено» к расчёту по составу и размеру проб. */
    fun onVysazhenoAuto(poroda: String) {
        _uiState.value = _uiState.value.copy(
            rezultaty = _uiState.value.rezultaty.map { if (it.poroda == poroda) it.copy(vysazheno = "", vysazhenoVruchnuyu = false) else it },
        )
    }

    /** «Прижилось» набрали числом. */
    fun onPrizhilosChange(poroda: String, value: String) {
        val count = value.filter(Char::isDigit).take(6).toIntOrNull() ?: 0
        _uiState.value = _uiState.value.copy(
            rezultaty = _uiState.value.rezultaty.map { if (it.poroda == poroda) it.copy(prizhilos = count) else it },
            error = null,
        )
    }

    /** Густоту/схему дописали в «Данных участка» — расчёт «высажено» берёт их сразу. */
    fun onPolyaSaved(request: UchastokPolyaRequest) {
        val state = _uiState.value
        val uchastok = state.selectedUchastok ?: return
        val updated = uchastok.withPolya(request)
        _uiState.value = state.copy(selectedUchastok = updated, uchastki = state.uchastki.map { if (it.id == updated.id) updated else it })
    }

    /** Добавить породу в карточку вручную — в списке ввода только те породы, что есть на участке. */
    fun addPoroda(poroda: String) {
        val name = poroda.trim()
        if (name.isEmpty()) return
        val rezultaty = _uiState.value.rezultaty
        if (rezultaty.any { it.poroda.equals(name, ignoreCase = true) }) return
        _uiState.value = _uiState.value.copy(rezultaty = rezultaty + RezultatEntry(poroda = name), error = null)
    }

    fun removeRezultat(poroda: String) {
        _uiState.value = _uiState.value.copy(rezultaty = _uiState.value.rezultaty.filterNot { it.poroda == poroda })
    }

    fun submit() {
        val state = _uiState.value
        val validationError = validateLesokulturyForm(state.selectedUchastok, state.proby, state.rezultatyItog)
        if (validationError != null) {
            _uiState.value = state.copy(error = validationError)
            return
        }
        val uchastok = state.selectedUchastok!!

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val request = InventarizatsiyaRequest(
                proby = state.proby.toProbaRowsIn(),
                rezultaty = state.rezultatyItog.toRezultatyIn(),
                god = state.god,
            )
            val result = repository.submitInventarizatsiya(uchastok.id, request)
            if (result.exceptionOrNull() is ConnectivityException) {
                queueManager.enqueueLesokultury(PendingLesokulturyPayload(uchastok.id, inventarizatsiya = request))
                _uiState.value = _uiState.value.copy(isSubmitting = false, queuedOffline = true)
                return@launch
            }
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isSubmitting = false, result = it) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось сохранить инвентаризацию") },
            )
        }
    }

    fun newCard() {
        _uiState.value = InventarizatsiyaUiState(
            uchastki = _uiState.value.uchastki,
            porody = _uiState.value.porody,
            isLoadingReference = false,
        )
    }
}
