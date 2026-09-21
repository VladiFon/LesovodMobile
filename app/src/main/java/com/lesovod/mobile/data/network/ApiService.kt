package com.lesovod.mobile.data.network

import com.lesovod.mobile.data.network.dto.AttendanceMarkDto
import com.lesovod.mobile.data.network.dto.AttendanceMarkRequest
import com.lesovod.mobile.data.network.dto.BreakdownRequest
import com.lesovod.mobile.data.network.dto.CompleteWorkPlanResponseDto
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.network.dto.GeoJsonFeatureCollection
import com.lesovod.mobile.data.network.dto.LoginResponseDto
import com.lesovod.mobile.data.network.dto.NoteCreateRequest
import com.lesovod.mobile.data.network.dto.NoteDto
import com.lesovod.mobile.data.network.dto.PhotoUploadResponseDto
import com.lesovod.mobile.data.network.dto.ProbaResponse
import com.lesovod.mobile.data.network.dto.ProbaSaveRequest
import com.lesovod.mobile.data.network.dto.RawReportRequest
import com.lesovod.mobile.data.network.dto.RecipientDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.network.dto.TrelevkaRequest
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.network.dto.WorkerLoginRequest
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {
    @POST("api/auth/worker-login")
    suspend fun workerLogin(@Body body: WorkerLoginRequest): LoginResponseDto

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") bearerToken: String): ResponseBody

    @GET("api/bot/delyanki")
    suspend fun listDelyanki(
        @Header("Authorization") bearerToken: String,
        @Query("kvartal") kvartal: String,
    ): List<DelyankaDto>

    @GET("api/bot/remaining")
    suspend fun getRemaining(
        @Header("Authorization") bearerToken: String,
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
        @Query("lesoseka") lesoseka: String? = null,
    ): RemainingResponseDto

    @Multipart
    @POST("api/bot/photo")
    suspend fun uploadPhoto(
        @Header("Authorization") bearerToken: String,
        @Part file: MultipartBody.Part,
    ): PhotoUploadResponseDto

    @POST("api/bot/reports")
    suspend fun createReport(
        @Header("Authorization") bearerToken: String,
        @Body body: RawReportRequest,
    ): ResponseBody

    @POST("api/bot/breakdowns")
    suspend fun createBreakdown(
        @Header("Authorization") bearerToken: String,
        @Body body: BreakdownRequest,
    ): ResponseBody

    @POST("api/bot/attendance")
    suspend fun createAttendanceMark(
        @Header("Authorization") bearerToken: String,
        @Body body: AttendanceMarkRequest,
    ): ResponseBody

    @GET("api/bot/attendance/latest")
    suspend fun getLatestAttendanceMark(
        @Header("Authorization") bearerToken: String,
    ): AttendanceMarkDto?

    @GET("api/bot/work-plan")
    suspend fun listWorkPlan(
        @Header("Authorization") bearerToken: String,
    ): List<WorkPlanItemDto>

    @POST("api/bot/work-plan/{work_plan_id}/complete")
    suspend fun completeWorkPlan(
        @Header("Authorization") bearerToken: String,
        @Path("work_plan_id") workPlanId: Int,
    ): CompleteWorkPlanResponseDto

    @POST("api/bot/trelevka")
    suspend fun createTrelevka(
        @Header("Authorization") bearerToken: String,
        @Body body: TrelevkaRequest,
    ): ResponseBody

    @GET("api/bot/recipients")
    suspend fun listRecipients(
        @Header("Authorization") bearerToken: String,
    ): List<RecipientDto>

    @POST("api/bot/notes")
    suspend fun createNote(
        @Header("Authorization") bearerToken: String,
        @Body body: NoteCreateRequest,
    ): ResponseBody

    @GET("api/notes")
    suspend fun listNotes(
        @Header("Authorization") bearerToken: String,
    ): List<NoteDto>

    @POST("api/uhody/proby")
    suspend fun createProba(
        @Header("Authorization") bearerToken: String,
        @Body body: ProbaSaveRequest,
    ): ProbaResponse

    // Карта и таксация (app/routers/map.py, app/routers/taxation.py) — публичные,
    // Authorization не требуют (см. докстринг app/auth.py про Этап 1 роутеры).
    @GET("api/map/lesnichestva")
    suspend fun listLesnichestva(): Map<String, Int>

    @GET("api/map/kvartaly")
    suspend fun getKvartalyLayer(
        @Query("lesnichestvo_num") lesnichestvoNum: String,
        @Query("bbox") bbox: String? = null,
        @Query("zoom") zoom: Double? = null,
    ): GeoJsonFeatureCollection

    @GET("api/map/vydela")
    suspend fun getVydelaLayer(
        @Query("lesnichestvo_num") lesnichestvoNum: String,
        @Query("bbox") bbox: String? = null,
        @Query("zoom") zoom: Double? = null,
    ): GeoJsonFeatureCollection

    @GET("api/map/import-layers")
    suspend fun getImportLayers(@Query("lesnichestvo_num") lesnichestvoNum: String? = null): GeoJsonFeatureCollection

    // Те же слои, но сырым телом: их кладём в дисковый кэш как есть и разбираем один раз.
    @Streaming
    @GET("api/map/kvartaly")
    suspend fun getKvartalyRaw(
        @Query("lesnichestvo_num") lesnichestvoNum: String,
        @Query("bbox") bbox: String? = null,
        @Query("zoom") zoom: Double? = null,
    ): ResponseBody

    @Streaming
    @GET("api/map/vydela")
    suspend fun getVydelaRaw(
        @Query("lesnichestvo_num") lesnichestvoNum: String,
        @Query("bbox") bbox: String? = null,
        @Query("zoom") zoom: Double? = null,
    ): ResponseBody

    @Streaming
    @GET("api/map/import-layers")
    suspend fun getImportLayersRaw(@Query("lesnichestvo_num") lesnichestvoNum: String? = null): ResponseBody

    // Делянки: где на карте они заведены и данные по конкретной делянке (лесосеке из документа МДО)
    @GET("api/delyanki/for-map")
    suspend fun getDelyankiForMap(): List<DelyankaMapRefDto>

    @GET("api/delyanki/{id}")
    suspend fun getDelyanka(@Path("id") id: Int): JsonObject

    @GET("api/taxation/vydel")
    suspend fun getVydelCard(
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
        @Query("lesnichestvo") lesnichestvo: String? = null,
    ): JsonObject
}
