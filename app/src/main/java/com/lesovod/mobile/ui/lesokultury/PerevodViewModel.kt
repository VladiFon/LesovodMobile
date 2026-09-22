package com.lesovod.mobile.ui.lesokultury

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.PerevodRequest
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Три значения поля reshenie — подтверждены схемой сервера, менять строки нельзя. */
enum class PerevodReshenie(val wireValue: String, val displayName: String) {
    PEREVESTI("перевести", "Перевести"),
    NE_PEREVODIT("не переводить", "Не переводить"),
    DORABOTAT("доработать", "Доработать"),
}

data class PerevodUiState(
    val uchastki: List<LesokulturyUchastok> = emptyList(),
    val selectedUchastok: LesokulturyUchastok? = null,
    val god: Int = 1,
    val porody: List<String> = emptyList(),
    val proby: List<ProbaEntry> = listOf(ProbaEntry()),
    val rezultaty: List<RezultatEntry> = emptyList(),
    val reshenie: PerevodReshenie? = null,
    val isLoadingReference: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: JsonElement? = null,
) {
    val preview: LesokulturyPreview get() = computePreview(proby, rezultaty, selectedUchastok?.ploshad)
}

class PerevodViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))

    private val _uiState = MutableStateFlow(PerevodUiState())
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

    fun selectUchastok(uchastok: LesokulturyUchastok) {
        _uiState.value = _uiState.value.copy(selectedUchastok = uchastok, error = null)
    }

    fun selectGod(god: Int) {
        _uiState.value = _uiState.value.copy(god = god)
    }

    fun selectReshenie(reshenie: PerevodReshenie) {
        _uiState.value = _uiState.value.copy(reshenie = reshenie, error = null)
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

    fun tapPoroda(poroda: String) {
        val rezultaty = _uiState.value.rezultaty
        val updated = if (rezultaty.any { it.poroda == poroda }) {
            rezultaty.map { if (it.poroda == poroda) it.copy(prizhilos = it.prizhilos + 1) else it }
        } else {
            rezultaty + RezultatEntry(poroda = poroda, prizhilos = 1)
        }
        _uiState.value = _uiState.value.copy(rezultaty = updated, error = null)
    }

    fun undoPoroda(poroda: String) {
        val entry = _uiState.value.rezultaty.firstOrNull { it.poroda == poroda } ?: return
        if (entry.prizhilos <= 0) return
        _uiState.value = _uiState.value.copy(
            rezultaty = _uiState.value.rezultaty.map { if (it.poroda == poroda) it.copy(prizhilos = it.prizhilos - 1) else it },
        )
    }

    fun onVysazhenoChange(poroda: String, value: String) {
        _uiState.value = _uiState.value.copy(
            rezultaty = _uiState.value.rezultaty.map { if (it.poroda == poroda) it.copy(vysazheno = value) else it },
            error = null,
        )
    }

    fun removeRezultat(poroda: String) {
        _uiState.value = _uiState.value.copy(rezultaty = _uiState.value.rezultaty.filterNot { it.poroda == poroda })
    }

    fun submit() {
        val state = _uiState.value
        val validationError = validateLesokulturyForm(state.selectedUchastok, state.proby, state.rezultaty)
            ?: if (state.reshenie == null) "Выберите решение" else null
        if (validationError != null) {
            _uiState.value = state.copy(error = validationError)
            return
        }
        val uchastok = state.selectedUchastok!!
        val reshenie = state.reshenie!!

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val request = PerevodRequest(
                proby = state.proby.toProbaRowsIn(),
                rezultaty = state.rezultaty.toRezultatyIn(),
                god = state.god,
                reshenie = reshenie.wireValue,
            )
            val result = repository.submitPerevod(uchastok.id, request)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isSubmitting = false, result = it) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить перевод") },
            )
        }
    }

    fun newCard() {
        _uiState.value = PerevodUiState(
            uchastki = _uiState.value.uchastki,
            porody = _uiState.value.porody,
            isLoadingReference = false,
        )
    }
}
