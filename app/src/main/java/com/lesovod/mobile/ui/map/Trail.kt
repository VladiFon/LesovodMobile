package com.lesovod.mobile.ui.map

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** След смены: где я сегодня прошёл. Пишется только по кнопке и только пока открыта карта. */
data class TrailState(
    val recording: Boolean = false,
    val points: List<LatLon> = emptyList(),
    val startedAt: Long = 0L,
) {
    val lengthM: Double get() = pathLengthMeters(points)
}

/**
 * Хранит след в файле на телефоне — закрыли приложение, вернулись, след на месте.
 * На сервер ничего не уходит, пока человек сам не нажмёт «Сохранить» (без слежки).
 */
object TrailStore {
    private const val FILE = "trail.txt"
    private const val MIN_STEP_M = 10.0
    private const val MAX_ACCURACY_M = 40f

    private val _state = MutableStateFlow(TrailState())
    val state: StateFlow<TrailState> = _state.asStateFlow()

    @Volatile private var file: File? = null
    private var unsaved = 0

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, FILE)
        file = f
        if (!f.exists()) return
        runCatching {
            val lines = f.readLines()
            val head = lines.firstOrNull()?.split(';') ?: return
            val started = head.getOrNull(1)?.toLongOrNull() ?: 0L
            val pts = lines.drop(1).mapNotNull { l ->
                val p = l.split(',')
                val lat = p.getOrNull(0)?.toDoubleOrNull()
                val lon = p.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null) LatLon(lat, lon) else null
            }
            // запись не продолжается сама после перезапуска — только след остаётся на карте
            _state.value = TrailState(recording = false, points = pts, startedAt = started)
        }
    }

    fun start() {
        val s = _state.value
        _state.value = s.copy(recording = true, startedAt = if (s.points.isEmpty()) System.currentTimeMillis() else s.startedAt)
        save()
    }

    fun pause() {
        _state.value = _state.value.copy(recording = false)
        save()
    }

    fun clear() {
        _state.value = TrailState()
        runCatching { file?.delete() }
    }

    /** true — точка легла в след. */
    fun onFix(p: LatLon, accuracy: Float?): Boolean {
        val s = _state.value
        if (!s.recording) return false
        if (accuracy != null && accuracy > MAX_ACCURACY_M) return false
        val last = s.points.lastOrNull()
        if (last != null && distanceMeters(last, p) < MIN_STEP_M) return false
        _state.value = s.copy(points = s.points + p)
        if (++unsaved >= 5) save()
        return true
    }

    private fun save() {
        unsaved = 0
        val f = file ?: return
        val s = _state.value
        runCatching {
            f.writeText(buildString {
                append("trail;").append(s.startedAt).append('\n')
                s.points.forEach { append(it.lat).append(',').append(it.lon).append('\n') }
            })
        }
    }

    fun flush() = save()
}

/** «Назад по следу»: от ближайшей ко мне точки следа обратно к его началу. */
fun trailRoute(points: List<LatLon>, me: LatLon): RoadRoute? {
    if (points.size < 2) return null
    val idx = points.indices.minBy { distanceMeters(points[it], me) }
    val back = points.subList(0, idx + 1).asReversed().toList()
    if (back.size < 2) return null
    val target = points.first()
    val walk = distanceMeters(me, back.first())
    val along = pathLengthMeters(back)
    val minutes = (walk + along) / (WALK_KMH * 1000.0 / 60.0)
    return RoadRoute(Vehicle.UAZ, me, target, back, along, walk, minutes, distanceMeters(me, target), byTrail = true)
}
