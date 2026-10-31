package com.lesovod.kubaturnik.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.kubaturnik.data.KubaturnikBatchStore
import com.lesovod.kubaturnik.data.KubaturnikBlock
import com.lesovod.kubaturnik.data.KubaturnikCalculation
import com.lesovod.kubaturnik.data.KubaturnikCountEntry
import com.lesovod.kubaturnik.data.KubaturnikSettingsStore
import com.lesovod.kubaturnik.data.KubaturnikSnapshot
import com.lesovod.kubaturnik.data.KubaturnikTable
import com.lesovod.kubaturnik.data.KubaturnikTableLoader
import com.lesovod.kubaturnik.data.TilesSide
import com.lesovod.kubaturnik.data.diameterButtons
import com.lesovod.kubaturnik.data.volumeFor
import com.lesovod.kubaturnik.data.KubaturnikVoice
import com.lesovod.kubaturnik.data.VoskModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class KubaturnikDestination(val label: String) {
    MACHINE("Машина"), TRAILER("Прицеп");

    companion object {
        fun fromWire(value: String): KubaturnikDestination = entries.firstOrNull { it.name == value } ?: MACHINE
    }
}

/** Диаметр -> количество отмеченных брёвен (в рамках одного сорт+назначение). */
typealias DiameterCounts = Map<Int, Int>

data class KubaturnikRow(val diameter: Int, val count: Int, val volumePerLog: Double, val totalVolume: Double)

enum class ThicknessRowKind { DIAMETER, GROUP, TOTAL }

/** Строка сводки: отдельный диаметр, итог группы ступеней («14–24», «26 и б.») или общий итог. */
data class ThicknessSummaryRow(
    val label: String,
    val count: Int,
    val volume: Double,
    val kind: ThicknessRowKind,
)

/** Одна таблица «Диаметр / кол-во / Объём»: вся партия или отдельно сорт · машина/прицеп. */
data class ThicknessSummaryTable(val title: String, val rows: List<ThicknessSummaryRow>)

data class KubaturnikUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val table: KubaturnikTable? = null,
    val selectedBlockId: String? = null,
    val selectedLengthIndex: Int = -1,
    val selectedLengthValue: Double = 0.0,
    val stepCm: Int = 2,
    val destination: KubaturnikDestination = KubaturnikDestination.MACHINE,
    /** Сорта фиксированы: всегда А, Б, С, Д — свои не добавляются. */
    val sorts: List<String> = FIXED_SORTS,
    val activeSort: String = DEFAULT_SORT,
    // sort -> destination -> diameter -> count
    val counts: Map<String, Map<KubaturnikDestination, DiameterCounts>> = emptyMap(),
    val lastTap: Pair<Int, Double>? = null,
    val showResetConfirm: Boolean = false,
    val outOfRangeLengthMessage: String? = null,
    /** Ранее сохранённые расчёты («Новая партия» архивирует сюда текущий, а не стирает его). */
    val calculations: List<KubaturnikCalculation> = emptyList(),
    /** Сторона плиток на вкладке «Счёт» — настройка экрана, не часть партии. */
    val tilesSide: TilesSide = TilesSide.LEFT,
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

    /**
     * Сводка по ступеням в виде таблиц, как в бумажной ведомости: каждая ступень отдельной строкой,
     * после ступеней 14–24 — их итог «14–24», после 26 и толще — итог «26 и б.», в конце «Итого».
     * Первая таблица — вся партия; если сортов/назначений несколько, ниже — по каждому отдельно.
     */
    fun thicknessSummary(): List<ThicknessSummaryTable> {
        val block = selectedBlock ?: return emptyList()
        val combos = counts.flatMap { (sort, byDest) ->
            byDest.filterValues { byDiam -> byDiam.values.any { it > 0 } }
                .map { (dest, byDiam) -> Triple(sort, dest, byDiam) }
        }
        if (combos.isEmpty()) return emptyList()

        val merged = mutableMapOf<Int, Int>()
        combos.forEach { (_, _, byDiam) -> byDiam.forEach { (d, c) -> merged[d] = (merged[d] ?: 0) + c } }

        val tables = mutableListOf(ThicknessSummaryTable("Вся партия", thicknessRows(block, merged)))
        if (combos.size > 1) {
            combos
                .sortedWith(compareBy({ sorts.indexOf(it.first) }, { it.second.ordinal }))
                .forEach { (sort, dest, byDiam) ->
                    tables += ThicknessSummaryTable("${sortLabel(sort)} · ${dest.label}", thicknessRows(block, byDiam))
                }
        }
        return tables
    }

    private fun thicknessRows(block: KubaturnikBlock, byDiameter: DiameterCounts): List<ThicknessSummaryRow> {
        val rows = mutableListOf<ThicknessSummaryRow>()
        var totalCount = 0
        var totalVolume = 0.0
        val entries = byDiameter.filterValues { it > 0 }.toSortedMap()
        for (group in ThicknessGroup.entries) {
            val inGroup = entries.filterKeys { thicknessGroup(it) == group }
            if (inGroup.isEmpty()) continue
            var groupCount = 0
            var groupVolume = 0.0
            for ((diameter, count) in inGroup) {
                val volume = (block.volumeFor(diameter, selectedLengthIndex) ?: 0.0) * count
                rows += ThicknessSummaryRow(diameter.toString(), count, volume, ThicknessRowKind.DIAMETER)
                groupCount += count
                groupVolume += volume
            }
            group.totalLabel?.let { rows += ThicknessSummaryRow(it, groupCount, groupVolume, ThicknessRowKind.GROUP) }
            totalCount += groupCount
            totalVolume += groupVolume
        }
        rows += ThicknessSummaryRow("Итого", totalCount, totalVolume, ThicknessRowKind.TOTAL)
        return rows
    }

    companion object {
        /** Четыре сорта — заранее, всегда все четыре. Ключ в счётчиках — сама буква. */
        val FIXED_SORTS = listOf("А", "Б", "С", "Д")
        const val DEFAULT_SORT = "А"
    }
}

