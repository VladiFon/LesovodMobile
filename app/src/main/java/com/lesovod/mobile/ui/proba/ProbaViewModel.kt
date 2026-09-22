package com.lesovod.mobile.ui.proba

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.ProbaFormRequest
import com.lesovod.mobile.data.network.dto.ProbaResponse
import com.lesovod.mobile.data.network.dto.ProbaRowRequest
import com.lesovod.mobile.data.network.dto.ProbaSaveRequest
import com.lesovod.mobile.data.repository.BotRepository
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
    val selectedLesokulturyIds: Set<Int> = emptySet(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: ProbaResponse? = null,
) {
    companion object {
        fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}

class ProbaViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

    private val _uiState = MutableStateFlow(ProbaUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadLesokulturyUchastki()
    }

    private fun loadLesokulturyUchastki() {
        viewModelScope.launch {
            repository.listLesokulturyUchastki().onSuccess {
                _uiState.value = _uiState.value.copy(lesokulturyUchastki = it)
            }
        }
    }

    fun toggleLesokulturyUchastok(id: Int) {
        val selected = _uiState.value.selectedLesokulturyIds
        _uiState.value = _uiState.value.copy(
            selectedLesokulturyIds = if (id in selected) selected - id else selected + id,
        )
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

        if (state.kvartal.isBlank() || state.vydel.isBlank()) {
            _uiState.value = state.copy(error = "Укажите квартал и выдел")
            return
        }
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

            val fotoStolbDelyankiResult = repository.uploadPhoto(context, fotoStolbDelyankiUri)
            fotoStolbDelyankiResult.onFailure {
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось загрузить фото столба границы делянки")
                return@launch
            }
            val fotoStolbProbyResult = repository.uploadPhoto(context, fotoStolbProbyUri)
            fotoStolbProbyResult.onFailure {
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось загрузить фото столба пробной площадки")
                return@launch
            }

            val result = repository.submitProba(
                ProbaSaveRequest(
                    kvartal = state.kvartal.trim(),
                    vydel = state.vydel.trim(),
                    ploshadVydela = ploshadVydela,
                    dataZamera = state.dataZamera.trim(),
                    rows = rows,
                    form = ProbaFormRequest(kolPloshadok, ploshadPloshadki),
                    lesokulturyUchastokIds = state.selectedLesokulturyIds.toList(),
                    fotoStolbDelyanki = fotoStolbDelyankiResult.getOrNull(),
                    fotoStolbProby = fotoStolbProbyResult.getOrNull(),
                ),
            )
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isSubmitting = false, result = it) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить пробу") },
            )
        }
    }

    fun newProba() {
        _uiState.value = ProbaUiState(lesokulturyUchastki = _uiState.value.lesokulturyUchastki)
    }
}
