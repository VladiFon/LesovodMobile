package com.lesovod.mobile.ui.map

import com.lesovod.mobile.data.network.dto.DorogaDto
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/** Тип лесной дороги: подпись, цвет на карте и насколько медленнее по ней ехать. */
enum class RoadTip(val code: String, val label: String, val color: Int, val speedFactor: Double, val dashed: Boolean) {
    GRUNTOVKA("gruntovka", "Грунтовка", 0xFFFFB300.toInt(), 1.0, false),
    LEZHNEVKA("lezhnevka", "Лежнёвка", 0xFF8D6E63.toInt(), 0.7, false),
    ZIMNIK("zimnik", "Зимник", 0xFF4FC3F7.toInt(), 0.8, true),
    VOLOK("volok", "Волок", 0xFFE57373.toInt(), 0.45, true);

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: GRUNTOVKA
    }
}

/** Кто проедет. Скорость — средняя по лесной дороге, км/ч. */
enum class Vehicle(val code: String, val label: String, val kmh: Double) {
    UAZ("uaz", "УАЗ", 25.0),
    LESOVOZ("lesovoz", "Лесовоз", 15.0),
    TRAKTOR("traktor", "Трактор", 8.0),
    LEGKOVAYA("legkovaya", "Легковая", 30.0);

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
        /** Вкладки в панели маршрута. */
        val tabs = listOf(UAZ, LESOVOZ, TRAKTOR)
    }
}

const val WALK_KMH = 4.0

class Road(
    val id: Int,
    val name: String?,
    val tip: RoadTip,
    val proezd: Set<Vehicle>,
    val points: List<LatLon>,
    val lengthM: Double,
    val lesnichestvo: String?,
    val comment: String?,
    val author: String?,
    val createdAt: String?,
) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: tip.label
}

fun DorogaDto.toRoad(): Road {
    val pts = tochki.mapNotNull { c -> if (c.size >= 2) LatLon(c[1], c[0]) else null }
    return Road(
        id = id,
        name = nazvanie,
        tip = RoadTip.of(tip),
        proezd = proezd.mapNotNull { Vehicle.of(it) }.toSet(),
        points = pts,
        lengthM = dlinaM ?: pathLengthMeters(pts),
        lesnichestvo = lesnichestvo,
        comment = kommentariy,
        author = sozdalFio,
        createdAt = createdAt,
    )
}

/** Маршрут: пешком до дороги → по дорогам → пешком от дороги до цели. */
class RoadRoute(
    val vehicle: Vehicle,
    val start: LatLon,
    val target: LatLon,
    /** Точки по дорогам (без пеших кусков). Пусто — дорог не нашлось, ведём по прямой. */
    val roadPath: List<LatLon>,
    val roadMeters: Double,
    val walkMeters: Double,
    val minutes: Double,
    /** Сколько было бы напрямик — чтобы честно сказать, если по прямой короче. */
    val straightMeters: Double,
) {
    val byRoad: Boolean get() = roadPath.size >= 2
    val totalMeters: Double get() = roadMeters + walkMeters

    /** Вся линия маршрута целиком, для подсказок «через 200 м направо». */
    val fullPath: List<LatLon> by lazy { if (byRoad) listOf(start) + roadPath + target else listOf(start, target) }
}

/** Подсказка на ходу: сколько до поворота, куда, сколько осталось. */
class RouteGuidance(
    val toTurnMeters: Double?,
    /** -1 налево, 1 направо, 0 — дальше прямо до цели. */
    val turn: Int,
    val sharp: Boolean,
    val remainingMeters: Double,
    val remainingMinutes: Double,
    val offRouteMeters: Double,
)

/**
 * Граф лесных дорог для одного вида транспорта. Линии дробятся на кусочки по ~25 м,
 * концы и пересечения разных дорог ближе 20 м склеиваются в один узел — так «вот тут
 * дорога упирается в другую» работает, даже если мастер нарисовал её не идеально.
 */