/** «Сорт А» — подпись для экрана, сводки и голосового ответа. */
fun sortLabel(sort: String): String = "Сорт $sort"

/**
 * Границы ступеней толщины подтверждены пользователем: до 13 / 14–24 / 26 и больше.
 * Тонкие (до 13) идут в сводке просто строками, без своей итоговой строки — как в бумажном образце.
 */
enum class ThicknessGroup(val totalLabel: String?) { THIN(null), MIDDLE("14–24"), THICK("26 и б.") }

fun thicknessGroup(diameter: Int): ThicknessGroup = when {
    diameter <= 13 -> ThicknessGroup.THIN
    diameter <= 24 -> ThicknessGroup.MIDDLE
    else -> ThicknessGroup.THICK
}

/** Строка журнала голосового ввода: что услышал телефон и что он с этим сделал. */
data class VoiceLogEntry(val heard: String, val result: String, val ok: Boolean)

data class VoiceUiState(
    val listening: Boolean = false,
    /** Модель грузится в память (секунда-две после нажатия на микрофон). */
    val starting: Boolean = false,
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    /** 0..1, или -1 — размер неизвестен. */
    val downloadProgress: Float = 0f,
    val showModelDialog: Boolean = false,
    val partial: String = "",
    /** Последние фразы, свежая — первой. */
    val log: List<VoiceLogEntry> = emptyList(),
    val speakBack: Boolean = true,
    val error: String? = null,
)

/** Одно изменение счётчика голосом — чтобы «отмена» могла его откатить. */
private data class VoiceDelta(val sort: String, val destination: KubaturnikDestination, val diameter: Int, val delta: Int)

class KubaturnikViewModel(application: Application) : AndroidViewModel(application) {
    private val store = KubaturnikBatchStore(application)
    private val settingsStore = KubaturnikSettingsStore(application)
    private val modelManager = VoskModelManager(application)
    private var voice: KubaturnikVoice? = null
    /** Голосовые фразы, изменившие счёт, — последняя в конце. «Отмена» откатывает фразу целиком. */
    private val voiceUndo = ArrayDeque<List<VoiceDelta>>()

    private val _uiState = MutableStateFlow(KubaturnikUiState())
    val uiState = _uiState.asStateFlow()

