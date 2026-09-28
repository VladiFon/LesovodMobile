package com.lesovod.mobile.ui.proba

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.ProbaFormRequest
import com.lesovod.mobile.data.network.dto.ProbaResponse
import com.lesovod.mobile.data.network.dto.ProbaRowRequest
import com.lesovod.mobile.data.network.dto.ProbaSaveRequest
import com.lesovod.mobile.data.local.ProbaHistoryStore
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProbaRowInput(
    val id: String = UUID.randomUUID().toString(),
    val poroda: String = "",
    val shirina: String = "",
    val vysota: String = "",
    val dlina: String = "",
)

enum class ProbaTab(val label: String) { NEW("Новая проба"), HISTORY("Мои пробы") }

data class ProbaUiState(
    val kvartal: String = "",
    val vydel: String = "",
    val ploshadVydela: String = "",
    val dataZamera: String = todayIso(),
    val rows: List<ProbaRowInput> = listOf(ProbaRowInput()),
    val kolPloshadok: String = "",
    val ploshadPloshadki: String = "",
    /** Фото столба границы делянки — обязательно перед отправкой. */
    val fotoStolbDelyankiUri: Uri? = null,
    /** Фото столба пробной площадки — обязательно перед отправкой. */
    val fotoStolbProbyUri: Uri? = null,
    val lesokulturyUchastki: List<LesokulturyUchastok> = emptyList(),
    /** Участок л/к, где взята проба — квартал/выдел/площадь подставляются из него. */
    val selectedUchastok: LesokulturyUchastok? = null,
    /** Квартал/выдел вручную — для проб не на участке лесных культур. */
    val manualPlace: Boolean = false,
    /** Справочник пород с сервера (как выпадающий список на вебе). */
    val porody: List<String> = emptyList(),
    val tab: ProbaTab = ProbaTab.NEW,
    val history: List<ProbaResponse> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null,
    val openedHistory: ProbaResponse? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: ProbaResponse? = null,
    val queuedOffline: Boolean = false,
) {
    companion object {
        fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}

class ProbaViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val queueManager = OfflineQueueManager.getInstance(application)
    private val historyStore = ProbaHistoryStore(application)

    private val _uiState = MutableStateFlow(ProbaUiState(history = historyStore.list()))
    val uiState = _uiState.asStateFlow()

    init {
        loadReference()
    }

    private fun loadReference() {
        viewModelScope.launch {
            repository.listLesokulturyUchastki().onSuccess {
                _uiState.value = _uiState.value.copy(lesokulturyUchastki = it)
            }
        }
        viewModelScope.launch {
            repository.listPorody().onSuccess {
                _uiState.value = _uiState.value.copy(porody = it)
            }
        }
    }

    fun selectTab(tab: ProbaTab) {
        _uiState.value = _uiState.value.copy(tab = tab, openedHistory = null)
        if (tab == ProbaTab.HISTORY) loadHistory()
    }

    /** Сначала показываем сохранённое на телефоне, потом обновляем с сервера (если есть сеть). */
    fun loadHistory() {
        _uiState.value = _uiState.value.copy(isLoadingHistory = true, historyError = null, history = historyStore.list())
        viewModelScope.launch {
            val result = repository.listMyProby()
            result.onSuccess { historyStore.replaceAll(it) }
            _uiState.value = _uiState.value.copy(
                isLoadingHistory = false,
                history = historyStore.list(),
                historyError = result.exceptionOrNull()?.let {
                    if (it is ConnectivityException) "Нет сети — показаны пробы, сохранённые на телефоне" else it.message
                },
            )
        }
    }

    fun openHistory(proba: ProbaResponse?) {
        _uiState.value = _uiState.value.copy(openedHistory = proba)
    }

    fun selectUchastok(uchastok: LesokulturyUchastok?) {
        _uiState.value = _uiState.value.copy(
            selectedUchastok = uchastok,
            ploshadVydela = uchastok?.ploshad?.let { formatPloshad(it) } ?: _uiState.value.ploshadVydela,
            error = null,
        )
    }

    fun setManualPlace(manual: Boolean) {
        _uiState.value = _uiState.value.copy(manualPlace = manual, error = null)
    }

    fun onFotoStolbDelyankiChange(uri: Uri?) {
        _uiState.value = _uiState.value.copy(fotoStolbDelyankiUri = uri, error = null)
    }

    fun onFotoStolbProbyChange(uri: Uri?) {
        _uiState.value = _uiState.value.copy(fotoStolbProbyUri = uri, error = null)
    }

    fun onKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(kvartal = value, error = null)
    }

    fun onVydelChange(value: String) {
        _uiState.value = _uiState.value.copy(vydel = value, error = null)
    }

    fun onPloshadVydelaChange(value: String) {
        _uiState.value = _uiState.value.copy(ploshadVydela = value)
    }

    fun onDataZameraChange(value: String) {
        _uiState.value = _uiState.value.copy(dataZamera = value)
    }

    fun onKolPloshadokChange(value: String) {
        _uiState.value = _uiState.value.copy(kolPloshadok = value, error = null)
    }

    fun onPloshadPloshadkiChange(value: String) {
        _uiState.value = _uiState.value.copy(ploshadPloshadki = value, error = null)
    }

    fun addRow() {
        _uiState.value = _uiState.value.copy(rows = _uiState.value.rows + ProbaRowInput())
    }

    fun removeRow(id: String) {
        val rows = _uiState.value.rows
        if (rows.size <= 1) return
        _uiState.value = _uiState.value.copy(rows = rows.filterNot { it.id == id })
    }

    fun onRowPorodaChange(id: String, value: String) = updateRow(id) { it.copy(poroda = value) }
    fun onRowShirinaChange(id: String, value: String) = updateRow(id) { it.copy(shirina = value) }
    fun onRowVysotaChange(id: String, value: String) = updateRow(id) { it.copy(vysota = value) }
    fun onRowDlinaChange(id: String, value: String) = updateRow(id) { it.copy(dlina = value) }

    private fun updateRow(id: String, transform: (ProbaRowInput) -> ProbaRowInput) {
        _uiState.value = _uiState.value.copy(
            rows = _uiState.value.rows.map { if (it.id == id) transform(it) else it },
            error = null,
        )
    }

    fun submit() {
        val state = _uiState.value

        val uchastok = state.selectedUchastok.takeIf { !state.manualPlace }
        val kvartal = (if (state.manualPlace) state.kvartal else uchastok?.kvartal).orEmpty().trim()
        val vydel = (if (state.manualPlace) state.vydel else uchastok?.vydel).orEmpty().trim()
        if (!state.manualPlace && uchastok == null) {
            _uiState.value = state.copy(error = "Выберите участок лесных культур (или введите квартал и выдел вручную)")
            return
        }
        if (kvartal.isBlank() || vydel.isBlank()) {
            _uiState.value = state.copy(
                error = if (state.manualPlace) "Укажите квартал и выдел" else "У выбранного участка не указан квартал/выдел — введите их вручную",
            )
            return
        }
        val lesokulturyIds = listOfNotNull(uchastok?.id)
        if (state.dataZamera.isBlank()) {
            _uiState.value = state.copy(error = "Укажите дату замера")
            return
        }
        val kolPloshadok = state.kolPloshadok.trim().toIntOrNull()
        if (kolPloshadok == null || kolPloshadok <= 0) {
            _uiState.value = state.copy(error = "Укажите количество пробных площадок числом")
            return
        }
        val ploshadPloshadki = state.ploshadPloshadki.trim().replace(',', '.').toDoubleOrNull()
        if (ploshadPloshadki == null || ploshadPloshadki <= 0) {
            _uiState.value = state.copy(error = "Укажите площадь одной площадки (га) числом")
            return
        }
        val ploshadVydela = state.ploshadVydela.trim().replace(',', '.').toDoubleOrNull()
        if (state.ploshadVydela.isNotBlank() && ploshadVydela == null) {
            _uiState.value = state.copy(error = "Площадь выдела указана неверно")
            return
        }
        val fotoStolbDelyankiUri = state.fotoStolbDelyankiUri
        val fotoStolbProbyUri = state.fotoStolbProbyUri
        if (fotoStolbDelyankiUri == null || fotoStolbProbyUri == null) {
            _uiState.value = state.copy(error = "Приложите оба фото: столба границы делянки и столба пробной площадки")
            return
        }

        val rows = mutableListOf<ProbaRowRequest>()
        for (row in state.rows) {
            if (row.poroda.isBlank()) {
                _uiState.value = state.copy(error = "Укажите породу в каждой укладке")
                return
            }
            val shirina = row.shirina.trim().replace(',', '.').toDoubleOrNull()
            val vysota = row.vysota.trim().replace(',', '.').toDoubleOrNull()
            val dlina = row.dlina.trim().replace(',', '.').toDoubleOrNull()
            if (shirina == null || vysota == null || dlina == null) {
                _uiState.value = state.copy(error = "Заполните ширину, высоту и длину числом в каждой укладке")
                return
            }
            rows += ProbaRowRequest(row.poroda.trim(), shirina, vysota, dlina)
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val context = getApplication<Application>()

            suspend fun enqueueOffline(uploadedDelyanki: String?, uploadedProby: String?) {
                queueManager.enqueueProba(
                    kvartal = kvartal,
                    vydel = vydel,
                    ploshadVydela = ploshadVydela,
                    dataZamera = state.dataZamera.trim(),
                    rows = rows,
                    kolPloshadok = kolPloshadok,
                    ploshadPloshadki = ploshadPloshadki,
                    lesokulturyUchastokIds = lesokulturyIds,
                    fotoStolbDelyankiUri = if (uploadedDelyanki == null) fotoStolbDelyankiUri else null,
                    fotoStolbProbyUri = if (uploadedProby == null) fotoStolbProbyUri else null,
                    uploadedFotoStolbDelyanki = uploadedDelyanki,
                    uploadedFotoStolbProby = uploadedProby,
                )
                _uiState.value = freshState(state).copy(queuedOffline = true)
            }

            val fotoStolbDelyankiResult = repository.uploadPhoto(context, fotoStolbDelyankiUri)
            val delyankiError = fotoStolbDelyankiResult.exceptionOrNull()
            if (delyankiError is ConnectivityException) {
                enqueueOffline(null, null)
                return@launch
            }
            fotoStolbDelyankiResult.onFailure {
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось загрузить фото столба границы делянки")
                return@launch
            }

            val fotoStolbProbyResult = repository.uploadPhoto(context, fotoStolbProbyUri)
            val probyError = fotoStolbProbyResult.exceptionOrNull()
            if (probyError is ConnectivityException) {
                enqueueOffline(fotoStolbDelyankiResult.getOrNull(), null)
                return@launch
            }
            fotoStolbProbyResult.onFailure {
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось загрузить фото столба пробной площадки")
                return@launch
            }

            val result = repository.submitProba(
                ProbaSaveRequest(
                    kvartal = kvartal,
                    vydel = vydel,
                    ploshadVydela = ploshadVydela,
                    dataZamera = state.dataZamera.trim(),
                    rows = rows,
                    form = ProbaFormRequest(kolPloshadok, ploshadPloshadki, ploshadLesoseki = ploshadVydela),
                    lesokulturyUchastokIds = lesokulturyIds,
                    fotoStolbDelyanki = fotoStolbDelyankiResult.getOrNull(),
                    fotoStolbProby = fotoStolbProbyResult.getOrNull(),
                ),
            )
            val submitError = result.exceptionOrNull()
            if (submitError is ConnectivityException) {
                enqueueOffline(fotoStolbDelyankiResult.getOrNull(), fotoStolbProbyResult.getOrNull())
                return@launch
            }
            result.onSuccess { historyStore.add(it) }
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isSubmitting = false, result = it, history = historyStore.list()) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить пробу") },
            )
        }
    }

    fun newProba() {
        _uiState.value = freshState(_uiState.value)
    }

    /** Новая пустая форма, но справочники, история и выбранный режим места сохраняются. */
    private fun freshState(from: ProbaUiState) = ProbaUiState(
        lesokulturyUchastki = from.lesokulturyUchastki,
        porody = from.porody,
        manualPlace = from.manualPlace,
        history = historyStore.list(),
    )

    private fun formatPloshad(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}
