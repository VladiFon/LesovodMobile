package com.lesovod.mobile.ui.kubaturnik

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lesovod.mobile.data.local.KubaturnikBatchStore
import com.lesovod.mobile.data.local.KubaturnikBlock
import com.lesovod.mobile.data.local.KubaturnikCalculation
import com.lesovod.mobile.data.local.KubaturnikCountEntry
import com.lesovod.mobile.data.local.KubaturnikSnapshot
import com.lesovod.mobile.data.local.KubaturnikTable
import com.lesovod.mobile.data.local.KubaturnikTableLoader
import com.lesovod.mobile.data.local.diameterButtons
import com.lesovod.mobile.data.local.volumeFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KubaturnikDestination(val label: String) {
    MACHINE("Машина"), TRAILER("Прицеп");

    companion object {
        fun fromWire(value: String): KubaturnikDestination = entries.firstOrNull { it.name == value } ?: MACHINE
    }
}

/** Диаметр -> количество отмеченных брёвен (в рамках одного сорт+назначение). */
typealias DiameterCounts = Map<Int, Int>

data class KubaturnikRow(val diameter: Int, val count: Int, val volumePerLog: Double, val totalVolume: Double)

data class ThicknessSummaryRow(
    val rangeLabel: String,
    val sort: String,
    val destination: KubaturnikDestination,
    val count: Int,
    val volume: Double,
)

data class KubaturnikUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val table: KubaturnikTable? = null,
    val selectedBlockId: String? = null,
    val selectedLengthIndex: Int = -1,
    val selectedLengthValue: Double = 0.0,
    val stepCm: Int = 2,
    val destination: KubaturnikDestination = KubaturnikDestination.MACHINE,
    val sorts: List<String> = listOf(DEFAULT_SORT),
    val activeSort: String = DEFAULT_SORT,
    // sort -> destination -> diameter -> count
    val counts: Map<String, Map<KubaturnikDestination, DiameterCounts>> = emptyMap(),
    val lastTap: Pair<Int, Double>? = null,
    val showAddSortDialog: Boolean = false,
    val showResetConfirm: Boolean = false,
    val outOfRangeLengthMessage: String? = null,
    /** Ранее сохранённые расчёты («Новая партия» архивирует сюда текущий, а не стирает его). */
    val calculations: List<KubaturnikCalculation> = emptyList(),
) {
    val lengthChosen: Boolean get() = selectedBlockId != null && selectedLengthIndex >= 0

    val selectedBlock: KubaturnikBlock?
        get() = table?.blocks?.firstOrNull { it.id == selectedBlockId }

    val diameterButtonValues: List<Int>
        get() = selectedBlock?.diameterButtons(stepCm).orEmpty()

    val currentCombo: DiameterCounts
        get() = counts[activeSort]?.get(destination).orEmpty()

    val currentComboRows: List<KubaturnikRow>
        get() {
            val block = selectedBlock ?: return emptyList()
            return currentCombo.entries
                .sortedBy { it.key }
                .map { (diameter, count) ->
                    val volumePerLog = block.volumeFor(diameter, selectedLengthIndex) ?: 0.0
                    KubaturnikRow(diameter, count, volumePerLog, volumePerLog * count)
                }
        }

    /** Итог партии целиком: машина + прицеп, все сорта вместе. */
    val totalVolume: Double
        get() {
            val block = selectedBlock ?: return 0.0
            return counts.values.sumOf { byDest ->
                byDest.values.sumOf { byDiam ->
                    byDiam.entries.sumOf { (diameter, count) -> (block.volumeFor(diameter, selectedLengthIndex) ?: 0.0) * count }
                }
            }
        }

    val totalLogCount: Int
        get() = counts.values.sumOf { byDest -> byDest.values.sumOf { byDiam -> byDiam.values.sum() } }

    fun thicknessSummary(): List<ThicknessSummaryRow> {
        val block = selectedBlock ?: return emptyList()
        val rows = mutableMapOf<Triple<String, String, KubaturnikDestination>, Pair<Int, Double>>()
        for ((sort, byDest) in counts) {
            for ((dest, byDiam) in byDest) {
                for ((diameter, count) in byDiam) {
                    if (count <= 0) continue
                    val range = thicknessRangeLabel(diameter)
                    val volume = (block.volumeFor(diameter, selectedLengthIndex) ?: 0.0) * count
                    val key = Triple(range, sort, dest)
                    val prev = rows[key] ?: (0 to 0.0)
                    rows[key] = (prev.first + count) to (prev.second + volume)
                }
            }
        }
        return rows.entries
            .map { (key, value) -> ThicknessSummaryRow(key.first, key.second, key.third, value.first, value.second) }
            .sortedWith(compareBy({ THICKNESS_ORDER.indexOf(it.rangeLabel) }, { it.sort }, { it.destination.ordinal }))
    }

    companion object {
        const val DEFAULT_SORT = "Осн."
        val THICKNESS_ORDER = listOf("До 13", "14–24", "26 и больше")

        /** Границы ступеней толщины подтверждены пользователем: до 13 / 14–24 / 26 и больше. */
        fun thicknessRangeLabel(diameter: Int): String = when {
            diameter <= 13 -> "До 13"
            diameter <= 24 -> "14–24"
            else -> "26 и больше"
        }
    }
}

