package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttendanceMarkRequest(
    val status: String,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class AttendanceMarkDto(
    val id: Int,
    val status: String,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Ровно три значения, которые принимает POST /api/bot/attendance
 * (backend/app/routers/bot.py: AttendanceMarkIn.status — Literal с теми
 * же тремя строками) — значения нужно менять синхронно на обеих сторонах.
 */
enum class AttendanceStatus(val wireValue: String, val displayName: String) {
    WORKING("работаю", "Работаю"),
    NOT_WORKING("не работаю", "Не работаю"),
    SICK_LEAVE("больничный", "Больничный");

    companion object {
        fun fromWireValue(value: String?): AttendanceStatus? = entries.firstOrNull { it.wireValue == value }
    }
}