    private val _voiceState = MutableStateFlow(
        VoiceUiState(modelReady = modelManager.isReady, speakBack = settingsStore.voiceSpeakBack),
    )
    val voiceState = _voiceState.asStateFlow()

    init {
        val tilesSide = settingsStore.tilesSide
        val table = runCatching { KubaturnikTableLoader.load(application) }
        table.onFailure {
            _uiState.value = _uiState.value.copy(
                loading = false,
                error = it.message ?: "Не удалось загрузить таблицу ГОСТ 2708-75",
                tilesSide = tilesSide,
            )
        }
        table.onSuccess { loaded ->
            val snapshot = store.loadDraft()
            _uiState.value = buildState(loaded, snapshot)
                .copy(calculations = store.listCalculations(), tilesSide = tilesSide)
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
            sorts = KubaturnikUiState.FIXED_SORTS,
            activeSort = snapshot.activeSort.takeIf { it in KubaturnikUiState.FIXED_SORTS } ?: KubaturnikUiState.DEFAULT_SORT,
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

    /** Переключатель «плитки слева/справа» — только вид экрана, счётчики партии не трогает. */
    fun toggleTilesSide() {
        val next = if (_uiState.value.tilesSide == TilesSide.LEFT) TilesSide.RIGHT else TilesSide.LEFT
        settingsStore.tilesSide = next
        _uiState.value = _uiState.value.copy(tilesSide = next)
    }

    /** Тема: тёмная по умолчанию, переключается значком в заголовке и запоминается. */
    private val _darkTheme = MutableStateFlow(settingsStore.darkTheme)
    val darkTheme = _darkTheme.asStateFlow()

    fun toggleTheme() {
        val next = !_darkTheme.value
        settingsStore.darkTheme = next
        _darkTheme.value = next
    }

    /** Тап по кнопке диаметра — +1 бревно в текущий счётчик (сорт × назначение × диаметр). */
    fun tapDiameter(diameter: Int) {
        val state = _uiState.value
        val block = state.selectedBlock ?: return
        val volume = block.volumeFor(diameter, state.selectedLengthIndex) ?: return
        _uiState.value = state.copy(
            counts = state.countsWith(state.activeSort, state.destination, diameter, +1),
            lastTap = diameter to volume,
        )
        persist()
    }

    /** Долгое нажатие / свайп влево — отменить последнее добавленное бревно этого диаметра. */
    fun undoDiameter(diameter: Int) {
        val state = _uiState.value
        val current = state.currentCombo[diameter] ?: return
        if (current <= 0) return
        val newCount = current - 1
        // Счёт «последней» плитки упал до 0 — сбрасываем «последний тап» (панель покажет «—»).
        val lastTap = if (newCount <= 0 && state.lastTap?.first == diameter) null else state.lastTap
        _uiState.value = state.copy(
            counts = state.countsWith(state.activeSort, state.destination, diameter, -1),
            lastTap = lastTap,
        )
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
        voiceUndo.clear()
        _uiState.value = KubaturnikUiState(
            loading = false,
            table = state.table,
            calculations = calculations,
            tilesSide = state.tilesSide,
        )
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

    // ---- Голосовой ввод ----

    fun setVoiceSpeakBack(enabled: Boolean) {
        settingsStore.voiceSpeakBack = enabled
        _voiceState.value = _voiceState.value.copy(speakBack = enabled)
    }

    fun showVoiceModelDialog(show: Boolean) {
        _voiceState.value = _voiceState.value.copy(showModelDialog = show, error = null)
    }

    fun dismissVoiceError() {
        _voiceState.value = _voiceState.value.copy(error = null)
    }

    fun downloadVoiceModel() = loadVoiceModel { onProgress -> modelManager.download(onProgress) }

    fun importVoiceModel(uri: Uri) = loadVoiceModel { onProgress -> modelManager.importFromUri(uri, onProgress) }

    private fun loadVoiceModel(load: suspend ((Float) -> Unit) -> Unit) {
        if (_voiceState.value.downloading) return
        _voiceState.value = _voiceState.value.copy(downloading = true, downloadProgress = 0f, error = null)
        viewModelScope.launch {
            val result = runCatching {
                load { progress -> _voiceState.value = _voiceState.value.copy(downloadProgress = progress) }
            }
            _voiceState.value = _voiceState.value.copy(
                downloading = false,
                modelReady = modelManager.isReady,
                showModelDialog = !modelManager.isReady,
                error = result.exceptionOrNull()?.let { "Модель не загрузилась: ${it.message ?: it.javaClass.simpleName}" },
            )
            if (modelManager.isReady) startVoice()
        }
    }

    /** Включить микрофон. Без модели — показываем окно загрузки модели. */
    fun startVoice() {
        val path = modelManager.modelPath
        if (path == null) {
            showVoiceModelDialog(true)
            return
        }
        val engine = voice ?: KubaturnikVoice(getApplication()).also { voice = it }
        _voiceState.value = _voiceState.value.copy(starting = true, listening = false, partial = "", error = null)
        engine.start(path, voiceVocabulary(), object : KubaturnikVoice.Listener {
            override fun onReady() {
                _voiceState.value = _voiceState.value.copy(starting = false, listening = true)
            }

            override fun onPartial(text: String) {
                _voiceState.value = _voiceState.value.copy(partial = text)
            }

            override fun onPhrase(text: String) = handlePhrase(text)

            override fun onError(message: String) {
                _voiceState.value = _voiceState.value.copy(starting = false, listening = false, partial = "", error = message)
            }
        })
    }

    fun stopVoice() {
        voice?.stop()
        _voiceState.value = _voiceState.value.copy(starting = false, listening = false, partial = "")
    }

    /** Словарь распознавателя: числа, команды и названия букв сортов («сорт бэ»). */
    private fun voiceVocabulary(): Set<String> = VoiceCommandParser.vocabulary

    /** Разбирает фразу и применяет команды по порядку; отвечает голосом одной фразой на всё сразу. */
    fun handlePhrase(text: String) {
        val parsed = VoiceCommandParser.parse(text)
        val replies = mutableListOf<String>()
        val applied = mutableListOf<VoiceDelta>()
        var ok = parsed.commands.isNotEmpty()

        for (command in parsed.commands) {
            when (command) {
                is VoiceCommand.Count -> {
                    val (reply, done) = applyVoiceCount(command, applied)
                    replies += reply
                    if (!done) ok = false
                }
                is VoiceCommand.SetDestination -> {
                    setDestination(command.destination)
                    replies += command.destination.label
                }
                is VoiceCommand.SetSort -> {
                    val sort = resolveSort(command)
                    if (sort != null) {
                        setActiveSort(sort)
                        replies += sortLabel(sort)
                    } else {
                        replies += "Нет такого сорта"
                        ok = false
                    }
                }
                VoiceCommand.Undo -> replies += undoLastVoice()
                VoiceCommand.Total -> replies += totalPhrase()
                VoiceCommand.Stop -> {
                    stopVoice()
                    replies += "Выключаю"
                }
            }
        }
        if (applied.isNotEmpty()) {
            voiceUndo.addLast(applied)
            while (voiceUndo.size > MAX_VOICE_UNDO) voiceUndo.removeFirst()
            persist()
        }

        val result = replies.joinToString(", ").ifEmpty { "не понял" }
        val log = (listOf(VoiceLogEntry(text, result, ok)) + _voiceState.value.log).take(MAX_VOICE_LOG)
        _voiceState.value = _voiceState.value.copy(partial = "", log = log)
        if (_voiceState.value.speakBack && replies.isNotEmpty()) voice?.speak(replies.joinToString(". "))
    }

    /** Возвращает (ответ, успешно ли). Диаметр подгоняется к плиткам: нечётный при шаге 2 — вверх (25 → 26). */
    private fun applyVoiceCount(command: VoiceCommand.Count, applied: MutableList<VoiceDelta>): Pair<String, Boolean> {
        val state = _uiState.value
        val block = state.selectedBlock ?: return "Сначала выбери длину" to false
        val buttons = state.diameterButtonValues
        if (buttons.isEmpty() || command.diameter < buttons.first() || command.diameter > buttons.last()) {
            return "${command.diameter} нет в таблице" to false
        }
        val diameter = buttons.first { it >= command.diameter }
        val volume = block.volumeFor(diameter, state.selectedLengthIndex) ?: return "$diameter нет в таблице" to false

        if (command.count > 0) {
            _uiState.value = state.copy(
                counts = state.countsWith(state.activeSort, state.destination, diameter, command.count),
                lastTap = diameter to volume,
            )
            applied += VoiceDelta(state.activeSort, state.destination, diameter, command.count)
            return (if (command.count == 1) "$diameter" else "$diameter, ${piecesPhrase(command.count)}") to true
        }

        val current = state.currentCombo[diameter] ?: 0
        if (current <= 0) return "$diameter и так нет" to false
        val removed = minOf(current, -command.count)
        val lastTap = if (current == removed && state.lastTap?.first == diameter) null else state.lastTap
        _uiState.value = state.copy(
            counts = state.countsWith(state.activeSort, state.destination, diameter, -removed),
            lastTap = lastTap,
        )
        applied += VoiceDelta(state.activeSort, state.destination, diameter, -removed)
        return (if (removed == 1) "Минус $diameter" else "Минус $diameter, ${piecesPhrase(removed)}") to true
    }

    /** «сорт два» / «сорт второй» / «сорт бэ» — парсер уже превратил букву в номер 1..4. */
    private fun resolveSort(command: VoiceCommand.SetSort): String? {
        val sorts = KubaturnikUiState.FIXED_SORTS
        command.number?.let { return sorts.getOrNull(it - 1) }
        val name = command.name ?: return null
        return sorts.firstOrNull { it.equals(name, ignoreCase = true) }
    }

    private fun undoLastVoice(): String {
        val deltas = voiceUndo.removeLastOrNull() ?: return "Нечего отменять"
        var state = _uiState.value
        for (d in deltas.asReversed()) {
            state = state.copy(counts = state.countsWith(d.sort, d.destination, d.diameter, -d.delta))
        }
        _uiState.value = state.copy(lastTap = null)
        persist()
        val what = deltas.joinToString(", ") { d ->
            val n = kotlin.math.abs(d.delta)
            if (n == 1) "${d.diameter}" else "${d.diameter}, ${piecesPhrase(n)}"
        }
        return "Отменил $what"
    }

    /** «Итого»: текущая связка сорт·машина/прицеп и вся партия. */
    private fun totalPhrase(): String {
        val state = _uiState.value
        val rows = state.currentComboRows
        val comboCount = rows.sumOf { it.count }
        val comboVolume = rows.sumOf { it.totalVolume }
        return "${state.destination.label}, ${sortLabel(state.activeSort).lowercase(Locale("ru"))}: ${piecesPhrase(comboCount)}, ${cubes(comboVolume)}. " +
            "Вся партия: ${piecesPhrase(state.totalLogCount)}, ${cubes(state.totalVolume)}"
    }

    private fun cubes(volume: Double): String = String.format(Locale("ru"), "%.2f куба", volume)

    override fun onCleared() {
        voice?.release()
        voice = null
        super.onCleared()
    }

    private fun persist() {
        val state = _uiState.value
        if (!state.lengthChosen) return
        store.saveDraft(currentSnapshot(state))
    }
}

/** Новая карта счётчиков: к (сорт, назначение, диаметр) прибавлено [delta]; ноль и меньше — запись убирается. */
private fun KubaturnikUiState.countsWith(
    sort: String,
    destination: KubaturnikDestination,
    diameter: Int,
    delta: Int,
): Map<String, Map<KubaturnikDestination, DiameterCounts>> {
    val newCounts = counts.toMutableMap()
    val bySort = newCounts[sort]?.toMutableMap() ?: mutableMapOf()
    val byDest = bySort[destination]?.toMutableMap() ?: mutableMapOf()
    val value = (byDest[diameter] ?: 0) + delta
    if (value <= 0) byDest.remove(diameter) else byDest[diameter] = value
    bySort[destination] = byDest
    newCounts[sort] = bySort
    return newCounts
}

private const val MAX_VOICE_UNDO = 30
private const val MAX_VOICE_LOG = 5