class KubaturnikViewModel(application: Application) : AndroidViewModel(application) {
    private val store = KubaturnikBatchStore(application)

    private val _uiState = MutableStateFlow(KubaturnikUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val table = runCatching { KubaturnikTableLoader.load(application) }
        table.onFailure {
            _uiState.value = _uiState.value.copy(loading = false, error = it.message ?: "Не удалось загрузить таблицу ГОСТ 2708-75")
        }
        table.onSuccess { loaded ->
            val snapshot = store.loadDraft()
            _uiState.value = buildState(loaded, snapshot).copy(calculations = store.listCalculations())
        }
    }

    private fun buildState(table: KubaturnikTable, snapshot: KubaturnikSnapshot?): KubaturnikUiState {
        if (snapshot == null || snapshot.blockId == null || snapshot.lengthIndex < 0) {
            return KubaturnikUiState(loading = false, table = table)
        }
        val counts = mutableMapOf<String, MutableMap<KubaturnikDestination, MutableMap<Int, Int>>>()
        for (entry in snapshot.counts) {
            val bySort = counts.getOrPut(entry.sort) { mutableMapOf() }
            val byDest = bySort.getOrPut(KubaturnikDestination.fromWire(entry.destination)) { mutableMapOf() }
            byDest[entry.diameter] = entry.count
        }
        return KubaturnikUiState(
            loading = false,
            table = table,
            selectedBlockId = snapshot.blockId,
            selectedLengthIndex = snapshot.lengthIndex,
            selectedLengthValue = snapshot.lengthValue,
            stepCm = snapshot.stepCm,
            destination = KubaturnikDestination.fromWire(snapshot.destination),
            sorts = snapshot.sorts.ifEmpty { listOf(KubaturnikUiState.DEFAULT_SORT) },
            activeSort = snapshot.activeSort,
            counts = counts,
        )
    }

    /** Только для чтения — карточка сохранённого расчёта в истории, тем же способом, что и черновик. */
    fun uiStateFor(calculation: KubaturnikCalculation): KubaturnikUiState {
        val table = _uiState.value.table ?: return KubaturnikUiState(loading = false)
        return buildState(table, calculation.snapshot)
    }

    fun selectLength(blockId: String, lengthIndex: Int, lengthValue: Double) {
        _uiState.value = _uiState.value.copy(
            selectedBlockId = blockId,
            selectedLengthIndex = lengthIndex,
            selectedLengthValue = lengthValue,
            outOfRangeLengthMessage = null,
        )
        persist()
    }

    fun requestUnavailableLength() {
        _uiState.value = _uiState.value.copy(
            outOfRangeLengthMessage = "Для этой длины таблицы нет — заложены только блоки 3,0–3,9 / 4,0–4,9 / 5,0–5,9 / 6,0–6,9 м.",
        )
    }

