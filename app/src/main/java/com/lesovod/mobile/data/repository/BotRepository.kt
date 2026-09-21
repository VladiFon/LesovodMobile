package com.lesovod.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.lesovod.mobile.data.network.ApiService
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.buildPhotoPart
import com.lesovod.mobile.data.network.dto.AttendanceMarkDto
import com.lesovod.mobile.data.network.dto.AttendanceMarkRequest
import com.lesovod.mobile.data.network.dto.AttendanceStatus
import com.lesovod.mobile.data.network.dto.BreakdownRequest
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.RawReportRequest
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.network.extractErrorMessage
import com.lesovod.mobile.data.session.SessionManager
import java.io.File
import java.io.IOException
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
        api.getLatestAttendanceMark(requireToken())
    }

    suspend fun listWorkPlan(): Result<List<WorkPlanItemDto>> = safeCall {
        api.listWorkPlan(requireToken())
    }

    suspend fun completeWorkPlanItem(id: Int): Result<Boolean> = safeCall {
        api.completeWorkPlan(requireToken(), id).done
    }

    private fun requireToken(): String =
        bearerToken() ?: error("Сессия истекла, войдите заново")

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: HttpException) {
            Result.failure(Exception(extractErrorMessage(e, "Ошибка сервера")))
        } catch (e: IOException) {
            // Сбой на уровне соединения (нет сети, DNS, таймаут), а не ответ сервера с ошибкой —
            // по этому типу вызывающая сторона решает поставить действие в офлайн-очередь.
            Result.failure(ConnectivityException("Нет соединения с интернетом", e))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Не удалось связаться с сервером"))
        }
    }
}
