package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class GeoJsonFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<GeoJsonFeature> = emptyList(),
)

@Serializable
data class GeoJsonFeature(
    val type: String = "Feature",
    val geometry: GeoJsonGeometry,
    val properties: JsonObject = JsonObject(emptyMap()),
)

/** coordinates разбирается отдельно (toOuterRings в ui/map) — вложенность разная для Polygon/MultiPolygon. */
@Serializable
data class GeoJsonGeometry(
    val type: String,
    val coordinates: JsonElement,
)
