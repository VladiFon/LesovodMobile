package com.lesovod.mobile.data.local

import com.lesovod.mobile.data.network.dto.ProbaRowRequest
import kotlinx.serialization.Serializable

/** Виды действий, которые можно выполнить офлайн и отправить позже. */
enum class PendingActionType {
    REPORT, BREAKDOWN, ATTENDANCE, TASK_COMPLETE, TRELEVKA, PROBA, NOTE
}

/**
 * Действие, сохранённое на устройстве из-за отсутствия сети и ожидающее отправки.
 * [payload] — JSON конкретного типа (см. Pending*Payload ниже), разбирается по [type].
 * [photoLocalPath2] — второе фото для проб (в пробе оба фото обязательны).
 */
@Serializable
data class PendingAction(
    val id: String,
    val type: PendingActionType,
    val createdAt: Long,
    val payload: String,
    val photoLocalPath: String? = null,
    val photoLocalPath2: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Serializable
data class PendingReportPayload(
    val tipRaboty: String,
    val kvartal: String? = null,
    val vydels: List<String>? = null,
    val opisanie: String? = null,
    val photoPath: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class PendingBreakdownPayload(
    val detailText: String,
    val photoPath: String? = null,
)

@Serializable
data class PendingAttendancePayload(
    val statusWire: String,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class PendingTaskCompletePayload(
    val id: Int,
    val lesnichestvo: String? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
)

@Serializable
data class PendingTrelevkaPayload(
    val otkuda: String,
    val kuda: String,
    val obyom: Double,
    val delyankaItemId: Int? = null,
)

@Serializable
data class PendingProbaPayload(
    val kvartal: String,
    val vydel: String,
    val ploshadVydela: Double? = null,
    val dataZamera: String,
    val rows: List<ProbaRowRequest>,
    val kolPloshadok: Int,
    val ploshadPloshadki: Double,
    val lesokulturyUchastokIds: List<Int> = emptyList(),
    val fotoStolbDelyankiPath: String? = null,
    val fotoStolbProbyPath: String? = null,
)

@Serializable
data class PendingNotePayload(
    val text: String,
    val recipientId: Int? = null,
)
