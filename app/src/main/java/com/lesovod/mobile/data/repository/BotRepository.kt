package com.lesovod.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.lesovod.mobile.data.network.ApiService
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.buildPhotoPart
import com.lesovod.mobile.data.network.dto.AttendanceMarkDto
import com.lesovod.mobile.data.network.dto.AttendanceMarkRequest
import com.lesovod.mobile.data.network.dto.AttendanceStatus
import com.lesovod.mobile.data.network.dto.BreakdownRequest
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.DelyankaLocationMatchDto
import com.lesovod.mobile.data.network.dto.GeoNoteCreateRequest
import com.lesovod.mobile.data.network.dto.InventarizatsiyaRequest
import com.lesovod.mobile.data.network.dto.NoteCreateRequest
import com.lesovod.mobile.data.network.dto.PerevodRequest
import com.lesovod.mobile.data.network.dto.NoteDto
import com.lesovod.mobile.data.network.dto.ProbaResponse
import com.lesovod.mobile.data.network.dto.ProbaSaveRequest
import com.lesovod.mobile.data.network.dto.RawReportRequest
import com.lesovod.mobile.data.network.dto.RecipientDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.network.dto.SentNoteDto
import com.lesovod.mobile.data.network.dto.TrelevkaRequest
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.network.extractErrorMessage
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.notifications.NotificationItem
import com.lesovod.mobile.ui.notifications.toNotificationItem
import com.lesovod.mobile.ui.notifications.toUnreadCount
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.ui.proba.toLesokulturyUchastok
import java.io.File
import java.io.IOException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class BotRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager,
) {
    private fun bearerToken(): String? = sessionManager.session.value?.token?.let { "Bearer $it" }

    suspend fun listDelyanki(kvartal: String): Result<List<DelyankaDto>> = safeCall {
        api.listDelyanki(requireToken(), kvartal)
    }

    /** Поиск делянки по кварталу/выделу — см. ApiService.getDelyankiByLocation. */
    suspend fun findDelyankaByLocation(kvartal: String, vydel: String): Result<List<DelyankaLocationMatchDto>> = safeCall {
        api.getDelyankiByLocation(kvartal, vydel)
    }

    suspend fun getRemaining(kvartal: String, vydel: String, lesoseka: String?): Result<RemainingResponseDto> = safeCall {
        api.getRemaining(requireToken(), kvartal, vydel, lesoseka?.takeIf { it.isNotBlank() })
    }

    suspend fun uploadPhoto(context: Context, uri: Uri): Result<String> = safeCall {
        val part = buildPhotoPart(context, uri)
        api.uploadPhoto(requireToken(), part).photoPath
    }

    /** Как [uploadPhoto], но для фото, ранее скопированного на диск при постановке действия в офлайн-очередь. */
    suspend fun uploadPhotoFile(file: File): Result<String> {
        if (!file.exists()) return Result.failure(Exception("Файл фото не найден"))
        return safeCall {
            val requestBody = file.readBytes().toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            api.uploadPhoto(requireToken(), part).photoPath
        }
    }

    suspend fun submitReport(
        tipRaboty: String,
        kvartal: String?,
        vydels: List<String>?,
        opisanie: String?,
        photoPath: String?,
        lat: Double? = null,
        lon: Double? = null,
        delyankaItemId: Int? = null,
    ): Result<Unit> {
        val telegramId = sessionManager.session.value?.appIdentity
            ?: return Result.failure(Exception("Не удалось определить учётную запись для отправки — переавторизуйтесь"))

        return safeCall {
            api.createReport(
                requireToken(),
                RawReportRequest(
                    telegramId = telegramId,
                    tipRaboty = tipRaboty,
                    kvartal = kvartal?.takeIf { it.isNotBlank() },
                    vydels = vydels?.takeIf { it.isNotEmpty() },
                    photoPath = photoPath,
                    opisanie = opisanie?.takeIf { it.isNotBlank() },
                    lat = lat,
                    lon = lon,
                    delyankaItemId = delyankaItemId,
                ),
            )
            Unit
        }
    }

    suspend fun submitBreakdown(detailText: String, photoPath: String?): Result<Unit> {
        val telegramId = sessionManager.session.value?.appIdentity
            ?: return Result.failure(Exception("Не удалось определить учётную запись для отправки — переавторизуйтесь"))

        return safeCall {
            api.createBreakdown(
                requireToken(),
                BreakdownRequest(
                    telegramId = telegramId,
                    detailText = detailText,
                    photoPath = photoPath,
                ),
            )
            Unit
        }
    }

    suspend fun submitAttendance(status: AttendanceStatus, lat: Double?, lon: Double?): Result<Unit> = safeCall {
        api.createAttendanceMark(requireToken(), AttendanceMarkRequest(status.wireValue, lat, lon))
        Unit
    }

    suspend fun getLatestAttendance(): Result<AttendanceMarkDto?> = safeCall {
        val raw = api.getLatestAttendanceMark(requireToken()).use { it.string() }.trim()
        if (raw.isEmpty() || raw == "null") null else NetworkModule.json.decodeFromString<AttendanceMarkDto>(raw)
    }

    suspend fun listWorkPlan(): Result<List<WorkPlanItemDto>> = safeCall {
        api.listWorkPlan(requireToken())
    }

    suspend fun completeWorkPlanItem(id: Int): Result<Boolean> = safeCall {
        api.completeWorkPlan(requireToken(), id).done
    }

    suspend fun submitTrelevka(otkuda: String, kuda: String, obyom: Double, delyankaItemId: Int?): Result<Unit> = safeCall {
        api.createTrelevka(requireToken(), TrelevkaRequest(otkuda, kuda, obyom, delyankaItemId))
        Unit
    }

    suspend fun listRecipients(): Result<List<RecipientDto>> = safeCall {
        api.listRecipients(requireToken())
    }

    suspend fun submitNote(text: String, recipientId: Int?): Result<Unit> = safeCall {
        api.createNote(requireToken(), NoteCreateRequest(text, recipientId))
        Unit
    }

    suspend fun listNotes(): Result<List<NoteDto>> = safeCall {
        api.listNotes(requireToken())
    }

    suspend fun listMyNotes(): Result<List<SentNoteDto>> = safeCall {
        api.listMyNotes(requireToken())
    }

    suspend fun submitGeoNote(lat: Double, lon: Double, noteText: String?, photoPath: String?): Result<Unit> = safeCall {
        api.createGeoNote(requireToken(), GeoNoteCreateRequest(lat, lon, noteText, photoPath))
        Unit
    }

    suspend fun submitProba(request: ProbaSaveRequest): Result<ProbaResponse> = safeCall {
        api.createProba(requireToken(), request)
    }

    suspend fun listLesokulturyUchastki(): Result<List<LesokulturyUchastok>> = safeCall {
        api.listLesokulturyUchastki(requireToken()).map { it.toLesokulturyUchastok() }
    }

    suspend fun submitInventarizatsiya(uchastokId: Int, request: InventarizatsiyaRequest): Result<JsonElement> = safeCall {
        api.createInventarizatsiya(requireToken(), uchastokId, request)
    }

    suspend fun submitPerevod(uchastokId: Int, request: PerevodRequest): Result<JsonElement> = safeCall {
        api.createPerevod(requireToken(), uchastokId, request)
    }

    /** Справочник пород — публичный, не требует токена. */
    suspend fun listPorody(): Result<List<String>> = safeCall {
        api.listPorody().porody.sorted()
    }

    suspend fun listNotifications(): Result<List<NotificationItem>> = safeCall {
        api.listNotifications(requireToken()).mapNotNull { it.toNotificationItem() }
    }

    suspend fun markNotificationRead(id: Int): Result<Unit> = safeCall {
        api.markNotificationRead(requireToken(), id)
        Unit
    }

    suspend fun getUnreadNotificationsCount(): Result<Int> = safeCall {
        api.getUnreadNotificationsCount(requireToken()).toUnreadCount()
    }

    suspend fun markAllNotificationsRead(): Result<Unit> = safeCall {
        api.markAllNotificationsRead(requireToken())
        Unit
    }

    private fun requireToken(): String =
        bearerToken() ?: error("Сессия истекла, войдите заново")

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: HttpException) {
            Result.failure(Exception(extractErrorMessage(e, "Ошибка сервера")))
        } catch (e: IOException) {
            // IOException прилетает и при настоящем отсутствии сети, и при прочих сетевых сбоях
            // (таймаут, TLS, временная недоступность сервера) — раньше все они одинаково
            // подписывались как «нет интернета», хотя соединение у пользователя было. Отличаем
            // по факту наличия сети на устройстве: только его отсутствие ставит действие в
            // офлайн-очередь, остальное — обычная ошибка запроса.
            if (sessionManager.hasActiveNetwork()) {
                Result.failure(Exception(e.message ?: "Не удалось связаться с сервером"))
            } else {
                Result.failure(ConnectivityException("Нет соединения с интернетом", e))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Не удалось связаться с сервером"))
        }
    }
}
