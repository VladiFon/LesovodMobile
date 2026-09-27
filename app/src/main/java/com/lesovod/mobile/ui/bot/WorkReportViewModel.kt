package com.lesovod.mobile.ui.bot

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.location.getCurrentLocationOrNull
import com.lesovod.mobile.data.location.hasLocationPermission
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Как заполнена локация отчёта: привязка к реальной делянке или ручной ввод квартала/выдела. */
enum class WorkReportLocationMode { DELYANKA, MANUAL }

data class WorkReportUiState(
    val tipRaboty: String = "",
    // Ручной ввод (запасной путь, если работа велась не на заведённой делянке).
    val kvartal: String = "",
    val vydelInput: String = "",
    val vydels: List<String> = emptyList(),
    val opisanie: String = "",
    val photoUri: Uri? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val queuedOffline: Boolean = false,
    // Привязка к реальной делянке — основной путь.
    val locationMode: WorkReportLocationMode = WorkReportLocationMode.DELYANKA,
    val selectedDelyanka: DelyankaMapRefDto? = null,
    val delyanki: List<DelyankaMapRefDto> = emptyList(),
    val isLoadingDelyanki: Boolean = false,
    val delyankiError: String? = null,
    val delyankaSearchQuery: String = "",
    val showDelyankaPicker: Boolean = false,
)

class WorkReportViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val queueManager = OfflineQueueManager.getInstance(application)

    val session = sessionManager.session

    private val _uiState = MutableStateFlow(WorkReportUiState())
    val uiState = _uiState.asStateFlow()

    fun onTipRabotyChange(value: String) {
        _uiState.value = _uiState.value.copy(tipRaboty = value, error = null)
    }

    fun onKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(kvartal = value, error = null)
    }

    fun onVydelInputChange(value: String) {
        _uiState.value = _uiState.value.copy(vydelInput = value)
    }

    fun addVydel() {
        val value = _uiState.value.vydelInput.trim()
        if (value.isEmpty() || _uiState.value.vydels.contains(value)) return
        _uiState.value = _uiState.value.copy(
            vydels = _uiState.value.vydels + value,
            vydelInput = "",
        )
    }

    fun removeVydel(value: String) {
        _uiState.value = _uiState.value.copy(vydels = _uiState.value.vydels - value)
    }

    fun onOpisanieChange(value: String) {
        _uiState.value = _uiState.value.copy(opisanie = value)
    }

    fun onPhotoPicked(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    /** Переключение между «выбрать делянку» и «указать вручную» — обе ветки хранятся в стейте. */
    fun setLocationMode(mode: WorkReportLocationMode) {
        _uiState.value = _uiState.value.copy(locationMode = mode, error = null)
    }

    fun openDelyankaPicker() {
        _uiState.value = _uiState.value.copy(showDelyankaPicker = true)
        if (_uiState.value.delyanki.isEmpty() && !_uiState.value.isLoadingDelyanki) {
            loadDelyanki()
        }
    }

    fun dismissDelyankaPicker() {
        _uiState.value = _uiState.value.copy(showDelyankaPicker = false)
    }

    fun onDelyankaSearchQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(delyankaSearchQuery = value)
    }

    fun retryLoadDelyanki() = loadDelyanki()

    private fun loadDelyanki() {
        _uiState.value = _uiState.value.copy(isLoadingDelyanki = true, delyankiError = null)
        viewModelScope.launch {
            val result = repository.getDelyankiForMap()
            _uiState.value = result.fold(
                onSuccess = { list -> _uiState.value.copy(isLoadingDelyanki = false, delyanki = list) },
                onFailure = { err ->
                    _uiState.value.copy(
                        isLoadingDelyanki = false,
                        delyankiError = err.message ?: "Не удалось загрузить список делянок",
                    )
                },
            )
        }
    }

    fun selectDelyanka(delyanka: DelyankaMapRefDto) {
        _uiState.value = _uiState.value.copy(
            selectedDelyanka = delyanka,
            locationMode = WorkReportLocationMode.DELYANKA,
            showDelyankaPicker = false,
            error = null,
        )
    }

    fun clearSelectedDelyanka() {
        _uiState.value = _uiState.value.copy(selectedDelyanka = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.tipRaboty.isBlank()) {
            _uiState.value = state.copy(error = "Укажите тип работы")
            return
        }
        if (state.locationMode == WorkReportLocationMode.DELYANKA && state.selectedDelyanka == null) {
            _uiState.value = state.copy(error = "Выберите делянку или перейдите на ручной ввод квартала/выдела")
            return
        }

        // Локация отчёта: либо реальная делянка (шлём её id и её квартал/выдел), либо то, что
        // человек ввёл руками — работа велась не на заведённой делянке.
        val delyanka = state.selectedDelyanka.takeIf { state.locationMode == WorkReportLocationMode.DELYANKA }
        val kvartal = delyanka?.kvartal ?: state.kvartal
        val vydels = delyanka?.vydel?.let { listOf(it) } ?: state.vydels

        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val location = if (hasLocationPermission(context)) getCurrentLocationOrNull(context) else null
            val lat = location?.latitude
            val lon = location?.longitude

            var photoPath: String? = null
            val uri = state.photoUri
            if (uri != null) {
                val uploadResult = repository.uploadPhoto(context, uri)
                val uploadError = uploadResult.exceptionOrNull()
                if (uploadError is ConnectivityException) {
                    queueManager.enqueueReport(
                        tipRaboty = state.tipRaboty.trim(),
                        kvartal = kvartal,
                        vydels = vydels,
                        opisanie = state.opisanie,
                        photoUri = uri,
                        uploadedPhotoPath = null,
                        lat = lat,
                        lon = lon,
                    )
                    _uiState.value = WorkReportUiState(queuedOffline = true)
                    return@launch
                }
                uploadResult.onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = it.message ?: "Не удалось загрузить фото",
                    )
                    return@launch
                }
                photoPath = uploadResult.getOrNull()
            }

            val result = repository.submitReport(
                tipRaboty = state.tipRaboty.trim(),
                kvartal = kvartal,
                vydels = vydels,
                opisanie = state.opisanie,
                photoPath = photoPath,
                lat = lat,
                lon = lon,
            )
            val error = result.exceptionOrNull()
            if (error is ConnectivityException) {
                queueManager.enqueueReport(
                    tipRaboty = state.tipRaboty.trim(),
                    kvartal = kvartal,
                    vydels = vydels,
                    opisanie = state.opisanie,
                    photoUri = null,
                    uploadedPhotoPath = photoPath,
                    lat = lat,
                    lon = lon,
                )
                _uiState.value = WorkReportUiState(queuedOffline = true)
                return@launch
            }
            _uiState.value = result.fold(
                onSuccess = { WorkReportUiState(submitted = true) },
                onFailure = { _uiState.value.copy(isSubmitting = false, error = it.message ?: "Не удалось отправить отчёт") },
            )
        }
    }

    fun resetSubmitted() {
        _uiState.value = WorkReportUiState()
    }
}

/** Подстроковый, регистронезависимый фильтр делянок по названию/кварталу/выделу/лесничеству. */
fun List<DelyankaMapRefDto>.filterBySearch(query: String): List<DelyankaMapRefDto> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { d ->
        listOfNotNull(d.nazvanie, d.kvartal, d.vydel, d.lesnichestvo).any {
            it.contains(trimmed, ignoreCase = true)
        }
    }
}