    fun dismissOutOfRangeMessage() {
        _uiState.value = _uiState.value.copy(outOfRangeLengthMessage = null)
    }

    fun setStep(step: Int) {
        _uiState.value = _uiState.value.copy(stepCm = step)
        persist()
    }

    fun setDestination(destination: KubaturnikDestination) {
        _uiState.value = _uiState.value.copy(destination = destination)
        persist()
    }

    fun setActiveSort(sort: String) {
        _uiState.value = _uiState.value.copy(activeSort = sort)
        persist()
    }

    fun showAddSortDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddSortDialog = show)
    }

    fun addSort(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val sorts = if (trimmed in state.sorts) state.sorts else state.sorts + trimmed
        _uiState.value = state.copy(sorts = sorts, activeSort = trimmed, showAddSortDialog = false)
        persist()
    }

    /** Тап по кнопке диаметра — +1 бревно в текущий счётчик (сорт × назначение × диаметр). */
    fun tapDiameter(diameter: Int) {
        val state = _uiState.value
        val block = state.selectedBlock ?: return
        val volume = block.volumeFor(diameter, state.selectedLengthIndex) ?: return

        val counts = state.counts.toMutableMap()
        val bySort = counts[state.activeSort]?.toMutableMap() ?: mutableMapOf()
        val byDest = bySort[state.destination]?.toMutableMap() ?: mutableMapOf()
        byDest[diameter] = (byDest[diameter] ?: 0) + 1
        bySort[state.destination] = byDest
        counts[state.activeSort] = bySort

        _uiState.value = state.copy(counts = counts, lastTap = diameter to volume)
        persist()
    }

    /** Долгое нажатие / свайп влево — отменить последнее добавленное бревно этого диаметра. */
    fun undoDiameter(diameter: Int) {
        val state = _uiState.value
        val current = state.currentCombo[diameter] ?: return
        if (current <= 0) return

        val counts = state.counts.toMutableMap()
        val bySort = counts[state.activeSort]?.toMutableMap() ?: return
        val byDest = bySort[state.destination]?.toMutableMap() ?: return
        val newCount = current - 1
        if (newCount <= 0) byDest.remove(diameter) else byDest[diameter] = newCount
        bySort[state.destination] = byDest
        counts[state.activeSort] = bySort

        _uiState.value = state.copy(counts = counts)
        persist()
    }

    fun requestReset() {
        _uiState.value = _uiState.value.copy(showResetConfirm = true)
    }

    fun dismissReset() {
        _uiState.value = _uiState.value.copy(showResetConfirm = false)
    }

    /** «Новая партия»: текущий расчёт (если в нём есть хоть одно бревно) уходит в историю, а не стирается. */
    fun confirmReset() {
        val state = _uiState.value
        var calculations = state.calculations
        if (state.lengthChosen && state.totalLogCount > 0) {
            store.archiveDraft(currentSnapshot(state))
            calculations = store.listCalculations()
        }
        store.clearDraft()
        _uiState.value = KubaturnikUiState(loading = false, table = state.table, calculations = calculations)
    }

    fun deleteCalculation(id: Int) {
        store.deleteCalculation(id)
        _uiState.value = _uiState.value.copy(calculations = store.listCalculations())
    }

    private fun currentSnapshot(state: KubaturnikUiState): KubaturnikSnapshot {
        val entries = state.counts.flatMap { (sort, byDest) ->
            byDest.flatMap { (dest, byDiam) ->
                byDiam.map { (diameter, count) -> KubaturnikCountEntry(sort, dest.name, diameter, count) }
            }
        }
        return KubaturnikSnapshot(
            blockId = state.selectedBlockId,
            lengthIndex = state.selectedLengthIndex,
            lengthValue = state.selectedLengthValue,
            stepCm = state.stepCm,
            destination = state.destination.name,
            sorts = state.sorts,
            activeSort = state.activeSort,
            counts = entries,
        )
    }

    private fun persist() {
        val state = _uiState.value
        if (!state.lengthChosen) return
        store.saveDraft(currentSnapshot(state))
    }
}
