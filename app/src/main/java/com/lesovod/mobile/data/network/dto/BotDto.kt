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
