package com.lesovod.mobile.ui.map

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.CompletedWorkStore
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.CellTier
import com.lesovod.mobile.data.repository.DownloadState
import com.lesovod.mobile.data.repository.MapDataHub
import com.lesovod.mobile.data.session.SessionManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.floor

/** Что показывать на карте — простые галочки, без тематических раскрасок. */
data class MapLayers(
    val kvartaly: Boolean = true,
    val vydela: Boolean = true,
    val lesoseki: Boolean = true,
    val satellite: Boolean = true,
)

/** Где была карта — чтобы при возврате на вкладку не прыгать заново на всё лесничество. */
data class MapCamera(val latitude: Double, val longitude: Double, val zoom: Double)

/** Незаконченная метка: долгое нажатие на карту уже задало точку, дальше — текст/фото и отправка. */
data class GeoNoteDraft(
    val lat: Double,
    val lon: Double,
    val text: String = "",
    val photoUri: Uri? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

data class MapUiState(
    val layers: MapLayers = MapLayers(),
    val selection: MapSelection? = null,
    /** Ключи "квартал|выдел" делянок, где работа отмечена выполненной. */
    val completed: Set<String> = emptySet(),
    val lesnichestva: Map<String, Int> = emptyMap(),
    val selectedLesnichestvo: String? = null,
    val isLoadingLayers: Boolean = false,
    val kvartaly: List<MapShape> = emptyList(),
    val vydela: List<MapShape> = emptyList(),
    val lesoseki: List<MapShape> = emptyList(),
    /** Растёт, когда карте нужно заново вписаться в кварталы (первая загрузка / смена лесничества). */
    val fitToken: Int = 0,
    val usedFallbackRectangles: Boolean = false,
    val download: DownloadState = DownloadState(),
    val error: String? = null,
    /** Карточка (таксация / квартал) открыта — второй тап по уже выбранному объекту. */
    val cardOpen: Boolean = false,
    val selectedKvartalLabel: String? = null,
    /** Таксация выдела — только для выдела, на котором делянки нет. */
    val selectedCard: VydelCard? = null,
    /** Данные самой делянки (лесосеки из МДО). */
    val selectedDelyanka: DelyankaCard? = null,
    /** Пояснение вместо карточки (например, контур делянки есть, а данных по ней нет). */
    val cardNotice: String? = null,
    val isLoadingCard: Boolean = false,
    val cardError: String? = null,
    /** Метки рабочих на карте (текст/фото). */
    val geoNotes: List<GeoNoteMarker> = emptyList(),
    /** Форма новой метки — открыта после долгого нажатия на карту. */
    val noteDraft: GeoNoteDraft? = null,
    /** Метка, по которой тапнули — маленькая карточка с текстом. */
    val selectedGeoNote: GeoNoteMarker? = null,
)

/** Выделы показываем только с масштаба, где их можно разглядеть: на общем плане хватает кварталов. */
private const val MIN_VYDELA_ZOOM = 13.5
private const val FINE_TIER_ZOOM = 14.5
private const val MAX_CELLS_PER_VIEW = 16
private const val MAX_CELLS_IN_MEMORY = 40
private const val PARALLEL_CELL_LOADS = 2
private const val OFFLINE_MESSAGE = "Нет связи с сервером — часть выделов недоступна"

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val hub = MapDataHub.getInstance(application)
    private val repository = hub.repository
    private val completedStore = CompletedWorkStore(application)
    private val botRepository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState = _uiState.asStateFlow()

    /** Читается один раз при создании карты, поэтому не часть state. */
    var camera: MapCamera? = null
        private set

    private var viewportJob: Job? = null
    private var lastViewport: Pair<DoubleArray, Double>? = null
    private var visibleCellKeys: List<String> = emptyList()
    private var pendingFit = false
    private var realLesoseki: List<MapShape> = emptyList()
    private var appliedDefault: String? = null

    /** Разобранные ячейки в памяти — при возврате в уже виденное место ничего не читается с диска. */
    private val cells = object : LinkedHashMap<String, List<MapShape>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MapShape>>?) = size > MAX_CELLS_IN_MEMORY
    }
    private val inFlight = HashMap<String, Deferred<Unit>>()
    private val cellLoads = Semaphore(PARALLEL_CELL_LOADS)

    init {
        viewModelScope.launch { hub.download.collect { _uiState.value = _uiState.value.copy(download = it) } }
        loadLesnichestva()
        loadGeoNotes()
    }

    fun loadGeoNotes() {
        viewModelScope.launch {
            repository.getGeoNotes().onSuccess { _uiState.value = _uiState.value.copy(geoNotes = it) }
        }
    }

    /** Долгое нажатие на карту — открываем форму новой метки в этой точке. */
    fun startNoteDraft(lat: Double, lon: Double) {
        _uiState.value = _uiState.value.copy(noteDraft = GeoNoteDraft(lat = lat, lon = lon), selectedGeoNote = null)
    }

    fun updateNoteDraftText(text: String) {
        val draft = _uiState.value.noteDraft ?: return
        _uiState.value = _uiState.value.copy(noteDraft = draft.copy(text = text, error = null))
    }

    fun updateNoteDraftPhoto(uri: Uri?) {
        val draft = _uiState.value.noteDraft ?: return
        _uiState.value = _uiState.value.copy(noteDraft = draft.copy(photoUri = uri))
    }

    fun dismissNoteDraft() {
        _uiState.value = _uiState.value.copy(noteDraft = null)
    }

    fun submitNoteDraft() {
        val draft = _uiState.value.noteDraft ?: return
        if (draft.text.isBlank() && draft.photoUri == null) {
            _uiState.value = _uiState.value.copy(noteDraft = draft.copy(error = "Добавьте текст или фото"))
            return
        }
        _uiState.value = _uiState.value.copy(noteDraft = draft.copy(isSubmitting = true, error = null))
        viewModelScope.launch {
            var photoPath: String? = null
            if (draft.photoUri != null) {
                val uploadResult = botRepository.uploadPhoto(getApplication<Application>(), draft.photoUri)
                uploadResult.onFailure { err ->
                    _uiState.value.noteDraft?.let {
                        _uiState.value = _uiState.value.copy(
                            noteDraft = it.copy(isSubmitting = false, error = err.message ?: "Не удалось загрузить фото"),
                        )
                    }
                    return@launch
                }
                photoPath = uploadResult.getOrNull()
            }
            val result = botRepository.submitGeoNote(draft.lat, draft.lon, draft.text.trim().takeIf { it.isNotBlank() }, photoPath)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(noteDraft = null)
                    loadGeoNotes()
                },
                onFailure = { err ->
                    _uiState.value.noteDraft?.let {
                        _uiState.value = _uiState.value.copy(
                            noteDraft = it.copy(isSubmitting = false, error = err.message ?: "Не удалось сохранить метку"),
                        )
                    }
                },
            )
        }
    }

    fun onGeoNoteTap(note: GeoNoteMarker) {
        _uiState.value = _uiState.value.copy(selectedGeoNote = note, noteDraft = null)
    }

    fun dismissGeoNotePopup() {
        _uiState.value = _uiState.value.copy(selectedGeoNote = null)
    }

    fun loadLesnichestva() {
        viewModelScope.launch {
            val result = repository.listLesnichestva(onStale = { map -> viewModelScope.launch { onLesnichestva(map) } })
            result.fold(
                onSuccess = { onLesnichestva(it) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) },
            )
        }
    }

    private fun onLesnichestva(map: Map<String, Int>) {
        val firstTime = _uiState.value.selectedLesnichestvo == null
        if (!firstTime && _uiState.value.lesnichestva == map) return

        // лесничество по умолчанию задаётся в профиле; нет такого — берём первое из списка
        val default = hub.prefs.defaultLesnichestvo?.takeIf { it in map }
        val preselected = _uiState.value.selectedLesnichestvo ?: default ?: map.keys.firstOrNull()
        appliedDefault = hub.prefs.defaultLesnichestvo
        _uiState.value = _uiState.value.copy(lesnichestva = map, selectedLesnichestvo = preselected, error = null)
        refreshCompleted()
        if (firstTime) preselected?.let { loadLayers(it) }
    }

    /** Зовём при возврате на экран: если в профиле сменили лесничество по умолчанию — переключаемся на него. */
    fun applyDefaultIfChanged() {
        val default = hub.prefs.defaultLesnichestvo
        if (default == appliedDefault) return
        appliedDefault = default
        if (default != null && default in _uiState.value.lesnichestva) selectLesnichestvo(default)
    }

    fun selectLesnichestvo(name: String) {
        if (name == _uiState.value.selectedLesnichestvo) return
        camera = null
        _uiState.value = _uiState.value.copy(selectedLesnichestvo = name, vydela = emptyList(), selection = null, cardOpen = false)
        refreshCompleted()
        loadLayers(name)
    }

    /** "Скачать карту": лесничество целиком сохраняется на устройство (и заодно обновляется). */
    fun downloadCurrent() {
        val name = _uiState.value.selectedLesnichestvo ?: return
        val num = _uiState.value.lesnichestva[name] ?: return
        hub.downloadLesnichestvo(name, num)
    }

    /**
     * Кварталы и лесосеки — небольшие слои, читаются с диска целиком. Выделы весят до ~56 МБ на
     * лесничество, поэтому их тянем только кусками под видимую область (см. [onViewportChanged]).
     */
    private fun loadLayers(name: String) {
        val num = _uiState.value.lesnichestva[name] ?: return
        pendingFit = true
        _uiState.value = _uiState.value.copy(isLoadingLayers = true, error = null)
        viewModelScope.launch {
            val kvartalyJob = async {
                repository.getKvartaly(num, onStale = { stale -> viewModelScope.launch { deliverKvartaly(stale) } })
            }
            val lesosekiJob = async { repository.getLesoseki(num) }
            val kvartalyResult = kvartalyJob.await()
            realLesoseki = lesosekiJob.await().getOrDefault(emptyList())

            kvartalyResult.getOrNull()?.let { deliverKvartaly(it) }
            _uiState.value = _uiState.value.copy(isLoadingLayers = false, error = kvartalyResult.exceptionOrNull()?.message)
            publishVydela()
            lastViewport?.let { (box, zoom) -> loadViewport(box, zoom) }
        }
    }

    private fun deliverKvartaly(shapes: List<MapShape>) {
        val fit = pendingFit && shapes.isNotEmpty()
        if (fit) pendingFit = false
        _uiState.value = _uiState.value.copy(
            kvartaly = shapes,
            fitToken = _uiState.value.fitToken + if (fit) 1 else 0,
        )
    }

    /** Зовёт ForestMapView, когда пользователь подвинул/масштабировал карту (с задержкой на себе). */
    fun onViewportChanged(bbox: String, zoom: Double, latitude: Double, longitude: Double) {
        camera = MapCamera(latitude, longitude, zoom)
        val parts = bbox.split(',').mapNotNull { it.toDoubleOrNull() }
        if (parts.size != 4) return
        val box = parts.toDoubleArray() // west, south, east, north
        lastViewport = box to zoom
        loadViewport(box, zoom)
    }

    private fun loadViewport(box: DoubleArray, zoom: Double) {
        val name = _uiState.value.selectedLesnichestvo ?: return
        val num = _uiState.value.lesnichestva[name] ?: return

        viewportJob?.cancel()
        val tier = if (zoom < FINE_TIER_ZOOM) CellTier.COARSE else CellTier.FINE
        val ixRange = floor(box[0] / tier.degrees).toInt()..floor(box[2] / tier.degrees).toInt()
        val iyRange = floor(box[1] / tier.degrees).toInt()..floor(box[3] / tier.degrees).toInt()

        if (zoom < MIN_VYDELA_ZOOM || (ixRange.last - ixRange.first + 1) * (iyRange.last - iyRange.first + 1) > MAX_CELLS_PER_VIEW) {
            visibleCellKeys = emptyList()
            publishVydela()
            return
        }

        val wanted = buildList {
            for (ix in ixRange) for (iy in iyRange) add(Triple(ix, iy, repository.cellKey(num, tier, ix, iy)))
        }
        visibleCellKeys = wanted.map { it.third }
        publishVydela()

        viewportJob = viewModelScope.launch {
            wanted.filter { (_, _, key) -> synchronized(cells) { key !in cells } }
                .map { (ix, iy, key) -> loadCell(num, tier, ix, iy, key) }
                .forEach { it.await() }
        }
    }

    /** Одна ячейка = один запрос; если она уже качается, второй раз не запрашиваем (и сдвиг карты её не отменяет). */
    private fun loadCell(num: Int, tier: CellTier, ix: Int, iy: Int, key: String): Deferred<Unit> =
        inFlight.getOrPut(key) {
            viewModelScope.async {
                cellLoads.withPermit {
                    val result = repository.getVydelaCell(num, tier, ix, iy, onStale = { stale ->
                        viewModelScope.launch { putCell(key, stale) }
                    })
                    result.fold(
                        onSuccess = {
                            putCell(key, it)
                            if (_uiState.value.error == OFFLINE_MESSAGE) _uiState.value = _uiState.value.copy(error = null)
                        },
                        onFailure = { if (_uiState.value.error == null) _uiState.value = _uiState.value.copy(error = OFFLINE_MESSAGE) },
                    )
                }
            }.also { deferred -> deferred.invokeOnCompletion { inFlight.remove(key) } }
        }

    private fun putCell(key: String, shapes: List<MapShape>) {
        synchronized(cells) { cells[key] = shapes }
        publishVydela()
    }

    /** Склеиваем ячейки, видимые сейчас; выдел на границе двух ячеек приходит дважды — оставляем один. */
    private fun publishVydela() {
        val seen = HashSet<String>()
        val merged = ArrayList<MapShape>()
        synchronized(cells) {
            for (key in visibleCellKeys) cells[key]?.forEach { if (seen.add(it.key)) merged.add(it) }
        }
        val useFallback = realLesoseki.isEmpty()
        _uiState.value = _uiState.value.copy(
            vydela = merged,
            lesoseki = if (useFallback) fallbackLesosekiFrom(merged) else realLesoseki,
            usedFallbackRectangles = useFallback,
        )
    }

    /**
     * Первый тап по выделу/делянке — только выделяем (заливка на карте: человек видит, на каком он
     * участке). Второй тап по тому же — открываем таксацию. Тап по другому — выделение переезжает.
     */
    fun onShapeTap(shape: MapShape) {
        val tapped = MapSelection(shape.kvartal, shape.vydel, shape.kind)
        if (_uiState.value.selection == tapped) {
            openSelected()
        } else {
            _uiState.value = _uiState.value.copy(
                selection = tapped,
                cardOpen = false,
                isLoadingCard = false,
                selectedCard = null, selectedDelyanka = null, cardNotice = null,
                cardError = null,
                selectedKvartalLabel = null,
            )
        }
    }

    /**
     * Открыть карточку выбранного объекта (второй тап или нажатие на подсказку). Если на этом
     * кв./выд. заведена делянка — показываем данные самой делянки (GET /api/delyanki/{id}), а не
     * общую таксацию выдела: они из документа МДО и могут отличаться. Таксация выдела — только
     * когда делянки на нём нет.
     */
    fun openSelected() {
        val selection = _uiState.value.selection ?: return
        if (_uiState.value.cardOpen) return
        val vydel = selection.vydel
        if (vydel == null) {
            _uiState.value = _uiState.value.copy(cardOpen = true, selectedKvartalLabel = "Квартал ${selection.kvartal}")
            return
        }
        _uiState.value = _uiState.value.copy(
            cardOpen = true,
            isLoadingCard = true,
            cardError = null,
            selectedCard = null,
            selectedDelyanka = null,
            cardNotice = null,
        )
        viewModelScope.launch {
            val lesnichestvo = _uiState.value.selectedLesnichestvo
            val refs = repository.getDelyankiForMap()
            val ref = refs.getOrNull()?.let { findDelyanka(it, selection.kvartal, vydel, lesnichestvo) }

            val next: MapUiState = when {
                refs.isFailure -> _uiState.value.copy(isLoadingCard = false, cardError = refs.exceptionOrNull()?.message)
                ref != null -> repository.getDelyanka(ref.delyankaId).fold(
                    onSuccess = { json ->
                        val card = json.toDelyankaCard(ref.delyankaId, selection.kvartal, vydel)
                        if (card != null) _uiState.value.copy(isLoadingCard = false, selectedDelyanka = card)
                        else _uiState.value.copy(isLoadingCard = false, cardNotice = "В делянке «${ref.nazvanie.orEmpty()}» нет данных по этому выделу")
                    },
                    onFailure = { _uiState.value.copy(isLoadingCard = false, cardError = it.message) },
                )
                selection.kind == ShapeKind.LESOSEKA ->
                    _uiState.value.copy(isLoadingCard = false, cardNotice = "Контур делянки есть на карте, но в системе она не заведена — данных нет")
                else -> repository.getVydelCard(selection.kvartal, vydel, lesnichestvo).fold(
                    onSuccess = { _uiState.value.copy(isLoadingCard = false, selectedCard = it.toVydelCard()) },
                    onFailure = { _uiState.value.copy(isLoadingCard = false, cardError = it.message) },
                )
            }
            if (_uiState.value.selection != selection) return@launch // пока грузили, выбрали другое
            _uiState.value = next
        }
    }

    /** Делянка на этом кв./выд.; если их несколько (архивная и новая) — берём последнюю заведённую. */
    private fun findDelyanka(refs: List<DelyankaMapRefDto>, kvartal: String, vydel: String, lesnichestvo: String?): DelyankaMapRefDto? =
        refs.filter {
            it.kvartal?.trim() == kvartal.trim() && it.vydel?.trim() == vydel.trim() && lesnichestvoMatches(it.lesnichestvo, lesnichestvo)
        }.maxByOrNull { it.delyankaId }

    private fun lesnichestvoMatches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        return a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
    }

    /** Зовём при возврате на экран: отметки о выполнении могли появиться на вкладке задач. */
    fun refreshCompleted() {
        _uiState.value = _uiState.value.copy(completed = completedStore.keysFor(_uiState.value.selectedLesnichestvo))
    }

    fun setLayers(layers: MapLayers) {
        _uiState.value = _uiState.value.copy(layers = layers)
    }

    /** Закрыть карточку, но оставить участок выделенным. */
    fun closeCard() {
        _uiState.value = _uiState.value.copy(cardOpen = false, isLoadingCard = false, selectedCard = null, selectedDelyanka = null, cardNotice = null, cardError = null, selectedKvartalLabel = null)
    }

    /** Снять выделение совсем. */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selection = null,
            cardOpen = false,
            isLoadingCard = false,
            selectedCard = null, selectedDelyanka = null, cardNotice = null,
            cardError = null,
            selectedKvartalLabel = null,
        )
    }
}
