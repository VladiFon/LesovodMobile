package com.lesovod.mobile.ui.tabel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.TabelDayEntryDto
import com.lesovod.mobile.data.network.dto.TabelEntrySaveDto
import com.lesovod.mobile.data.network.dto.TabelLesokulturyUchastokDto
import com.lesovod.mobile.data.network.dto.VidRabotyDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.session.SessionManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Статусы табеля — код для сервера и подпись для UI, порядок как в прототипе. */
enum class TabelStatus(val wireValue: String, val displayName: String) {
    WORKED("работал", "Работал"),
    NOT_WORKED("не работал", "Не работал"),
    SICK("больничный", "Больничный"),
    VACATION("отпуск", "Отпуск"),
    DAY_OFF("выходной", "Выходной");

    companion object {
        fun fromWireValue(value: String?): TabelStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Место работы, выбранное для строки табеля — либо делянка, либо участок лесных культур. */
sealed class TabelPlace {
    abstract val label: String

    data class Delyanka(val delyankaItemId: Int, override val label: String) : TabelPlace()
    data class Lesokultury(val uchastokId: Int, override val label: String) : TabelPlace()
}

/** Строка табеля в редактируемом состоянии экрана — обёртка над ответом сервера плюс локальные правки. */
data class TabelRowState(
    val sotrudnikId: Int,
    val fio: String,
    val dolzhnost: String,
    val status: TabelStatus?,
    val place: TabelPlace?,
    val vidRabotyId: Int?,
    val vidRabotyNazvanie: String?,
    val kommentariy: String,
    val dirty: Boolean,
) {
    companion object {
        fun fromDto(dto: TabelDayEntryDto): TabelRowState {
            val place = when {
                dto.delyankaItemId != null -> TabelPlace.Delyanka(
                    dto.delyankaItemId,
                    "кв. ${dto.dKvartal.orEmpty()} · выд. ${dto.dVydel.orEmpty()}",
                )
                dto.lesokulturyUchastokId != null -> TabelPlace.Lesokultury(
                    dto.lesokulturyUchastokId,
                    "кв. ${dto.lkuKvartal.orEmpty()} · выд. ${dto.lkuVydel.orEmpty()} · культуры",
                )
                else -> null
            }
            return TabelRowState(
                sotrudnikId = dto.sotrudnikId,
                fio = dto.fio,
                dolzhnost = dto.dolzhnost.orEmpty(),
                status = TabelStatus.fromWireValue(dto.status),
                place = place,
                vidRabotyId = dto.vidRabotyId,
                vidRabotyNazvanie = dto.vidRabotyNazvanie,
                kommentariy = dto.kommentariy.orEmpty(),
                dirty = false,
            )
        }
    }
}

/** Состояние поиска лесных культур в диалоге «Место работы» — экран-как-в-прототипе. */
sealed class LesokulturySearchState {
    data object Idle : LesokulturySearchState()
    data object Loading : LesokulturySearchState()
    data object Empty : LesokulturySearchState()
    data class Results(val items: List<TabelLesokulturyUchastokDto>) : LesokulturySearchState()
}

data class TabelUiState(
    val date: LocalDate = LocalDate.now(),
    val rows: List<TabelRowState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val query: String = "",
    val vidyRabot: List<VidRabotyDto> = emptyList(),
) {
    val filteredRows: List<TabelRowState>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return rows
            return rows.filter { (it.fio + " " + it.dolzhnost).contains(q, ignoreCase = true) }
        }
    val filledCount: Int get() = rows.count { it.status != null }
    val changedCount: Int get() = rows.count { it.dirty }
    val isToday: Boolean get() = date == LocalDate.now()
}

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class TabelViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)

    private val _uiState = MutableStateFlow(TabelUiState())
    val uiState = _uiState.asStateFlow()

    // Кэш уже загруженных дней, чтобы навигация «назад-вперёд» по датам не всегда дёргала сеть
    // заново и не теряла несохранённые правки при случайном обратном переходе на тот же день.
    private val dayCache = mutableMapOf<String, List<TabelRowState>>()

    init {
        loadCurrentDay()
        loadVidyRabot()
    }

    private fun dateKey(date: LocalDate): String = date.format(ISO_DATE)

    fun loadCurrentDay() {
        val date = _uiState.value.date
        val cached = dayCache[dateKey(date)]
        if (cached != null) {
            _uiState.value = _uiState.value.copy(rows = cached, isLoading = false, error = null)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = repository.getTabelDay(dateKey(date))
            result.fold(
                onSuccess = { list ->
                    val rows = list.map { TabelRowState.fromDto(it) }
                    dayCache[dateKey(date)] = rows
                    // Дата могла успеть перещёлкнуться, пока шёл запрос — не перетираем чужой день.
                    if (_uiState.value.date == date) {
                        _uiState.value = _uiState.value.copy(rows = rows, isLoading = false, error = null)
                    }
                },
                onFailure = { e ->
                    if (_uiState.value.date == date) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Не удалось загрузить табель")
                    }
                },
            )
        }
    }

    private fun loadVidyRabot() {
        viewModelScope.launch {
            repository.listVidyRabot().onSuccess { list ->
                _uiState.value = _uiState.value.copy(vidyRabot = list)
            }
        }
    }

    fun goToDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date, query = "", saveMessage = null)
        loadCurrentDay()
    }

    fun goToPreviousDay() = goToDate(_uiState.value.date.minusDays(1))
    fun goToNextDay() = goToDate(_uiState.value.date.plusDays(1))

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    private fun updateRow(sotrudnikId: Int, transform: (TabelRowState) -> TabelRowState) {
        val updated = _uiState.value.rows.map { row ->
            if (row.sotrudnikId == sotrudnikId) transform(row) else row
        }
        _uiState.value = _uiState.value.copy(rows = updated, saveMessage = null)
        dayCache[dateKey(_uiState.value.date)] = updated
    }

    fun setStatus(sotrudnikId: Int, status: TabelStatus) {
        updateRow(sotrudnikId) { row ->
            val newStatus = if (row.status == status) null else status
            row.copy(status = newStatus, dirty = true)
        }
    }

    fun setPlace(sotrudnikId: Int, place: TabelPlace?) {
        updateRow(sotrudnikId) { it.copy(place = place, dirty = true) }
    }

    fun setVidRaboty(sotrudnikId: Int, id: Int?, nazvanie: String?) {
        updateRow(sotrudnikId) { it.copy(vidRabotyId = id, vidRabotyNazvanie = nazvanie, dirty = true) }
    }

    fun setKommentariy(sotrudnikId: Int, text: String) {
        updateRow(sotrudnikId) { it.copy(kommentariy = text, dirty = true) }
    }

    /** Создаёт (или переиспользует существующий) вид работы и сразу выбирает его для сотрудника. */
    fun createAndSelectVidRaboty(sotrudnikId: Int, nazvanie: String) {
        val trimmed = nazvanie.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.createVidRaboty(trimmed).onSuccess { vid ->
                if (_uiState.value.vidyRabot.none { it.id == vid.id }) {
                    _uiState.value = _uiState.value.copy(vidyRabot = _uiState.value.vidyRabot + vid)
                }
                setVidRaboty(sotrudnikId, vid.id, vid.nazvanie)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Не удалось создать вид работы")
            }
        }
    }

    fun saveDay() {
        val state = _uiState.value
        val dirty = state.rows.filter { it.dirty }
        if (dirty.isEmpty()) {
            _uiState.value = state.copy(saveMessage = "Нет изменений")
            return
        }
        val entries = dirty.mapNotNull { row ->
            val status = row.status ?: return@mapNotNull null
            TabelEntrySaveDto(
                sotrudnikId = row.sotrudnikId,
                status = status.wireValue,
                delyankaItemId = (row.place as? TabelPlace.Delyanka)?.delyankaItemId,
                lesokulturyUchastokId = (row.place as? TabelPlace.Lesokultury)?.uchastokId,
                vidRabotyId = row.vidRabotyId,
                kommentariy = row.kommentariy,
            )
        }
        if (entries.isEmpty()) {
            _uiState.value = state.copy(saveMessage = "Отметьте статус хотя бы у одного изменённого сотрудника")
            return
        }
        _uiState.value = state.copy(isSaving = true, error = null, saveMessage = null)
        viewModelScope.launch {
            val result = repository.saveTabelDay(dateKey(state.date), entries)
            result.fold(
                onSuccess = { list ->
                    val rows = list.map { TabelRowState.fromDto(it) }
                    dayCache[dateKey(state.date)] = rows
                    if (_uiState.value.date == state.date) {
                        _uiState.value = _uiState.value.copy(
                            rows = rows,
                            isSaving = false,
                            saveMessage = "Сохранено строк: ${entries.size}",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "Не удалось сохранить табель")
                },
            )
        }
    }

    // ---------- поиск участков лесных культур для пикера «Место работы» ----------

    private val _lesokulturySearch = MutableStateFlow<LesokulturySearchState>(LesokulturySearchState.Idle)
    val lesokulturySearch = _lesokulturySearch.asStateFlow()
    private var searchJob: Job? = null

    fun searchLesokultury(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _lesokulturySearch.value = LesokulturySearchState.Idle
            return
        }
        _lesokulturySearch.value = LesokulturySearchState.Loading
        searchJob = viewModelScope.launch {
            delay(300) // дебаунс, как в прототипе
            repository.listTabelLesokulturyUchastki(query).fold(
                onSuccess = { list ->
                    _lesokulturySearch.value = if (list.isEmpty()) {
                        LesokulturySearchState.Empty
                    } else {
                        LesokulturySearchState.Results(list)
                    }
                },
                onFailure = {
                    _lesokulturySearch.value = LesokulturySearchState.Empty
                },
            )
        }
    }

    fun resetLesokulturySearch() {
        searchJob?.cancel()
        _lesokulturySearch.value = LesokulturySearchState.Idle
    }

    /**
     * Поиск делянок по кварталу/выделу — client-side фильтр уже загруженного списка
     * GET /api/delyanki/for-map. ВАЖНО: этот справочник отдаёт delyanka_id (id самой
     * делянки), а не id конкретного delyanka_item (выдела) — см. докстринг
     * list_delyanka_items_for_map на бэкенде (SELECT ... i.delyanka_id ..., без i.id).
     * tabel_zapis.delyanka_item_id ссылается именно на delyanka_item.id, которого этот
     * список не содержит, поэтому подставлять сюда delyankaId было бы записью в чужой
     * внешний ключ — вместо этого результаты показываются только для справки, а выбор
     * (запись delyanka_item_id) в этой вкладке отключён. См. отчёт по задаче.
     */
    suspend fun searchDelyankiForMapLabelsOnly(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val all = repository.getDelyankiForMap().getOrNull().orEmpty()
        return all.filter {
            listOfNotNull(it.kvartal, it.vydel, it.lesnichestvo, it.nazvanie)
                .any { field -> field.contains(q, ignoreCase = true) }
        }.map { "кв. ${it.kvartal.orEmpty()} · выд. ${it.vydel.orEmpty()} · ${it.nazvanie.orEmpty()}" }
    }
}
