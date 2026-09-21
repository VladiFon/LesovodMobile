package com.lesovod.mobile.ui.map

import android.graphics.Color as AndroidColor

enum class ShapeKind { KVARTAL, VYDEL, LESOSEKA }

/**
 * Готовая к рисованию геометрия: кольца лежат плоскими массивами [lat0, lon0, lat1, lon1, …],
 * границы и точка подписи посчитаны заранее — при отрисовке и тапе ничего не разбирается заново.
 */
class MapShape(
    val kind: ShapeKind,
    val kvartal: String,
    val vydel: String?,
    val statusColor: Int?,
    val rings: List<DoubleArray>,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    val labelLat: Double get() = (minLat + maxLat) / 2
    val labelLon: Double get() = (minLon + maxLon) / 2
    val label: String get() = if (kind == ShapeKind.KVARTAL) kvartal else vydel.orEmpty()

    /** Один и тот же выдел может прийти из двух соседних ячеек — по этому ключу склеиваем. */
    val key: String = "$kind|$kvartal|$vydel|${rings.size}|${rings.firstOrNull()?.size}"
}

fun buildShape(kind: ShapeKind, kvartal: String, vydel: String?, statusColor: Int?, rings: List<DoubleArray>): MapShape? {
    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    var any = false
    for (ring in rings) {
        var i = 0
        while (i + 1 < ring.size) {
            val lat = ring[i]
            val lon = ring[i + 1]
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            any = true
            i += 2
        }
    }
    return if (any) MapShape(kind, kvartal, vydel, statusColor, rings, minLat, maxLat, minLon, maxLon) else null
}

/** Общая для потокового парсера логика: какие свойства GeoJSON что означают для каждого слоя. */
fun shapeFromProperties(kind: ShapeKind, prop: (String) -> String?, rawNumVds: String?, rings: List<DoubleArray>): MapShape? {
    if (rings.isEmpty()) return null
    return when (kind) {
        ShapeKind.KVARTAL -> buildShape(kind, prop("num_kv") ?: return null, null, null, rings)
        ShapeKind.VYDEL -> {
            val kv = prop("num_kv") ?: return null
            val vd = prop("num_vd") ?: return null
            val color = prop("status_color")?.let { runCatching { AndroidColor.parseColor(it) }.getOrNull() }
            buildShape(kind, kv, vd, color, rings)
        }
        ShapeKind.LESOSEKA -> {
            // колонка vydel у моста ГИСлесхоз не заполняется — реальный номер (первый из списка) лежит в raw.num_vds
            val kv = prop("kvartal") ?: prop("num_kv") ?: return null
            val vd = prop("vydel")?.takeIf { it.isNotBlank() } ?: rawNumVds?.substringBefore(',')?.trim()
            if (vd.isNullOrBlank()) return null
            buildShape(kind, kv, vd, null, rings)
        }
    }
}

/** "Рисуй делянку по прямоугольнику выдела как временную замену" — пока мост ГИСлесхоз ничего не прислал. */
fun fallbackLesosekiFrom(vydela: List<MapShape>): List<MapShape> = vydela.mapNotNull { v ->
    val vd = v.vydel
    if (v.statusColor == null || vd == null) return@mapNotNull null
    val ring = doubleArrayOf(
        v.maxLat, v.minLon, v.maxLat, v.maxLon, v.minLat, v.maxLon, v.minLat, v.minLon, v.maxLat, v.minLon,
    )
    buildShape(ShapeKind.LESOSEKA, v.kvartal, vd, null, listOf(ring))
}
