package com.lesovod.mobile.ui.lesokultury

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.PendingLesokulturyPayload
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.PerevodRequest
import com.lesovod.mobile.data.network.dto.TaksatsiyaIn
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Значения поля reshenie — подтверждены схемой сервера, менять строки нельзя. */
enum class PerevodReshenie(val wireValue: String, val displayName: String) {
    PEREVESTI("перевести", "Перевести"),
    DORASHCHIVANIE("доращивание", "Доращивание"),
    SPISAT("списать", "Списать"),
    NE_PEREVODIT("не переводить", "Не переводить"),
    DORABOTAT("доработать", "Доработать"),
}

/** Таксация при переводе (прил. 4) — ввод текстом, как везде в приложении. */
data class TaksatsiyaForm(
    val nomerKartochki: String = "",
    val ploshad: String = "",
    val podvydel: String = "",
    val sostav: String = "",
    val vozrast: String = "",
    val vysota: String = "",
    val diametr: String = "",
    val polnota: String = "",
)

private fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Сокращения пород для формулы состава («8Е2Б»); неизвестная порода — первая буква. */
private val PORODA_ABBR = mapOf(
    "сосна" to "С", "ель" to "Е", "берёза" to "Б", "береза" to "Б", "дуб" to "Д", "осина" to "Ос",
    "ольха черная" to "Ол", "ольха чёрная" to "Ол", "ольха серая" to "Олс", "лиственница" to "Лц",
    "ясень" to "Я", "клён" to "Кл", "клен" to "Кл", "липа" to "Лп", "граб" to "Г",
)

/** Формула состава по прижившимся на пробах: доли в десятках, порода с долей меньше 1 не пишется. */
fun sostavIzRezultatov(rezultaty: List<RezultatEntry>): String {
    val total = rezultaty.sumOf { it.prizhilos }
    if (total <= 0) return ""
    return rezultaty
        .filter { it.prizhilos > 0 }
        .sortedByDescending { it.prizhilos }
        .mapNotNull { r ->
            val share = Math.round(r.prizhilos * 10.0 / total).toInt()
            if (share < 1) return@mapNotNull null
            val name = r.poroda.trim()
            val abbr = PORODA_ABBR[name.lowercase()] ?: name.take(1).uppercase()
            "$share$abbr"
        }
        .joinToString("")
}

data class PerevodUiState(
    val uchastki: List<LesokulturyUchastok> = emptyList(),
    val selectedUchastok: LesokulturyUchastok? = null,
    val god: Int = 1,
    val porody: List<String> = emptyList(),
    val proby: List<ProbaEntry> = listOf(ProbaEntry()),
    val rezultaty: List<RezultatEntry> = emptyList(),
    val reshenie: PerevodReshenie? = null,
    val taksatsiya: TaksatsiyaForm = TaksatsiyaForm(),
    val doGoda: String = "",
    val prichinaSpisaniya: String = "",
    val isLoadingReference: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: JsonElement? = null,
    /** Нет сети — карточка сохранена на телефоне и уйдёт на сервер сама, когда появится связь. */
    val queuedOffline: Boolean = false,
) {
    val preview: LesokulturyPreview get() = computePreview(proby, rezultaty, selectedUchastok?.ploshad)

    /** Состав по результатам проб — подсказка, если в таксации не вписан свой. */
    val sostavPoProbam: String get() = sostavIzRezultatov(rezultaty)

    /** Лет с посадки — к нему прибавляют возраст посадочного материала. */
    val letSPosadki: Int? get() = selectedUchastok?.god?.toIntOrNull()?.let { java.time.Year.now().value - it }
}

class PerevodViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))
    private val queueManager = OfflineQueueManager.getInstance(application)

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

    fun selectUchastok(uchastok: LesokulturyUchastok?) {
        _uiState.value = _uiState.value.copy(selectedUchastok = uchastok, error = null)
    }

    fun selectGod(god: Int) {
        _uiState.value = _uiState.value.copy(god = god)
    }

    fun selectReshenie(reshenie: PerevodReshenie) {
        _uiState.value = _uiState.value.copy(reshenie = reshenie, error = null)
    }

    fun onTaksatsiyaChange(transform: (TaksatsiyaForm) -> TaksatsiyaForm) {
        _uiState.value = _uiState.value.copy(taksatsiya = transform(_uiState.value.taksatsiya), error = null)
    }

    fun onDoGodaChange(value: String) {
        _uiState.value = _uiState.value.copy(doGoda = value, error = null)
    }

    fun onPrichinaChange(value: String) {
        _uiState.value = _uiState.value.copy(prichinaSpisaniya = value, error = null)
    }

    private fun decisionError(state: PerevodUiState): String? = when (state.reshenie) {
        null -> "Выберите решение"
        PerevodReshenie.DORASHCHIVANIE -> state.doGoda.trim().takeIf { it.isNotEmpty() }?.let {
            val god = it.toIntOrNull()
            if (god == null || god !in 2000..2100) "Год доращивания — четыре цифры, например 2028" else null
        }
        PerevodReshenie.SPISAT -> if (state.prichinaSpisaniya.isBlank()) "Укажите причину списания" else null
        PerevodReshenie.PEREVESTI -> taksatsiyaError(state.taksatsiya)
        else -> null
    }

    private fun taksatsiyaError(t: TaksatsiyaForm): String? {
        fun bad(value: String, max: Double) = value.isNotBlank() && (value.num()?.let { it < 0 || it > max } ?: true)
        return when {
            t.ploshad.isNotBlank() && (t.ploshad.num()?.let { it <= 0 } ?: true) -> "Площадь перевода — число больше нуля"
            t.vozrast.isNotBlank() && (t.vozrast.trim().toIntOrNull()?.let { it !in 0..200 } ?: true) -> "Возраст — целое число лет"
            bad(t.vysota, 60.0) -> "Высота — число в метрах"
            bad(t.diametr, 100.0) -> "Диаметр — число в сантиметрах"
            bad(t.polnota, 1.5) -> "Полнота — число от 0 до 1.5"
            else -> null
        }
    }

    private fun PerevodUiState.taksatsiyaIn(): TaksatsiyaIn {
        val t = taksatsiya
        return TaksatsiyaIn(
            nomerKartochki = t.nomerKartochki.trim(),
            ploshad = t.ploshad.num(),
            podvydel = t.podvydel.trim(),
            sostav = t.sostav.trim().ifEmpty { sostavPoProbam },
            vozrast = t.vozrast.trim().toIntOrNull(),
            vysota = t.vysota.num(),
            diametr = t.diametr.num(),
            polnota = t.polnota.num(),
        )
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
        val validationError = validateLesokulturyForm(state.selectedUchastok, state.proby, state.rezultaty)
            ?: decisionError(state)
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
                taksatsiya = if (reshenie == PerevodReshenie.PEREVESTI) state.taksatsiyaIn() else null,
                doGoda = if (reshenie == PerevodReshenie.DORASHCHIVANIE) state.doGoda.trim().toIntOrNull() else null,
                prichinaSpisaniya = if (reshenie == PerevodReshenie.SPISAT) state.prichinaSpisaniya.trim() else "",
            )
            val result = repository.submitPerevod(uchastok.id, request)
            if (result.exceptionOrNull() is ConnectivityException) {
                queueManager.enqueueLesokultury(PendingLesokulturyPayload(uchastok.id, perevod = request))
                _uiState.value = _uiState.value.copy(isSubmitting = false, queuedOffline = true)
                return@launch
            }
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
