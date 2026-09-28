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
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("delyanka_id") val delyankaId: Int? = null,
    @SerialName("lesokultury_uchastok_id") val lesokulturyUchastokId: Int? = null,
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

/** Подтверждённая схема сервера: { otkuda, kuda, obyom, delyanka_item_id }. */
@Serializable
data class TrelevkaRequest(
    val otkuda: String,
    val kuda: String,
    val obyom: Double,
    @SerialName("delyanka_item_id") val delyankaItemId: Int? = null,
)

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
    /** Сервер отдаёт автора заметки как sotrudnik_fio (webext.list_worker_notes). */
    @SerialName("sotrudnik_fio") val authorFio: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** "Мои отправленные" (GET /api/bot/notes/mine) — тот же журнал заметок, что и входящие у мастера. */
@Serializable
data class SentNoteDto(
    val id: Int,
    val text: String,
    @SerialName("recipient_fio") val recipientFio: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Тело POST /api/bot/geo-notes. telegram_id обязателен на бэкенде (GeoNoteIn в
 * app/routers/bot.py) — без него FastAPI отвечает 422 ещё до вызова обработчика. */
@Serializable
data class GeoNoteCreateRequest(
    @SerialName("telegram_id") val telegramId: String,
    val lat: Double,
    val lon: Double,
    @SerialName("note_text") val noteText: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    /** Тип метки (ветровал, пожар, …) — коды см. GeoNoteCategory. */
    val kategoriya: String? = null,
)
