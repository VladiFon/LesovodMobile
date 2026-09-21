package com.lesovod.mobile.ui.map

import com.lesovod.mobile.data.network.dto.GeoJsonGeometry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/**
 * Только внешние кольца (без дырок) каждого полигона — достаточно, чтобы
 * показать контур на телефоне, не усложняя рендер вырезанными внутренними
 * "окнами", которые в лесных выделах редки и малы.
 */
fun GeoJsonGeometry.toOuterRings(): List<List<GeoPoint>> = when (type) {
    "Polygon" -> {
        val outer = coordinates.jsonArray.firstOrNull()?.jsonArray
        if (outer == null) emptyList() else listOf(outer.toGeoPoints())
    }
    "MultiPolygon" -> coordinates.jsonArray.mapNotNull { polygon ->
        polygon.jsonArray.firstOrNull()?.jsonArray?.toGeoPoints()
    }
    else -> emptyList()
}

private fun JsonArray.toGeoPoints(): List<GeoPoint> = map { point ->
    val coords = point.jsonArray
    val lon = coords[0].jsonPrimitive.double
    val lat = coords[1].jsonPrimitive.double
    GeoPoint(lat, lon)
}

fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Ограничивающий прямоугольник контура — временная замена настоящей лесосеки, пока мост её не прислал. */
fun List<List<GeoPoint>>.boundingBoxOrNull(): BoundingBox? {
    val allPoints = flatten()
    if (allPoints.isEmpty()) return null
    return BoundingBox.fromGeoPoints(allPoints)
}

fun BoundingBox.toRingPoints(): List<GeoPoint> = listOf(
    GeoPoint(latNorth, lonWest),
    GeoPoint(latNorth, lonEast),
    GeoPoint(latSouth, lonEast),
    GeoPoint(latSouth, lonWest),
    GeoPoint(latNorth, lonWest),
)
