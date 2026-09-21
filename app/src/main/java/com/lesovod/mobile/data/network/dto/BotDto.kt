package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawReportRequest(
    @SerialName("telegram_id") val telegramId: String,
    @SerialName("tip_raboty") val tipRaboty: String,
    val kvartal: String? = null,
    val vydels: List<String>? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    val opisanie: String? = null,
)

@Serializable
data class BreakdownRequest(
    @SerialName("telegram_id") val telegramId: String,
    @SerialName("detail_text") val detailText: String,
    @SerialName("photo_path") val photoPath: String? = null,
)

@Serializable
data class PhotoUploadResponseDto(
    @SerialName("photo_path") val photoPath: String,
)

@Serializable
data class DelyankaDto(
    val vydel: String,
    @SerialName("lesoseka_nomer") val lesosekaNomer: String? = null,
)

@Serializable
data class VolumeBreakdownDto(
    val limit: Double? = null,
    @SerialName("fakt_naryad") val faktNaryad: Double? = null,
    @SerialName("fakt_egais") val faktEgais: Double? = null,
    @SerialName("ostatok_safe") val ostatokSafe: Double? = null,
)

@Serializable
data class PorodaRemainingDto(
    val delovaya: VolumeBreakdownDto? = null,
    val drova: VolumeBreakdownDto? = null,
)

@Serializable
data class RemainingResponseDto(
    val found: Boolean = false,
    @SerialName("item_ids") val itemIds: List<Int>? = null,
    val grouped: Map<String, PorodaRemainingDto>? = null,
    @SerialName("last_update") val lastUpdate: String? = null,
    @SerialName("egais_imported_at") val egaisImportedAt: String? = null,
)

/**
 * ВНИМАНИЕ: точные имена полей не были даны в задаче (только путь POST /api/bot/trelevka
 * и смысл формы — делянка необязательна, откуда/куда/объём обязательны). Названы по аналогии
 * с остальными полями бота (kvartal/vydel как в RawReportRequest) — нужно сверить с реальной
 * Pydantic-схемой на бэкенде перед продакшеном.
 */
@Serializable
data class TrelevkaRequest(
    val kvartal: String? = null,
    val vydel: String? = null,
    val otkuda: String,
    val kuda: String,
    val obyom: Double,
)

/**
 * ВНИМАНИЕ: поле получателя (`recipient_sotrudnik_id`) названо явно в задаче, остальные —
 * по аналогии с другими DTO бота, сверить с реальной схемой на бэкенде.
 */
@Serializable
data class NoteCreateRequest(
    val text: String,
    @SerialName("recipient_sotrudnik_id") val recipientSotrudnikId: Int? = null,
)

@Serializable
data class RecipientDto(
    val id: Int,
    val fio: String,
)

@Serializable
data class NoteDto(
    val id: Int,
    val text: String,
    @SerialName("author_fio") val authorFio: String? = null,
    @SerialName("created_at") val createdAt: String,
)
