package com.lesovod.mobile.ui.bot

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.location.CurrentPlace
import com.lesovod.mobile.data.location.getCurrentLocationOrNull
import com.lesovod.mobile.data.location.hasLocationPermission
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.data.local.FieldDataCache
import com.lesovod.mobile.ui.navigation.WorkReportPrefill
import com.lesovod.mobile.ui.navigation.WorkReportPrefillRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Как заполнена локация отчёта: делянка, участок лесных культур или ручной ввод квартала/выдела. */
enum class WorkReportLocationMode(val label: String) {
    DELYANKA("Делянка"),
    LESOKULTURY("Лесные культуры"),
    MANUAL("Вручную"),
}

data class WorkReportUiState(
    val tipRaboty: String = "",
    // Ручной ввод (запасной путь, если работа велась не на заведённой делянке).
    val kvartal: String = "",
    val vydelInput: String = "",
    val vydels: List<String> = emptyList(),
    val opisanie: String = "",
    /** Объём, м³ — например, итог партии из Кубатурника; уходит в описание отчёта. */
    val obyom: String = "",
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
    // Участок лесных культур — для работ по уходу за культурами.
    val uchastki: List<LesokulturyUchastok> = emptyList(),
    val selectedUchastok: LesokulturyUchastok? = null,
    val isLoadingUchastki: Boolean = false,
    val uchastkiError: String? = null,
    /** «Подставлено из задачи на сегодня: …» / «Из Кубатурника: …» — что заполнено автоматически. */
    val prefillNote: String? = null,
)

class WorkReportViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val queueManager = OfflineQueueManager.getInstance(application)
    private val cache = FieldDataCache(application)

    val session = sessionManager.session

    // Где человек стоит по GPS (карта определила квартал/выдел) — подставляем в ручной ввод,
    // чтобы не набирать номера на морозе и не ошибаться в них.
    private val place = CurrentPlace.fresh()

    private val _uiState = MutableStateFlow(
        WorkReportUiState(
            kvartal = place?.kvartal.orEmpty(),
            vydels = listOfNotNull(place?.vydel),
        ),
    )
    val uiState = _uiState.asStateFlow()

    /** Человек сам выбрал делянку/режим — автоподстановка из задачи его выбор не перетирает. */
    private var locationTouched = false

    init {
        if (place?.vydel != null) loadDelyanki()
        prefillFromTodayTask()
        // «Добавить в отчёт» из Кубатурника: объём партии и сорта
        viewModelScope.launch {
            WorkReportPrefillRequest.prefill.filterNotNull().collect {
                WorkReportPrefillRequest.consume()?.let { applyKubaturnikPrefill(it) }
            }
        }
    }

    private fun applyKubaturnikPrefill(prefill: WorkReportPrefill) {
        val state = _uiState.value
        _uiState.value = state.copy(
            obyom = String.format(Locale.US, "%.3f", prefill.obyom),
            opisanie = if (state.opisanie.isBlank()) prefill.opisanie else state.opisanie,
            submitted = false,
            queuedOffline = false,
            prefillNote = "Объём подставлен из Кубатурника",
        )
    }

    /**
     * Есть активная задача на сегодня, привязанная к делянке (план работ, как на «Смене»), — подставляем
     * её делянку (или квартал/выдел вручную, если делянку в справочнике не нашли). Человек может поменять.
     * Без связи задачи берутся из сохранённых («Подготовиться к выезду»).
     */
    private fun prefillFromTodayTask() {
        viewModelScope.launch {
            val tasks = repository.listWorkPlan().getOrNull()
                ?.also { cache.saveWorkPlan(it) }
                ?: cache.loadWorkPlan()?.data
                ?: return@launch
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val withPlace = tasks.filter { !it.kvartal.isNullOrBlank() }
            val task = withPlace.firstOrNull { it.data.take(10) == today }
                ?: withPlace.filter { it.data.take(10) < today }.maxByOrNull { it.data }
                ?: return@launch
            val kvartal = task.kvartal?.trim().orEmpty()
            val vydel = task.vydel?.trim()
            if (locationTouched) return@launch

            val delyanki = _uiState.value.delyanki.ifEmpty { repository.getDelyankiForMap().getOrNull().orEmpty() }
            val match = delyanki.filter { it.kvartal?.trim() == kvartal && (vydel == null || it.vydel?.trim() == vydel) }
                .maxByOrNull { it.delyankaId }
            if (locationTouched) return@launch
            val note = "Подставлено из задачи: ${task.zadacha}"
            _uiState.value = if (match != null) {
                _uiState.value.copy(
                    delyanki = delyanki,
                    selectedDelyanka = match,
                    locationMode = WorkReportLocationMode.DELYANKA,
                    tipRaboty = _uiState.value.tipRaboty.ifBlank { task.zadacha },
                    prefillNote = note,
                )
            } else {
                _uiState.value.copy(
                    delyanki = delyanki,
                    locationMode = WorkReportLocationMode.MANUAL,
                    kvartal = kvartal,
                    vydels = listOfNotNull(vydel),
                    tipRaboty = _uiState.value.tipRaboty.ifBlank { task.zadacha },
                    prefillNote = note,
                )
            }
        }
    }

    fun onObyomChange(value: String) {
        _uiState.value = _uiState.value.copy(obyom = value.filter { it.isDigit() || it == '.' || it == ',' })
    }

    fun onTipRabotyChange(value: String) {
        _uiState.value = _uiState.value.copy(tipRaboty = value, error = null)
    }

    fun onKvartalChange(value: String) {
        locationTouched = true
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
        locationTouched = true
        _uiState.value = _uiState.value.copy(locationMode = mode, error = null)
        if (mode == WorkReportLocationMode.LESOKULTURY && _uiState.value.uchastki.isEmpty() && !_uiState.value.isLoadingUchastki) {
            loadUchastki()
        }
    }

    fun retryLoadUchastki() = loadUchastki()

    private fun loadUchastki() {
        _uiState.value = _uiState.value.copy(isLoadingUchastki = true, uchastkiError = null)
        viewModelScope.launch {
            val result = repository.listLesokulturyUchastki()
            _uiState.value = result.fold(
                onSuccess = { list -> _uiState.value.copy(isLoadingUchastki = false, uchastki = list) },
                onFailure = { err ->
                    _uiState.value.copy(
                        isLoadingUchastki = false,
                        uchastkiError = err.message ?: "Не удалось загрузить участки лесных культур",
                    )
                },
            )
        }
    }

    fun selectUchastok(uchastok: LesokulturyUchastok?) {
        _uiState.value = _uiState.value.copy(selectedUchastok = uchastok, error = null)
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
                onSuccess = { list ->
                    // делянка, на которой человек стоит по GPS, — выбрана сразу
                    val here = place?.takeIf { _uiState.value.selectedDelyanka == null }?.let { p ->
                        list.filter { it.kvartal?.trim() == p.kvartal && p.vydel != null && it.vydel?.trim() == p.vydel }
                            .maxByOrNull { it.delyankaId }
                    }
                    _uiState.value.copy(
                        isLoadingDelyanki = false,
                        delyanki = list,
                        selectedDelyanka = _uiState.value.selectedDelyanka ?: here,
                    )
                },
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
        locationTouched = true
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
        if (state.locationMode == WorkReportLocationMode.LESOKULTURY && state.selectedUchastok == null) {
            _uiState.value = state.copy(error = "Выберите участок лесных культур")
            return
        }

        // Локация отчёта: реальная делянка или участок л/к (шлём id и их квартал/выдел), либо то,
        // что человек ввёл руками — работа велась не на заведённом объекте.
        val delyanka = state.selectedDelyanka.takeIf { state.locationMode == WorkReportLocationMode.DELYANKA }
        val uchastok = state.selectedUchastok.takeIf { state.locationMode == WorkReportLocationMode.LESOKULTURY }
        val kvartal = delyanka?.kvartal ?: uchastok?.kvartal ?: state.kvartal
        val vydels = delyanka?.vydel?.let { listOf(it) } ?: uchastok?.vydel?.let { listOf(it) } ?: state.vydels
        val delyankaId = delyanka?.delyankaId
        val uchastokId = uchastok?.id
        // У отчёта на сервере нет отдельного поля объёма — объём дописываем в описание.
        val obyomValue = state.obyom.replace(',', '.').toDoubleOrNull()
        val opisanie = listOfNotNull(
            state.opisanie.trim().takeIf { it.isNotBlank() },
            obyomValue?.let { "Объём: ${String.format(Locale.US, "%.3f", it)} м³" },
        ).joinToString("\n")

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
                        opisanie = opisanie,
                        photoUri = uri,
                        uploadedPhotoPath = null,
                        lat = lat,
                        lon = lon,
                        delyankaId = delyankaId,
                        lesokulturyUchastokId = uchastokId,
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
                opisanie = opisanie,
                photoPath = photoPath,
                lat = lat,
                lon = lon,
                delyankaId = delyankaId,
                lesokulturyUchastokId = uchastokId,
            )
            val error = result.exceptionOrNull()
            if (error is ConnectivityException) {
                queueManager.enqueueReport(
                    tipRaboty = state.tipRaboty.trim(),
                    kvartal = kvartal,
                    vydels = vydels,
                    opisanie = opisanie,
                    photoUri = null,
                    uploadedPhotoPath = photoPath,
                    lat = lat,
                    lon = lon,
                    delyankaId = delyankaId,
                    lesokulturyUchastokId = uchastokId,
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
