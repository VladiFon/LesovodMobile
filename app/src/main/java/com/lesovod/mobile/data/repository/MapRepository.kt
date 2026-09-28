package com.lesovod.mobile.data.repository

import com.lesovod.mobile.data.local.MapCache
import com.lesovod.mobile.data.network.ApiService
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.network.extractErrorMessage
import com.lesovod.mobile.ui.map.GeoJsonStreamParser
import com.lesovod.mobile.ui.map.GeoNoteMarker
import com.lesovod.mobile.ui.map.MapShape
import com.lesovod.mobile.ui.map.ShapeCodec
import com.lesovod.mobile.ui.map.ShapeKind
import com.lesovod.mobile.ui.map.toMarker
import com.lesovod.mobile.data.network.dto.LesokulturyMapDto
import com.lesovod.mobile.data.network.dto.MapSearchResultDto
import com.lesovod.mobile.data.network.dto.SkladDto
import com.lesovod.mobile.data.network.dto.VydelHistoryDto
import com.lesovod.mobile.data.network.dto.VydelLocationDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.InputStream
import java.io.OutputStream

/** Размер ячейки сетки выделов и то, с каким упрощением сервер отдаёт её геометрию. */
enum class CellTier(val degrees: Double, val serverZoom: Double, val tag: String) {
    COARSE(0.06, 13.0, "c60"),
    FINE(0.03, 16.0, "f30"),
}

/**
 * Слои карты читаются с диска, а сеть нужна только когда файл устарел (см. [cached]):
 * карта открывается сразу и работает без интернета там, где данные уже скачаны.
 */
