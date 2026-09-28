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
import com.lesovod.mobile.data.location.CurrentPlace
import com.lesovod.mobile.data.location.Place
import com.lesovod.mobile.data.network.dto.SkladDto
import com.lesovod.mobile.data.network.dto.TrackCreateRequest
import com.lesovod.mobile.data.network.dto.VydelHistoryDto
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.repository.MapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.floor

/** Что показывать на карте — простые галочки. */
data class MapLayers(
    val kvartaly: Boolean = true,
    val vydela: Boolean = true,
    val lesoseki: Boolean = true,
    val satellite: Boolean = true,
    val lesokultury: Boolean = true,
    val geoNotes: Boolean = true,
    val sklady: Boolean = true,
    val tasks: Boolean = true,
    /** Своя подложка из файла .mbtiles (например, ортофото, выгруженное из QGIS) — работает без интернета. */
    val offlineBase: Boolean = true,
)

/** Где была карта — чтобы при возврате на вкладку не прыгать заново на всё лесничество. */
data class MapCamera(val latitude: Double, val longitude: Double, val zoom: Double)

/** Незаконченная метка: долгое нажатие на карту уже задало точку, дальше — текст/фото и отправка. */
data class GeoNoteDraft(
    val lat: Double,
    val lon: Double,
    val text: String = "",
    val photoUri: Uri? = null,
    val category: GeoNoteCategory = GeoNoteCategory.ZAMETKA,
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
    val geoNotesError: String? = null,

    // --- раскраска ---
    val colorMode: ColorMode = ColorMode.WORKS,
    val workColors: Map<String, Int> = emptyMap(),
    val delyankaStatusColors: Map<String, Int> = emptyMap(),
    val lesokulturyKeys: Set<String> = emptySet(),

    // --- мои задачи и склады ---
    val tasks: List<WorkPlanItemDto> = emptyList(),
    val tasksOpen: Boolean = false,
    val sklady: List<SkladDto> = emptyList(),
    val selectedSklad: SkladDto? = null,

    // --- где я, "веди до делянки" ---
    val myLocation: LatLon? = null,
    val myAccuracy: Float? = null,
    val here: Place? = null,
    val navTarget: NavTarget? = null,

    // --- поиск ---
    val search: SearchState = SearchState(),
    /** Растёт, когда карте нужно перелететь в точку (поиск, задача, "где я"). */
    val focus: MapFocus? = null,

    // --- инструменты ---
    val tool: MapTool = MapTool.NONE,
    val rulerPoints: List<LatLon> = emptyList(),
    val walk: WalkState = WalkState(),

    // --- история выдела в карточке ---
    val history: VydelHistoryDto? = null,
    val historyLoading: Boolean = false,

    /** Своя офлайн-подложка (.mbtiles). */
    val offlineBasePath: String? = null,
    val offlineBaseName: String? = null,

    /** Короткое сообщение внизу ("Метка удалена", "Контур сохранён: 2,35 га"). */
    val message: String? = null,
)

/** Куда ведём: выбранный выдел/делянка/метка. */
data class NavTarget(val point: LatLon, val label: String)

data class MapFocus(val point: LatLon, val zoom: Double, val token: Int)

enum class MapTool { NONE, RULER, WALK }

data class WalkState(
    val recording: Boolean = false,
    val points: List<LatLon> = emptyList(),
    val saving: Boolean = false,
    val showSaveDialog: Boolean = false,
    val error: String? = null,
) {
    val perimeterMeters: Double get() = pathLengthMeters(points, closed = true)
    val areaSquareMeters: Double get() = polygonAreaSquareMeters(points)
}

data class SearchItem(
    val kind: String,
    val title: String,
    val subtitle: String?,
    val kvartal: String?,
    val vydel: String?,
)

data class SearchState(
    val open: Boolean = false,
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchItem> = emptyList(),
    val error: String? = null,
)

