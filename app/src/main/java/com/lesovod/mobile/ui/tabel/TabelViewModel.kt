package com.lesovod.mobile.ui.tabel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.DelyankaByLocationDto
import com.lesovod.mobile.data.network.dto.TabelLesokulturyUchastokDto
import com.lesovod.mobile.data.network.dto.VidRabotyDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TabelPane { MAIN, PLACE, WORK }
enum class PlaceTab { DELYANKA, LESOKULTURY }

data class TabelUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val date: LocalDate = LocalDate.now(),
    val employees: List<TabelEmployee> = emptyList(),
    val searchQuery: String = "",
    val saving: Boolean = false,
    val saveMessage: String? = null,

    val pane: TabelPane = TabelPane.MAIN,
    val editingEmployeeId: Int? = null,
    /** Сотрудник, у которого сейчас открыто инлайн-поле комментария на главном экране. */
    val editingCommentId: Int? = null,

    // Экран «Место работы»
    val placeTab: PlaceTab = PlaceTab.DELYANKA,
    val placeKvartal: String = "",
    val placeVydel: String = "",
    val delyankaLoading: Boolean = false,
    val delyankaSearched: Boolean = false,
    val delyankaResults: List<DelyankaByLocationDto> = emptyList(),
    val delyankaSearchedKvartal: String = "",
    val delyankaSearchedVydel: String = "",
    val kulQuery: String = "",
    val kulLoading: Boolean = false,
    val kulSearched: Boolean = false,
    val kulResults: List<TabelLesokulturyUchastokDto> = emptyList(),
    val selectedPlace: TabelPlace? = null,

    // Экран «Вид работы»
    val vidyRaboty: List<VidRabotyDto> = emptyList(),
    val newWorkTypeOpen: Boolean = false,
    val creatingWorkType: Boolean = false,
    val selectedWorkTypeId: Int? = null,
    val selectedWorkTypeLabel: String? = null,
) {
    val editingEmployee: TabelEmployee?
        get() = employees.firstOrNull { it.sotrudnikId == editingEmployeeId }

    val filledCount: Int get() = employees.count { it.status != null }
    val dirtyCount: Int get() = employees.count { it.dirty }
}

class TabelViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

    private val _uiState = MutableStateFlow(TabelUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDay(_uiState.value.date)
        loadVidyRaboty()
    }

    private fun dateKey(date: LocalDate): String = date.toString()

    fun loadDay(date: LocalDate) {
        _uiState.value = _uiState.value.copy(loading = true, error = null, date = date)
        viewModelScope.launch {
            val result = repository.getTabelDay(dateKey(date))
            _uiState.value = result.fold(
                onSuccess = { dtos -> _uiState.value.copy(loading = false, employees = dtos.map { it.toEmployee() }) },
                onFailure = { _uiState.value.copy(loading = false, error = it.message) },
            )
        }
    }

    private fun loadVidyRaboty() {
        viewModelScope.launch {
            // Тихо: если не удалось — список видов работы просто пуст, кроме «+ новый вид работы» ничего не покажется.
            repository.listVidyRaboty().onSuccess { list ->
                _uiState.value = _uiState.value.copy(vidyRaboty = list.sortedBy { it.nazvanie })
            }
        }
    }

    /** Переключение дня — несохранённые правки текущего дня останутся несохранёнными, как и в вебе. */
    fun changeDate(deltaDays: Long) {
        val state = _uiState.value
        val hadUnsaved = state.dirtyCount > 0
        loadDay(state.date.plusDays(deltaDays))
        if (hadUnsaved) {
            _uiState.value = _uiState.value.copy(saveMessage = "Несохранённые изменения за предыдущий день не сохранены")
        }
    }

    fun goToToday() {
        if (_uiState.value.date != LocalDate.now()) loadDay(LocalDate.now())
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /** Тап по чипу статуса — повторный тап по уже выбранному снимает статус, как и в вебе. */
    fun setStatus(sotrudnikId: Int, status: TabelStatus) {
        updateEmployee(sotrudnikId) { e ->
            e.copy(status = if (e.status == status) null else status, dirty = true)
        }
    }

    fun startEditComment(sotrudnikId: Int) {
        _uiState.value = _uiState.value.copy(editingCommentId = sotrudnikId)
    }

    fun commitComment(sotrudnikId: Int, text: String) {
        val trimmed = text.trim()
        updateEmployee(sotrudnikId) { e -> if (e.kommentariy != trimmed) e.copy(kommentariy = trimmed, dirty = true) else e }
        if (_uiState.value.editingCommentId == sotrudnikId) {
            _uiState.value = _uiState.value.copy(editingCommentId = null)
        }
    }

    private inline fun updateEmployee(sotrudnikId: Int, transform: (TabelEmployee) -> TabelEmployee) {
        val state = _uiState.value
        _uiState.value = state.copy(
            employees = state.employees.map { if (it.sotrudnikId == sotrudnikId) transform(it) else it },
        )
    }

    fun saveDay() {
        val state = _uiState.value
        val entries = state.employees.filter { it.dirty }.mapNotNull { it.toEntryRequest() }
        if (entries.isEmpty()) {
            _uiState.value = state.copy(saveMessage = "Нет изменений")
            return
        }
        _uiState.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = repository.saveTabelDay(dateKey(state.date), entries)
            _uiState.value = result.fold(
                onSuccess = { dtos ->
                    _uiState.value.copy(
                        saving = false,
                        employees = dtos.map { it.toEmployee() },
                        saveMessage = "Отправлено строк: ${entries.size} (только изменённые)",
                    )
                },
                onFailure = { _uiState.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun dismissSaveMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ---------- Место работы (делянка / лесные культуры) ----------

    fun openPlacePicker(sotrudnikId: Int) {
        val employee = _uiState.value.employees.firstOrNull { it.sotrudnikId == sotrudnikId } ?: return
        _uiState.value = _uiState.value.copy(
            pane = TabelPane.PLACE,
            editingEmployeeId = sotrudnikId,
            placeTab = PlaceTab.DELYANKA,
            placeKvartal = "",
            placeVydel = "",
            delyankaLoading = false,
            delyankaSearched = false,
            delyankaResults = emptyList(),
            delyankaSearchedKvartal = "",
            delyankaSearchedVydel = "",
            kulQuery = "",
            kulLoading = false,
            kulSearched = false,
            kulResults = emptyList(),
            selectedPlace = employee.place,
        )
    }

    fun setPlaceTab(tab: PlaceTab) {
        _uiState.value = _uiState.value.copy(placeTab = tab, selectedPlace = null)
    }

    fun onPlaceKvartalChange(value: String) {
        _uiState.value = _uiState.value.copy(placeKvartal = value)
    }

    fun onPlaceVydelChange(value: String) {
        _uiState.value = _uiState.value.copy(placeVydel = value)
    }

    fun findDelyanka() {
        val state = _uiState.value
        val kvartal = state.placeKvartal.trim()
        val vydel = state.placeVydel.trim()
        if (kvartal.isEmpty() || vydel.isEmpty()) return

        _uiState.value = state.copy(delyankaLoading = true, selectedPlace = null)
        viewModelScope.launch {
            val result = repository.getDelyankiByLocation(kvartal, vydel)
            _uiState.value = result.fold(
                onSuccess = { results ->
                    _uiState.value.copy(
                        delyankaLoading = false,
                        delyankaSearched = true,
                        delyankaResults = results,
                        delyankaSearchedKvartal = kvartal,
                        delyankaSearchedVydel = vydel,
                        selectedPlace = results.singleOrNull()
                            ?.let { TabelPlace.Delyanka(it.itemId, it.toPlaceLabel(kvartal, vydel)) },
                    )
                },
                onFailure = {
                    _uiState.value.copy(
                        delyankaLoading = false, delyankaSearched = true,
                        delyankaResults = emptyList(), error = it.message,
                    )
                },
            )
        }
    }

    fun selectDelyankaResult(dto: DelyankaByLocationDto) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedPlace = TabelPlace.Delyanka(
                dto.itemId,
                dto.toPlaceLabel(state.delyankaSearchedKvartal, state.delyankaSearchedVydel),
            ),
        )
    }

    fun onKulQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(kulQuery = query, selectedPlace = null)
    }

    /**
     * Вызывается из LaunchedEffect(kulQuery) после debounce — не оборачиваем в viewModelScope.launch
     * специально: пусть отмену (смена запроса до ответа) делает сам LaunchedEffect через структурную
     * конкурентность, тогда устаревший ответ просто не применится, без отдельного токена/счётчика.
     */
    suspend fun searchLesokultury(query: String) {
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(kulLoading = false, kulSearched = false, kulResults = emptyList())
            return
        }
        _uiState.value = _uiState.value.copy(kulLoading = true)
        val result = repository.searchTabelLesokulturyUchastki(query.trim())
        _uiState.value = result.fold(
            onSuccess = { _uiState.value.copy(kulLoading = false, kulSearched = true, kulResults = it) },
            onFailure = {
                _uiState.value.copy(kulLoading = false, kulSearched = true, kulResults = emptyList(), error = it.message)
            },
        )
    }

    fun selectKulResult(dto: TabelLesokulturyUchastokDto) {
        _uiState.value = _uiState.value.copy(selectedPlace = TabelPlace.Lesokultury(dto.id, dto.toPlaceLabel()))
    }

    fun confirmPlace() {
        val state = _uiState.value
        val employeeId = state.editingEmployeeId ?: return
        updateEmployee(employeeId) { it.copy(place = state.selectedPlace, dirty = true) }
        closePane()
    }

    fun clearPlace() {
        val employeeId = _uiState.value.editingEmployeeId ?: return
        updateEmployee(employeeId) { it.copy(place = null, dirty = true) }
        closePane()
    }

    // ---------- Вид работы ----------

    fun openWorkPicker(sotrudnikId: Int) {
        val employee = _uiState.value.employees.firstOrNull { it.sotrudnikId == sotrudnikId } ?: return
        _uiState.value = _uiState.value.copy(
            pane = TabelPane.WORK,
            editingEmployeeId = sotrudnikId,
            newWorkTypeOpen = false,
            selectedWorkTypeId = employee.vidRabotyId,
            selectedWorkTypeLabel = employee.vidRabotyLabel,
        )
        if (_uiState.value.vidyRaboty.isEmpty()) loadVidyRaboty()
    }

    fun selectWorkType(dto: VidRabotyDto) {
        _uiState.value = _uiState.value.copy(
            selectedWorkTypeId = dto.id,
            selectedWorkTypeLabel = dto.nazvanie,
            newWorkTypeOpen = false,
        )
    }

    fun toggleNewWorkType() {
        _uiState.value = _uiState.value.copy(newWorkTypeOpen = !_uiState.value.newWorkTypeOpen)
    }

    fun createWorkType(nazvanie: String) {
        val trimmed = nazvanie.trim()
        if (trimmed.isEmpty() || _uiState.value.creatingWorkType) return
        _uiState.value = _uiState.value.copy(creatingWorkType = true)
        viewModelScope.launch {
            val result = repository.createVidRaboty(trimmed)
            _uiState.value = result.fold(
                onSuccess = { dto ->
                    val state = _uiState.value
                    val already = state.vidyRaboty.any { it.id == dto.id }
                    state.copy(
                        creatingWorkType = false,
                        vidyRaboty = if (already) state.vidyRaboty else (state.vidyRaboty + dto).sortedBy { it.nazvanie },
                        selectedWorkTypeId = dto.id,
                        selectedWorkTypeLabel = dto.nazvanie,
                        newWorkTypeOpen = false,
                    )
                },
                onFailure = { _uiState.value.copy(creatingWorkType = false, error = it.message) },
            )
        }
    }

    fun confirmWorkType() {
        val state = _uiState.value
        val employeeId = state.editingEmployeeId ?: return
        updateEmployee(employeeId) {
            it.copy(vidRabotyId = state.selectedWorkTypeId, vidRabotyLabel = state.selectedWorkTypeLabel, dirty = true)
        }
        closePane()
    }

    fun clearWorkType() {
        val employeeId = _uiState.value.editingEmployeeId ?: return
        updateEmployee(employeeId) { it.copy(vidRabotyId = null, vidRabotyLabel = null, dirty = true) }
        closePane()
    }

    fun closePane() {
        _uiState.value = _uiState.value.copy(pane = TabelPane.MAIN, editingEmployeeId = null)
    }
}
