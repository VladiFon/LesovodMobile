package com.lesovod.mobile.ui.bot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.FieldDataCache
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.MyDelyankaDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.FieldPrepManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.navigation.StockNavRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * «Мои делянки» → остаток в одно касание (анализ удобства 01.10.2026): вместо «квартал → делянка →
 * выдел» — список делянок с освоением, «Мои» сверху. Последний удачный список и остатки по каждой
 * делянке лежат на устройстве ([FieldDataCache]) — без связи показываются с пометкой времени.
 */
data class StockUiState(
    val delyanki: List<MyDelyankaDto> = emptyList(),
    val isLoadingList: Boolean = false,
    /** «Данные на ДД.ММ чч:мм (нет связи)» — список взят из кэша; null — свежий с сервера. */
    val listStamp: String? = null,
    val listError: String? = null,
    /** Фильтр по кварталу/названию в списке. */
    val query: String = "",
    /** Открытая делянка (детали остатка); null — показываем список. */
    val selectedId: Int? = null,
    val selectedTitle: String? = null,
    val isLoadingRemaining: Boolean = false,
    val remaining: RemainingResponseDto? = null,
    val remainingStamp: String? = null,
    val error: String? = null,
)

class StockViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = BotRepository(NetworkModule.api, sessionManager)
    private val cache = FieldDataCache(application)
    private val prepManager = FieldPrepManager.getInstance(application)

    private val _uiState = MutableStateFlow(StockUiState())
    val uiState = _uiState.asStateFlow()

    /** Состояние кнопки «Подготовиться к выезду». */
    val prepState = prepManager.state

    init {
        loadList()
        // запрос «открой делянку» с карты/главного экрана — сразу в детали, минуя список
        viewModelScope.launch {
            StockNavRequest.delyankaId.filterNotNull().collect {
                StockNavRequest.consume()?.let { id -> openDelyanka(id) }
            }
        }
        // после «Подготовиться к выезду» список в кэше свежий — перечитываем
        viewModelScope.launch {
            prepManager.state.collect { if (it.result != null && _uiState.value.listStamp != null) loadList() }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun loadList() {
        _uiState.value = _uiState.value.copy(isLoadingList = true, listError = null)
        viewModelScope.launch {
            repository.listMyDelyanki().fold(
                onSuccess = { list ->
                    cache.saveMyDelyanki(list)
                    _uiState.value = _uiState.value.copy(isLoadingList = false, delyanki = list, listStamp = null)
                },
                onFailure = { err ->
                    val cached = cache.loadMyDelyanki()
                    _uiState.value = if (cached != null) {
                        _uiState.value.copy(
                            isLoadingList = false,
                            delyanki = cached.data,
                            listStamp = "${cached.stamp} (нет связи)",
                        )
                    } else {
                        _uiState.value.copy(isLoadingList = false, listError = err.message ?: "Не удалось загрузить делянки")
                    }
                },
            )
        }
    }

    fun openDelyanka(delyankaId: Int) {
        val title = _uiState.value.delyanki.firstOrNull { it.delyankaId == delyankaId }?.title()
            ?: cache.loadMyDelyanki()?.data?.firstOrNull { it.delyankaId == delyankaId }?.title()
        _uiState.value = _uiState.value.copy(
            selectedId = delyankaId,
            selectedTitle = title,
            remaining = null,
            remainingStamp = null,
            error = null,
        )
        loadRemaining()
    }

    fun closeDelyanka() {
        _uiState.value = _uiState.value.copy(selectedId = null, selectedTitle = null, remaining = null, remainingStamp = null, error = null)
    }

    fun loadRemaining() {
        val id = _uiState.value.selectedId ?: return
        _uiState.value = _uiState.value.copy(isLoadingRemaining = true, error = null)
        viewModelScope.launch {
            repository.getRemainingByDelyanka(id).fold(
                onSuccess = {
                    cache.saveRemaining(id, it)
                    if (_uiState.value.selectedId == id) {
                        _uiState.value = _uiState.value.copy(isLoadingRemaining = false, remaining = it, remainingStamp = null)
                    }
                },
                onFailure = { err ->
                    if (_uiState.value.selectedId != id) return@fold
                    val cached = cache.loadRemaining(id)
                    _uiState.value = if (cached != null) {
                        _uiState.value.copy(
                            isLoadingRemaining = false,
                            remaining = cached.data,
                            remainingStamp = "${cached.stamp} (нет связи)",
                        )
                    } else {
                        _uiState.value.copy(isLoadingRemaining = false, error = err.message, remaining = null)
                    }
                },
            )
        }
    }

    fun prepareForTrip() = prepManager.prepare()
}

/** Подпись делянки в списке: название или «кв. N, выд. M». */
fun MyDelyankaDto.title(): String =
    nazvanie?.takeIf { it.isNotBlank() } ?: listOfNotNull(
        kvartal?.takeIf { it.isNotBlank() }?.let { "кв. $it" },
        vydel?.takeIf { it.isNotBlank() }?.let { "выд. $it" },
    ).joinToString(", ").ifBlank { "Делянка №$delyankaId" }

/** Фильтр списка: по кварталу (точное совпадение номера или начало), названию или выделу. */
fun List<MyDelyankaDto>.filterByQuery(query: String): List<MyDelyankaDto> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { d ->
        val kvartaly = d.kvartal.orEmpty().split(',').map { it.trim() }
        kvartaly.any { it.equals(q, ignoreCase = true) || it.startsWith(q, ignoreCase = true) } ||
            d.nazvanie.orEmpty().contains(q, ignoreCase = true) ||
            d.vydel.orEmpty().split(',').any { it.trim().equals(q, ignoreCase = true) }
    }
}
