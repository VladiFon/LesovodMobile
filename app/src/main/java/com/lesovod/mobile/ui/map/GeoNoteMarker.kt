package com.lesovod.mobile.ui.map

import kotlinx.serialization.Serializable

/** Метка рабочего на карте (POST /api/bot/geo-notes) — точка с необязательным текстом и/или фото. */
@Serializable
data class GeoNoteMarker(
    val lat: Double,
    val lon: Double,
    val noteText: String? = null,
    val photoPath: String? = null,
    val authorFio: String? = null,
    val createdAt: String? = null,
)