class MapRepository(
    private val api: ApiService,
    private val cache: MapCache,
    /** "Bearer …" текущего рабочего — метки, цвета, поиск и история доступны только с токеном. */
    private val bearerToken: () -> String? = { null },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Данные старше этой отметки считаются устаревшими независимо от TTL. */
    @Volatile
    var minValidEpoch: Long = 0L

    suspend fun listLesnichestva(onStale: (Map<String, Int>) -> Unit = {}): Result<Map<String, Int>> = cached(
        key = "lesnichestva",
        ttlMs = DAY_MS,
        fetch = { api.listLesnichestva() },
        decode = { json.decodeFromString<Map<String, Int>>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
        onStale = onStale,
    )

    suspend fun getKvartaly(num: Int, force: Boolean = false, onStale: (List<MapShape>) -> Unit = {}): Result<List<MapShape>> =
        cachedShapes(
            key = "kvartaly_$num",
            ttlMs = LAYER_TTL_MS,
            force = force,
            fetch = { api.getKvartalyRaw(num.toString(), null, 13.0).use { GeoJsonStreamParser.parse(it.byteStream(), ShapeKind.KVARTAL) } },
            onStale = onStale,
        )

    /**
     * Только слой лесосек из моста ГИСлесхоз. Живые данные показали, что мост называет слой
     * человекочитаемым именем вида "Лесосеки 10,09,2026", поэтому сверяемся по подстроке "лесосек".
     * Плагин «Лесовод-мост» публиковал слой латиницей "qgis_lesoseki" — такие слои раньше
     * отфильтровывались, и лесосеки из QGIS на телефоне не показывались вовсе.
     */
    suspend fun getLesoseki(num: Int, force: Boolean = false): Result<List<MapShape>> = cachedShapes(
        key = "lesoseki_v2_$num", // v2: после исправления фильтра по имени слоя старый пустой кэш не годится
        ttlMs = LESOSEKI_TTL_MS,
        force = force,
        fetch = {
            api.getImportLayersRaw(num.toString()).use {
                GeoJsonStreamParser.parse(it.byteStream(), ShapeKind.LESOSEKA, layerNameContains = LESOSEKI_LAYER_NAMES)
            }
        },
    )

    fun cellKey(num: Int, tier: CellTier, ix: Int, iy: Int) = "vydela_${num}_${tier.tag}_${ix}_$iy"

    /** Выделы грузим не по каждому сдвигу карты, а ячейками сетки — каждая ячейка скачивается один раз и живёт на диске. */
    suspend fun getVydelaCell(
        num: Int,
        tier: CellTier,
        ix: Int,
        iy: Int,
        force: Boolean = false,
        onStale: (List<MapShape>) -> Unit = {},
    ): Result<List<MapShape>> {
        val west = ix * tier.degrees
        val south = iy * tier.degrees
        val bbox = "$west,$south,${west + tier.degrees},${south + tier.degrees}"
        return cachedShapes(
            key = cellKey(num, tier, ix, iy),
            ttlMs = LAYER_TTL_MS,
            force = force,
            fetch = { api.getVydelaRaw(num.toString(), bbox, tier.serverZoom).use { GeoJsonStreamParser.parse(it.byteStream(), ShapeKind.VYDEL) } },
            onStale = onStale,
        )
    }

    /** Карточки таксации кэшируются по мере просмотра — открытые однажды работают и без связи. */
    suspend fun getVydelCard(kvartal: String, vydel: String, lesnichestvo: String?): Result<JsonObject> = cached(
        key = "card_${lesnichestvo.orEmpty()}_${kvartal}_$vydel",
        ttlMs = DAY_MS,
        fetch = { api.getVydelCard(kvartal, vydel, lesnichestvo) },
        decode = { json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject },
        encode = { value, out -> out.write(value.toString().toByteArray()) },
    )

    /** Где заведены делянки (кв./выд. → delyanka_id). Кэшируется ненадолго: список меняется, когда заводят делянки. */
    suspend fun getDelyankiForMap(): Result<List<DelyankaMapRefDto>> = cached(
        key = "delyanki_for_map",
        ttlMs = DELYANKI_TTL_MS,
        fetch = { api.getDelyankiForMap() },
        decode = { json.decodeFromString<List<DelyankaMapRefDto>>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /**
     * Метки рабочего на карте — только свои (GET /api/bot/geo-notes, по токену). Раньше приложение
     * ходило на GET /api/map/geo-notes.geojson, который требует токен QGIS-моста, получало ошибку и
     * молча ничего не рисовало. Короткий TTL: список должен обновляться часто, не как лесной слой.
     */
    suspend fun getGeoNotes(force: Boolean = false): Result<List<GeoNoteMarker>> = cached(
        key = "geo_notes_mine",
        ttlMs = GEO_NOTES_TTL_MS,
        force = force,
        fetch = { api.listMyGeoNotes(requireToken()).map { it.toMarker() } },
        decode = { json.decodeFromString<List<GeoNoteMarker>>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /**
     * Цвета выделов по видам выполненных работ — отдельно от геометрии: геометрия лежит в кэше
     * неделями, а цвет должен меняться, как только работу подтвердили (раньше цвет был зашит в
     * геометрию выделов и устаревал до следующего "Скачать карту").
     */
    suspend fun getWorkColors(num: Int, force: Boolean = false): Result<Map<String, Int>> = cached(
        key = "work_colors_$num",
        ttlMs = WORK_COLORS_TTL_MS,
        force = force,
        fetch = {
            api.getWorkColors(requireToken(), num.toString()).items.mapNotNull { item ->
                val color = runCatching { android.graphics.Color.parseColor(item.color) }.getOrNull() ?: return@mapNotNull null
                vydelKey(item.kvartal, item.vydel) to color
            }.toMap()
        },
        decode = { json.decodeFromString<Map<String, Int>>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /** Участки лесных культур лесничества (кв./выд.) — для слоя "Лесные культуры". */
    suspend fun getLesokultury(num: Int, force: Boolean = false): Result<List<LesokulturyMapDto>> = cached(
        key = "lesokultury_$num",
        ttlMs = DELYANKI_TTL_MS,
        force = force,
        fetch = { api.getLesokulturyForMap(requireToken(), num.toString()) },
        decode = { json.decodeFromString<List<LesokulturyMapDto>>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /** Склады — точки на карте; меняются редко. */
    suspend fun getSklady(force: Boolean = false): Result<List<SkladDto>> = cached(
        key = "sklady",
        ttlMs = DAY_MS,
        force = force,
        fetch = { api.listSklady() },
        decode = { json.decodeFromString<List<SkladDto>>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /** История выдела для карточки — кэшируется, чтобы открытая однажды работала без связи. */
    suspend fun getVydelHistory(num: Int?, kvartal: String, vydel: String): Result<VydelHistoryDto> = cached(
        key = "history_${num ?: 0}_${kvartal}_$vydel",
        ttlMs = DELYANKI_TTL_MS,
        fetch = { api.getVydelHistory(requireToken(), kvartal, vydel, num?.toString()) },
        decode = { json.decodeFromString<VydelHistoryDto>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /** Центр выдела (для поиска и "веди до делянки"), кэш на сутки — выделы не двигаются. */
    suspend fun getVydelLocation(num: Int, kvartal: String, vydel: String): Result<VydelLocationDto> = cached(
        key = "loc_${num}_${kvartal}_$vydel",
        ttlMs = LAYER_TTL_MS * 14,
        fetch = { api.getVydelLocation(num.toString(), kvartal, vydel) },
        decode = { json.decodeFromString<VydelLocationDto>(it.readBytes().decodeToString()) },
        encode = { value, out -> out.write(json.encodeToString(value).toByteArray()) },
    )

    /** Поиск всегда идёт в сеть (без кэша): это ответ на только что набранный текст. */
    suspend fun search(query: String, num: Int?): Result<List<MapSearchResultDto>> = withContext(Dispatchers.IO) {
        try {
            Result.success(api.searchMap(requireToken(), query, num?.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(Exception(describe(e)))
        }
    }

    private fun requireToken(): String = bearerToken() ?: throw IllegalStateException("Сессия истекла, войдите заново")

    /** Данные конкретной делянки: {delyanka, items[]}. */
    suspend fun getDelyanka(id: Int): Result<JsonObject> = cached(
        key = "delyanka_$id",
        ttlMs = DELYANKI_TTL_MS,
        fetch = { api.getDelyanka(id) },
        decode = { json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject },
        encode = { value, out -> out.write(value.toString().toByteArray()) },
    )

    private suspend fun cachedShapes(
        key: String,
        ttlMs: Long,
        force: Boolean,
        fetch: suspend () -> List<MapShape>,
        onStale: (List<MapShape>) -> Unit = {},
    ): Result<List<MapShape>> = cached(key, ttlMs, fetch, { ShapeCodec.read(it) }, { v, out -> ShapeCodec.write(v, out) }, onStale, force)

    /**
     * Свежий файл из кэша → отдаём сразу, сети нет. Устаревший → показываем его через onStale,
     * пока идёт обновление, а если сети нет — остаёмся на нём. Нет файла → сеть обязательна.
     * Нехватка памяти при разборе не роняет приложение, а превращается в обычную ошибку.
     */
    private suspend fun <T> cached(
        key: String,
        ttlMs: Long,
        fetch: suspend () -> T,
        decode: (InputStream) -> T,
        encode: (T, OutputStream) -> Unit,
        onStale: (T) -> Unit = {},
        force: Boolean = false,
    ): Result<T> = withContext(Dispatchers.IO) {
        val file = cache.file(key)
        val stored: T? = if (file.exists()) runCatching { file.inputStream().buffered().use(decode) }.getOrNull() else null
        val fresh = stored != null && !force && file.lastModified() >= maxOf(System.currentTimeMillis() - ttlMs, minValidEpoch)

        if (stored != null && fresh) return@withContext Result.success(stored)
        if (stored != null) onStale(stored)

        try {
            val value = fetch()
            cache.write(key) { encode(value, it) }
            Result.success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (stored != null) Result.success(stored) else Result.failure(Exception(describe(e)))
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is HttpException -> extractErrorMessage(e, "Ошибка сервера")
        is OutOfMemoryError -> "Не хватило памяти для загрузки карты"
        else -> e.message ?: "Не удалось связаться с сервером"
    }

    companion object {
        val LESOSEKI_LAYER_NAMES = listOf("лесосек", "lesosek")

        fun vydelKey(kvartal: String, vydel: String) = "${kvartal.trim()}|${vydel.trim()}"

        private const val HOUR_MS = 60 * 60 * 1000L
        const val DAY_MS = 24 * HOUR_MS
        const val LAYER_TTL_MS = 12 * HOUR_MS
        const val LESOSEKI_TTL_MS = 3 * HOUR_MS
        const val DELYANKI_TTL_MS = HOUR_MS / 2
        const val GEO_NOTES_TTL_MS = 5 * 60 * 1000L
        const val WORK_COLORS_TTL_MS = 5 * 60 * 1000L
    }
}