class RoadGraph private constructor(
    private val lat: DoubleArray,
    private val lon: DoubleArray,
    private val adj: Array<MutableList<Edge>>,
) {
    private class Edge(val to: Int, val meters: Double, val factor: Double)

    val size: Int get() = lat.size

    private fun node(i: Int) = LatLon(lat[i], lon[i])

    fun route(start: LatLon, target: LatLon, vehicle: Vehicle): RoadRoute {
        val straight = distanceMeters(start, target)
        val walkOnly = RoadRoute(vehicle, start, target, emptyList(), 0.0, straight, straight / mPerMin(WALK_KMH), straight)
        if (size == 0) return walkOnly

        val toStart = DoubleArray(size) { distanceMeters(start, node(it)) }
        val toTarget = DoubleArray(size) { distanceMeters(node(it), target) }
        val nearStart = toStart.min()
        val nearTarget = toTarget.min()
        // до дороги дальше 3 км — это уже не «подойти к дороге», ведём по прямой
        if (nearStart > MAX_WALK_TO_ROAD_M || nearTarget > MAX_WALK_TO_ROAD_M) return walkOnly
        val startRadius = max(ENTRY_RADIUS_M, nearStart + 50)
        val endRadius = max(ENTRY_RADIUS_M, nearTarget + 50)
        val walkSpeed = mPerMin(WALK_KMH)
        val driveSpeed = mPerMin(vehicle.kmh)

        val cost = DoubleArray(size) { Double.MAX_VALUE }
        val prev = IntArray(size) { -1 }
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        for (i in 0 until size) if (toStart[i] <= startRadius) {
            cost[i] = toStart[i] / walkSpeed
            queue.add(cost[i] to i)
        }
        var best = Double.MAX_VALUE
        var bestNode = -1
        while (queue.isNotEmpty()) {
            val (c, u) = queue.poll()!!
            if (c > cost[u]) continue
            if (c >= best) break
            if (toTarget[u] <= endRadius) {
                val total = c + toTarget[u] / walkSpeed
                if (total < best) { best = total; bestNode = u }
            }
            for (e in adj[u]) {
                val nc = c + e.meters / (driveSpeed * e.factor)
                if (nc < cost[e.to]) {
                    cost[e.to] = nc
                    prev[e.to] = u
                    queue.add(nc to e.to)
                }
            }
        }
        if (bestNode < 0 || best >= walkOnly.minutes) return walkOnly

        val nodes = ArrayList<Int>()
        var v = bestNode
        while (v >= 0) { nodes.add(v); v = prev[v] }
        nodes.reverse()
        val path = nodes.map { node(it) }
        val walk = toStart[nodes.first()] + toTarget[bestNode]
        return RoadRoute(vehicle, start, target, path, pathLengthMeters(path), walk, best, straight)
    }

    companion object {
        private const val STEP_M = 25.0
        private const val SNAP_M = 20.0
        private const val ENTRY_RADIUS_M = 300.0
        private const val MAX_WALK_TO_ROAD_M = 3000.0

        private fun mPerMin(kmh: Double) = kmh * 1000.0 / 60.0

        fun build(roads: List<Road>, vehicle: Vehicle): RoadGraph {
            val lat = ArrayList<Double>()
            val lon = ArrayList<Double>()
            val adj = ArrayList<MutableList<Edge>>()
            val grid = HashMap<Long, MutableList<Int>>()
            val lat0 = roads.firstOrNull { it.points.isNotEmpty() }?.points?.first()?.lat ?: 60.0
            val cellLat = SNAP_M / 111_320.0
            val cellLon = SNAP_M / (111_320.0 * cos(Math.toRadians(lat0)).coerceAtLeast(0.1))

            fun key(ix: Int, iy: Int) = (ix.toLong() shl 32) xor (iy.toLong() and 0xffffffffL)

            fun nodeFor(p: LatLon): Int {
                val ix = floor(p.lon / cellLon).toInt()
                val iy = floor(p.lat / cellLat).toInt()
                var found = -1
                var foundD = SNAP_M
                for (dx in -1..1) for (dy in -1..1) {
                    grid[key(ix + dx, iy + dy)]?.forEach { n ->
                        val d = distanceMeters(p, LatLon(lat[n], lon[n]))
                        if (d <= foundD) { foundD = d; found = n }
                    }
                }
                if (found >= 0) return found
                val id = lat.size
                lat.add(p.lat); lon.add(p.lon); adj.add(ArrayList(3))
                grid.getOrPut(key(ix, iy)) { ArrayList(2) }.add(id)
                return id
            }

            fun link(a: Int, b: Int, factor: Double) {
                if (a == b) return
                val m = distanceMeters(LatLon(lat[a], lon[a]), LatLon(lat[b], lon[b]))
                if (adj[a].any { it.to == b }) return
                adj[a].add(Edge(b, m, factor))
                adj[b].add(Edge(a, m, factor))
            }

            for (road in roads) {
                if (vehicle !in road.proezd || road.points.size < 2) continue
                var last = nodeFor(road.points[0])
                for (i in 1 until road.points.size) {
                    val a = road.points[i - 1]
                    val b = road.points[i]
                    val n = ceil(distanceMeters(a, b) / STEP_M).toInt().coerceAtLeast(1)
                    for (k in 1..n) {
                        val t = k.toDouble() / n
                        val cur = nodeFor(LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
                        link(last, cur, road.tip.speedFactor)
                        last = cur
                    }
                }
            }
            return RoadGraph(lat.toDoubleArray(), lon.toDoubleArray(), adj.toTypedArray())
        }
    }
}

/** Где я на маршруте и что впереди. Считается на каждую GPS-точку — путь короткий, это быстро. */
fun RoadRoute.guidance(me: LatLon): RouteGuidance {
    val path = fullPath
    // ближайший отрезок маршрута
    var bestSeg = 0
    var bestD = Double.MAX_VALUE
    var bestT = 0.0
    val mLat = 111_320.0
    val mLon = 111_320.0 * cos(Math.toRadians(me.lat))
    for (i in 0 until path.size - 1) {
        val a = path[i]
        val b = path[i + 1]
        val ax = (a.lon - me.lon) * mLon
        val ay = (a.lat - me.lat) * mLat
        val bx = (b.lon - me.lon) * mLon
        val by = (b.lat - me.lat) * mLat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + dx * t
        val py = ay + dy * t
        val d = kotlin.math.sqrt(px * px + py * py)
        if (d < bestD) { bestD = d; bestSeg = i; bestT = t }
    }
    val segLen = distanceMeters(path[bestSeg], path[bestSeg + 1])
    var ahead = segLen * (1 - bestT)
    var remaining = ahead
    for (i in bestSeg + 1 until path.size - 1) remaining += distanceMeters(path[i], path[i + 1])

    var toTurn: Double? = null
    var turn = 0
    var sharp = false
    for (i in bestSeg + 1 until path.size - 1) {
        val inB = bearingDegrees(path[i - 1], path[i])
        val outB = bearingDegrees(path[i], path[i + 1])
        var diff = outB - inB
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        if (abs(diff) >= TURN_DEG) {
            toTurn = ahead
            turn = if (diff > 0) 1 else -1
            sharp = abs(diff) >= 100
            break
        }
        ahead += distanceMeters(path[i], path[i + 1])
    }
    val share = if (totalMeters > 0) (remaining / totalMeters).coerceIn(0.0, 1.0) else 0.0
    return RouteGuidance(toTurn, turn, sharp, remaining, minutes * share, bestD)
}

private const val TURN_DEG = 35.0

/** «Через 120 м налево» / «Дальше прямо 1,2 км». */
fun RouteGuidance.say(): String = when {
    remainingMeters < 25 -> "Вы на месте"
    toTurnMeters == null -> "Прямо ${formatDistance(remainingMeters)} до цели"
    toTurnMeters < 20 -> (if (turn > 0) "Направо" else "Налево") + (if (sharp) " круто" else "") + " сейчас"
    else -> "Через ${formatDistance(toTurnMeters)} " + (if (turn > 0) "направо" else "налево") + (if (sharp) " круто" else "")
}

fun formatMinutes(min: Double): String {
    val m = min.toInt().coerceAtLeast(1)
    return if (m < 60) "~$m мин" else "~${m / 60} ч ${m % 60} мин"
}