/** Выделы показываем только с масштаба, где их можно разглядеть: на общем плане хватает кварталов. */
private const val MIN_VYDELA_ZOOM = 13.5
private const val FINE_TIER_ZOOM = 14.5
private const val MAX_CELLS_PER_VIEW = 16
private const val MAX_CELLS_IN_MEMORY = 40
private const val PARALLEL_CELL_LOADS = 2
private const val OFFLINE_MESSAGE = "Нет связи с сервером — часть выделов недоступна"
private const val WALK_MIN_STEP_M = 3.0
private const val WALK_MAX_ACCURACY_M = 30f
private const val PLACE_MIN_MOVE_M = 15.0
private const val SEARCH_DEBOUNCE_MS = 350L

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

    private var searchJob: Job? = null
    private var focusCounter = 0
    private var lastPlaceAt: LatLon? = null

    init {
        viewModelScope.launch { hub.download.collect { _uiState.value = _uiState.value.copy(download = it) } }
        _uiState.value = _uiState.value.copy(
            offlineBasePath = hub.prefs.offlineBasePath?.takeIf { java.io.File(it).exists() },
            offlineBaseName = hub.prefs.offlineBasePath?.let { java.io.File(it).name },
        )
        loadLesnichestva()
        loadGeoNotes()
        loadTasks()
        loadSklady()
    }

    /** force — сразу после создания своей метки: кэш на 5 минут её бы ещё не знал. */
    fun loadGeoNotes(force: Boolean = false) {
        viewModelScope.launch {
            repository.getGeoNotes(force).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(geoNotes = it, geoNotesError = null) },
                onFailure = { _uiState.value = _uiState.value.copy(geoNotesError = "Метки не загрузились: ${it.message}") },
            )
        }
    }

    private fun loadTasks() {
        viewModelScope.launch {
            botRepository.listWorkPlan().onSuccess { list ->
                _uiState.value = _uiState.value.copy(tasks = list)
            }
        }
    }

    private fun loadSklady() {
        viewModelScope.launch {
            repository.getSklady().onSuccess { _uiState.value = _uiState.value.copy(sklady = it) }
        }
    }

    /** Цвета по видам работ, статусы делянок и лесные культуры выбранного лесничества — лёгкие запросы. */
    private fun loadColors(num: Int, force: Boolean = false) {
        viewModelScope.launch {
            repository.getWorkColors(num, force).onSuccess { _uiState.value = _uiState.value.copy(workColors = it) }
        }
        viewModelScope.launch {
            repository.getDelyankiForMap().onSuccess { refs ->
                val lesnichestvo = _uiState.value.selectedLesnichestvo
                val colors = HashMap<String, Int>()
                // на одном выделе может быть несколько делянок (архивная и новая) — берём последнюю
                refs.filter { lesnichestvoMatches(it.lesnichestvo, lesnichestvo) }
                    .sortedBy { it.delyankaId }
                    .forEach { ref ->
                        val kv = ref.kvartal?.trim().orEmpty()
                        val vd = ref.vydel?.trim().orEmpty()
                        if (kv.isNotEmpty() && vd.isNotEmpty()) colors[MapRepository.vydelKey(kv, vd)] = DelyankaStatus.fromCode(ref.statusRabot).color
                    }
                _uiState.value = _uiState.value.copy(delyankaStatusColors = colors)
            }
        }
        viewModelScope.launch {
            repository.getLesokultury(num, force).onSuccess { list ->
                _uiState.value = _uiState.value.copy(lesokulturyKeys = list.map { MapRepository.vydelKey(it.kvartal, it.vydel) }.toSet())
            }
        }
    }

    /** Кнопка "Обновить" в слоях: цвета, метки, задачи — без перекачивания геометрии. */
    fun refreshOverlays() {
        val num = currentNum() ?: return
        loadColors(num, force = true)
        loadGeoNotes(force = true)
        loadTasks()
        _uiState.value = _uiState.value.copy(message = "Цвета, метки и задачи обновлены")
    }

    fun setColorMode(mode: ColorMode) {
        _uiState.value = _uiState.value.copy(colorMode = mode)
    }

    private fun currentNum(): Int? = _uiState.value.selectedLesnichestvo?.let { _uiState.value.lesnichestva[it] }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Долгое нажатие на карту — открываем форму новой метки в этой точке. */
    fun startNoteDraft(lat: Double, lon: Double) {
        _uiState.value = _uiState.value.copy(noteDraft = GeoNoteDraft(lat = lat, lon = lon), selectedGeoNote = null)
    }

    fun updateNoteDraftText(text: String) {
        val draft = _uiState.value.noteDraft ?: return
        _uiState.value = _uiState.value.copy(noteDraft = draft.copy(text = text, error = null))
    }

    fun updateNoteDraftCategory(category: GeoNoteCategory) {
        val draft = _uiState.value.noteDraft ?: return
        _uiState.value = _uiState.value.copy(noteDraft = draft.copy(category = category, error = null))
    }

    fun deleteGeoNote(note: GeoNoteMarker) {
        viewModelScope.launch {
            botRepository.deleteGeoNote(note.id).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        selectedGeoNote = null,
                        geoNotes = _uiState.value.geoNotes.filterNot { it.id == note.id },
                        message = "Метка удалена",
                    )
                    loadGeoNotes(force = true)
                },
                onFailure = { _uiState.value = _uiState.value.copy(message = it.message ?: "Не удалось удалить метку") },
            )
        }
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
        if (draft.text.isBlank() && draft.photoUri == null && draft.category == GeoNoteCategory.ZAMETKA) {
            _uiState.value = _uiState.value.copy(noteDraft = draft.copy(error = "Добавьте текст, фото или выберите тип метки"))
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
            val result = botRepository.submitGeoNote(
                draft.lat, draft.lon, draft.text.trim().takeIf { it.isNotBlank() }, photoPath, draft.category.code,
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(noteDraft = null, message = "Метка сохранена")
                    loadGeoNotes(force = true)
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

    private fun onLayersRequested(name: String) {
        _uiState.value.lesnichestva[name]?.let { loadColors(it) }
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
        onLayersRequested(name)
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
        if (vydel != null) loadHistory(selection.kvartal, vydel)
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

    private fun loadHistory(kvartal: String, vydel: String) {
        _uiState.value = _uiState.value.copy(history = null, historyLoading = true)
        viewModelScope.launch {
            val result = repository.getVydelHistory(currentNum(), kvartal, vydel)
            val sel = _uiState.value.selection
            if (sel == null || sel.kvartal != kvartal || sel.vydel != vydel) return@launch
            _uiState.value = _uiState.value.copy(history = result.getOrNull(), historyLoading = false)
        }
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

    // ------------------------------------------------------------------ где я ---

    /** ForestMapView присылает каждую новую GPS-точку (пока открыта карта). */
    fun onLocationFix(lat: Double, lon: Double, accuracy: Float?) {
        val here = LatLon(lat, lon)
        var state = _uiState.value.copy(myLocation = here, myAccuracy = accuracy)

        // обмер обходом: точка в трек, если GPS точный и человек сдвинулся
        val walk = state.walk
        if (walk.recording && (accuracy == null || accuracy <= WALK_MAX_ACCURACY_M)) {
            val last = walk.points.lastOrNull()
            if (last == null || distanceMeters(last, here) >= WALK_MIN_STEP_M) {
                state = state.copy(walk = walk.copy(points = walk.points + here))
            }
        }
        _uiState.value = state

        // квартал/выдел под ногами — не на каждый сдвиг, а когда отошли на 15 м
        val prev = lastPlaceAt
        if (prev == null || distanceMeters(prev, here) >= PLACE_MIN_MOVE_M) {
            lastPlaceAt = here
            updatePlace(here)
        }
    }

    private fun updatePlace(here: LatLon) {
        val state = _uiState.value
        val vydel = state.vydela.lastOrNull { it.contains(here.lat, here.lon) }
        val kvartal = vydel?.kvartal ?: state.kvartaly.lastOrNull { it.contains(here.lat, here.lon) }?.kvartal
        val place = kvartal?.let { Place(state.selectedLesnichestvo, it, vydel?.vydel) }
        CurrentPlace.update(place)
        if (place != state.here) _uiState.value = _uiState.value.copy(here = place)
    }

    /** Тап по плашке "Вы в кв. …" — выделяем этот выдел и открываем его карточку. */
    fun openHere() {
        val place = _uiState.value.here ?: return
        val kind = if (place.vydel != null) ShapeKind.VYDEL else ShapeKind.KVARTAL
        _uiState.value = _uiState.value.copy(selection = MapSelection(place.kvartal, place.vydel, kind), cardOpen = false)
        openSelected()
    }

    // ------------------------------------------------------ веди до делянки ---

    /** Центр выбранного объекта (для "Вести сюда", навигатора и "Поделиться"). */
    fun selectedPoint(): LatLon? {
        val sel = _uiState.value.selection ?: return null
        val state = _uiState.value
        val shapes = when (sel.kind) {
            ShapeKind.KVARTAL -> state.kvartaly
            ShapeKind.VYDEL -> state.vydela
            ShapeKind.LESOSEKA -> state.lesoseki
        }
        val shape = shapes.lastOrNull { it.kvartal == sel.kvartal && it.vydel == sel.vydel } ?: return null
        return LatLon(shape.labelLat, shape.labelLon)
    }

    fun navigateTo(point: LatLon, label: String) {
        _uiState.value = _uiState.value.copy(navTarget = NavTarget(point, label), cardOpen = false)
    }

    fun navigateToSelected(label: String) {
        selectedPoint()?.let { navigateTo(it, label) }
    }

    fun stopNavigation() {
        _uiState.value = _uiState.value.copy(navTarget = null)
    }

    // ---------------------------------------------------------------- поиск ---

    fun openSearch(open: Boolean) {
        _uiState.value = _uiState.value.copy(search = if (open) _uiState.value.search.copy(open = true) else SearchState())
    }

    fun onSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(query = query, error = null))
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(results = emptyList(), loading = false))
            return
        }
        // кварталы лесничества уже в телефоне — их находим сразу и без связи
        val local = _uiState.value.kvartaly
            .filter { it.kvartal == q || (q.length >= 2 && it.kvartal.startsWith(q)) }
            .distinctBy { it.kvartal }
            .take(5)
            .map { SearchItem("kvartal", "Квартал ${it.kvartal}", "есть на карте", it.kvartal, null) }
        _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(results = local, loading = true))
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            repository.search(q, currentNum()).fold(
                onSuccess = { remote ->
                    val items = remote.map { r ->
                        SearchItem(r.type, r.title, r.subtitle, r.kvartal, r.vydel)
                    }
                    val merged = (local + items.filterNot { it.kind == "kvartal" && local.any { l -> l.kvartal == it.kvartal } })
                    _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(results = merged, loading = false))
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        search = _uiState.value.search.copy(loading = false, error = if (local.isEmpty()) err.message else null),
                    )
                },
            )
        }
    }

    /** Результат поиска/задача: перелетаем туда и выделяем объект. */
    fun goTo(item: SearchItem) {
        val kvartal = item.kvartal ?: return
        _uiState.value = _uiState.value.copy(search = SearchState(), tasksOpen = false)
        focusOn(kvartal, item.vydel, openCard = true)
    }

    private fun focusOn(kvartal: String, vydel: String?, openCard: Boolean) {
        val state = _uiState.value
        val kind = if (vydel != null) ShapeKind.VYDEL else ShapeKind.KVARTAL
        _uiState.value = state.copy(selection = MapSelection(kvartal, vydel, kind), cardOpen = false)

        val loaded = (if (vydel != null) state.vydela.lastOrNull { it.kvartal == kvartal && it.vydel == vydel } else null)
            ?: state.kvartaly.lastOrNull { it.kvartal == kvartal && vydel == null }
        if (loaded != null) {
            setFocus(LatLon(loaded.labelLat, loaded.labelLon), if (vydel != null) 16.0 else 14.0)
            if (openCard) openSelected()
            return
        }
        val num = currentNum() ?: return
        if (vydel == null) {
            _uiState.value = _uiState.value.copy(message = "Квартал $kvartal не найден в этом лесничестве")
            return
        }
        viewModelScope.launch {
            val loc = repository.getVydelLocation(num, kvartal, vydel).getOrNull()
            if (loc?.found == true && loc.lat != null && loc.lon != null) {
                setFocus(LatLon(loc.lat, loc.lon), 16.0)
                if (openCard) openSelected()
            } else {
                // выдела нет в слое — хотя бы к кварталу
                val kv = _uiState.value.kvartaly.lastOrNull { it.kvartal == kvartal }
                if (kv != null) setFocus(LatLon(kv.labelLat, kv.labelLon), 14.0)
                _uiState.value = _uiState.value.copy(message = "Выдел $vydel не найден на карте — показан квартал $kvartal")
            }
        }
    }

    private fun setFocus(point: LatLon, zoom: Double) {
        focusCounter += 1
        _uiState.value = _uiState.value.copy(focus = MapFocus(point, zoom, focusCounter))
    }

    fun focusOnMe() {
        _uiState.value.myLocation?.let { setFocus(it, 16.0) }
    }

    // ---------------------------------------------------------- мои задачи ---

    fun openTasks(open: Boolean) {
        _uiState.value = _uiState.value.copy(tasksOpen = open)
    }

    fun goToTask(task: WorkPlanItemDto) {
        val kv = task.kvartal?.trim() ?: return
        _uiState.value = _uiState.value.copy(tasksOpen = false)
        focusOn(kv, task.vydel?.trim()?.takeIf { it.isNotEmpty() }, openCard = true)
    }

    /** Ключи "кв|выд" задач текущего лесничества — выделы обводятся на карте. */
    fun taskKeys(state: MapUiState): Set<String> = state.tasks
        .filter { lesnichestvoMatches(it.lesnichestvo, state.selectedLesnichestvo) }
        .mapNotNull { t ->
            val kv = t.kvartal?.trim().orEmpty()
            val vd = t.vydel?.trim().orEmpty()
            if (kv.isNotEmpty() && vd.isNotEmpty()) MapRepository.vydelKey(kv, vd) else null
        }.toSet()

    // -------------------------------------------------------------- склады ---

    fun onSkladTap(sklad: SkladDto) {
        _uiState.value = _uiState.value.copy(selectedSklad = sklad, selectedGeoNote = null)
    }

    fun dismissSklad() {
        _uiState.value = _uiState.value.copy(selectedSklad = null)
    }

    // ---------------------------------------------------------- инструменты ---

    fun setTool(tool: MapTool) {
        val current = _uiState.value
        if (current.walk.recording && tool != MapTool.WALK) return // сначала остановите обмер
        _uiState.value = current.copy(
            tool = tool,
            rulerPoints = if (tool == MapTool.RULER) current.rulerPoints else emptyList(),
            walk = if (tool == MapTool.WALK) current.walk else WalkState(),
            selection = if (tool != MapTool.NONE) null else current.selection,
            cardOpen = false,
        )
    }

    fun onRulerTap(point: LatLon) {
        _uiState.value = _uiState.value.copy(rulerPoints = _uiState.value.rulerPoints + point)
    }

    fun undoRulerPoint() {
        _uiState.value = _uiState.value.copy(rulerPoints = _uiState.value.rulerPoints.dropLast(1))
    }

    fun clearRuler() {
        _uiState.value = _uiState.value.copy(rulerPoints = emptyList())
    }

    fun startWalk() {
        val walk = _uiState.value.walk
        // первая точка — где стоим сейчас
        val first = _uiState.value.myLocation
        _uiState.value = _uiState.value.copy(
            walk = walk.copy(recording = true, error = null, points = if (walk.points.isEmpty() && first != null) listOf(first) else walk.points),
        )
    }

    fun pauseWalk() {
        _uiState.value = _uiState.value.copy(walk = _uiState.value.walk.copy(recording = false))
    }

    fun undoWalkPoint() {
        val walk = _uiState.value.walk
        _uiState.value = _uiState.value.copy(walk = walk.copy(points = walk.points.dropLast(1)))
    }

    fun finishWalk() {
        val walk = _uiState.value.walk
        if (walk.points.size < 3) {
            _uiState.value = _uiState.value.copy(walk = walk.copy(error = "Нужно минимум 3 точки — пройдите по границе участка"))
            return
        }
        _uiState.value = _uiState.value.copy(walk = walk.copy(recording = false, showSaveDialog = true, error = null))
    }

    fun dismissWalkDialog() {
        _uiState.value = _uiState.value.copy(walk = _uiState.value.walk.copy(showSaveDialog = false))
    }

    fun saveWalk(name: String, note: String) {
        val walk = _uiState.value.walk
        val here = _uiState.value.here
        _uiState.value = _uiState.value.copy(walk = walk.copy(saving = true, error = null))
        viewModelScope.launch {
            val request = TrackCreateRequest(
                nazvanie = name.trim().ifBlank { null },
                points = walk.points.map { listOf(it.lon, it.lat) },
                closed = true,
                kvartal = here?.kvartal,
                vydel = here?.vydel,
                lesnichestvo = _uiState.value.selectedLesnichestvo,
                noteText = note.trim().ifBlank { null },
            )
            botRepository.createTrack(request).fold(
                onSuccess = { created ->
                    val area = created.ploshadGa?.let { String.format(java.util.Locale("ru"), "%.2f га", it) }
                        ?: formatArea(walk.areaSquareMeters)
                    _uiState.value = _uiState.value.copy(
                        tool = MapTool.NONE,
                        walk = WalkState(),
                        message = "Контур сохранён: $area — он уже виден в QGIS",
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(walk = _uiState.value.walk.copy(saving = false, error = err.message ?: "Не удалось сохранить"))
                },
            )
        }
    }

    // ------------------------------------------------------ офлайн-подложка ---

    /** Своя подложка: файл .mbtiles копируется в память приложения и дальше работает без сети. */
    fun importOfflineBase(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = queryDisplayName(app, uri) ?: "podlozhka.mbtiles"
                    if (!name.lowercase().endsWith(".mbtiles")) error("Нужен файл .mbtiles (в QGIS: «Создать XYZ-тайлы (MBTiles)»)")
                    val dir = java.io.File(app.filesDir, "offline_maps").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() } // одна подложка — старую убираем, чтобы не копить гигабайты
                    val target = java.io.File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    } ?: error("Не удалось открыть файл")
                    target
                }
            }
            result.fold(
                onSuccess = { file ->
                    hub.prefs.offlineBasePath = file.path
                    _uiState.value = _uiState.value.copy(
                        offlineBasePath = file.path,
                        offlineBaseName = file.name,
                        layers = _uiState.value.layers.copy(offlineBase = true),
                        message = "Подложка «${file.name}» загружена — работает без интернета",
                    )
                },
                onFailure = { _uiState.value = _uiState.value.copy(message = it.message ?: "Не удалось загрузить подложку") },
            )
        }
    }

    fun removeOfflineBase() {
        _uiState.value.offlineBasePath?.let { java.io.File(it).delete() }
        hub.prefs.offlineBasePath = null
        _uiState.value = _uiState.value.copy(offlineBasePath = null, offlineBaseName = null, message = "Подложка удалена")
    }

    private fun queryDisplayName(context: Application, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
