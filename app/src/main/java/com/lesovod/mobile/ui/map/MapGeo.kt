package com.lesovod.mobile.ui.map

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Точка на карте без привязки к osmdroid — хранится в состоянии экрана и в кэше. */
@Serializable
data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_008.8
private val RU = Locale("ru")

/** Расстояние по поверхности Земли, метры. */
fun distanceMeters(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
}

/** Азимут от a на b, градусы 0..360 (0 — север, по часовой). */
fun bearingDegrees(a: LatLon, b: LatLon): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Длина ломаной, метры. */
fun pathLengthMeters(points: List<LatLon>, closed: Boolean = false): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) total += distanceMeters(points[i - 1], points[i])
    if (closed && points.size > 2) total += distanceMeters(points.last(), points.first())
    return total
}

/**
 * Площадь многоугольника, м². На масштабе делянки (сотни метров) равнопромежуточная проекция
 * вокруг первой точки даёт ошибку меньше долей процента — точнее GPS-обхода. Сервер
 * пересчитывает площадь сохранённого контура в UTM.
 */
fun polygonAreaSquareMeters(points: List<LatLon>): Double {
    if (points.size < 3) return 0.0
    val lat0 = Math.toRadians(points.first().lat)
    val mPerDegLat = PI * EARTH_RADIUS_M / 180.0
    val mPerDegLon = mPerDegLat * cos(lat0)
    var sum = 0.0
    for (i in points.indices) {
        val p = points[i]
        val q = points[(i + 1) % points.size]
        val x1 = (p.lon - points[0].lon) * mPerDegLon
        val y1 = (p.lat - points[0].lat) * mPerDegLat
        val x2 = (q.lon - points[0].lon) * mPerDegLon
        val y2 = (q.lat - points[0].lat) * mPerDegLat
        sum += x1 * y2 - x2 * y1
    }
    return abs(sum) / 2.0
}

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.roundToInt()} м"
    meters < 10_000 -> String.format(RU, "%.2f км", meters / 1000)
    else -> String.format(RU, "%.1f км", meters / 1000)
}

fun formatArea(squareMeters: Double): String {
    val ga = squareMeters / 10_000.0
    return if (ga < 0.1) "${squareMeters.roundToInt()} м²" else String.format(RU, "%.2f га", ga)
}

/** "на северо-восток" — для подписи к стрелке "веди до делянки". */
fun bearingName(degrees: Double): String {
    val names = listOf("север", "северо-восток", "восток", "юго-восток", "юг", "юго-запад", "запад", "северо-запад")
    return names[(((degrees + 22.5) % 360) / 45).toInt().coerceIn(0, 7)]
}

fun formatCoordinates(p: LatLon): String = String.format(Locale.US, "%.6f, %.6f", p.lat, p.lon)

/** Точка внутри контура (любого из колец). */
fun MapShape.contains(lat: Double, lon: Double): Boolean {
    if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
    return rings.any { ringContains(it, lat, lon) }
}

private fun ringContains(ring: DoubleArray, lat: Double, lon: Double): Boolean {
    var inside = false
    val n = ring.size / 2
    var j = n - 1
    for (i in 0 until n) {
        val latI = ring[i * 2]
        val lonI = ring[i * 2 + 1]
        val latJ = ring[j * 2]
        val lonJ = ring[j * 2 + 1]
        if ((latI > lat) != (latJ > lat) && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI) inside = !inside
        j = i
    }
    return inside
}
