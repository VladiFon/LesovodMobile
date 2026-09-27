package com.lesovod.mobile.data.network

import com.lesovod.mobile.data.network.dto.AttendanceMarkDto
import com.lesovod.mobile.data.network.dto.AttendanceMarkRequest
import com.lesovod.mobile.data.network.dto.BreakdownRequest
import com.lesovod.mobile.data.network.dto.CompleteWorkPlanResponseDto
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.DelyankaLocationMatchDto
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.network.dto.GeoJsonFeatureCollection
import com.lesovod.mobile.data.network.dto.GeoNoteCreateRequest
import com.lesovod.mobile.data.network.dto.InventarizatsiyaRequest
import com.lesovod.mobile.data.network.dto.LoginResponseDto
import com.lesovod.mobile.data.network.dto.PerevodRequest
import com.lesovod.mobile.data.network.dto.NoteCreateRequest
import com.lesovod.mobile.data.network.dto.NoteDto
import com.lesovod.mobile.data.network.dto.PhotoUploadResponseDto
import com.lesovod.mobile.data.network.dto.PorodySpravochnikDto
import com.lesovod.mobile.data.network.dto.ProbaResponse
import com.lesovod.mobile.data.network.dto.ProbaSaveRequest
import com.lesovod.mobile.data.network.dto.RawReportRequest
import com.lesovod.mobile.data.network.dto.RecipientDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.network.dto.SentNoteDto
import com.lesovod.mobile.data.network.dto.TrelevkaRequest
import com.lesovod.mobile.data.network.dto.VydelLocationDto
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.network.dto.WorkerLoginRequest
import com.lesovod.mobile.ui.proba.LesokulturyUchastokDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
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

    // Возвращает "null" как валидное тело ответа, когда отметок ещё не было — тип-конвертер
    // на базе kotlinx.serialization не различает Kotlin-nullability у стёртого Java Type
    // suspend-функции и падает на литерале null, поэтому парсим тело вручную (см. BotRepository).
    @GET("api/bot/attendance/latest")
    suspend fun getLatestAttendanceMark(
        @Header("Authorization") bearerToken: String,
    ): ResponseBody

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

    @GET("api/bot/notes/mine")
    suspend fun listMyNotes(
        @Header("Authorization") bearerToken: String,
    ): List<SentNoteDto>

    @POST("api/bot/geo-notes")
    suspend fun createGeoNote(
        @Header("Authorization") bearerToken: String,
        @Body body: GeoNoteCreateRequest,
    ): ResponseBody

    // НЕ api/map/geo-notes.geojson — тот защищён общим сервисным токеном QGIS-моста
    // (?token=...), которого у мобильного клиента нет и быть не должно. Здесь —
    // отдельный эндпоинт с обычной Bearer-авторизацией рабочего (см. app/routers/bot.py).
    @GET("api/bot/geo-notes.geojson")
    suspend fun getGeoNotesGeoJson(
        @Header("Authorization") bearerToken: String,
    ): GeoJsonFeatureCollection

    @POST("api/uhody/proby")
    suspend fun createProba(
        @Header("Authorization") bearerToken: String,
        @Body body: ProbaSaveRequest,
    ): ProbaResponse

    @GET("api/lesokultury/uchastki")
    suspend fun listLesokulturyUchastki(
        @Header("Authorization") bearerToken: String,
    ): List<LesokulturyUchastokDto>

    @POST("api/lesokultury/{uchastok_id}/inventarizatsiya")
    suspend fun createInventarizatsiya(
        @Header("Authorization") bearerToken: String,
        @Path("uchastok_id") uchastokId: Int,
        @Body body: InventarizatsiyaRequest,
    ): JsonElement

    @POST("api/lesokultury/{uchastok_id}/perevod")
    suspend fun createPerevod(
        @Header("Authorization") bearerToken: String,
        @Path("uchastok_id") uchastokId: Int,
        @Body body: PerevodRequest,
    ): JsonElement

    // Справочник пород (app/routers/uhody.py) — публичный, как остальные справочники карты.
    @GET("api/uhody/porody")
    suspend fun listPorody(): PorodySpravochnikDto

    @GET("api/notifications")
    suspend fun listNotifications(
        @Header("Authorization") bearerToken: String,
    ): List<JsonObject>

    @PATCH("api/notifications/{id}")
    suspend fun markNotificationRead(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: Int,
        @Body body: JsonObject = JsonObject(emptyMap()),
    ): ResponseBody

    @GET("api/notifications/unread-count")
    suspend fun getUnreadNotificationsCount(
        @Header("Authorization") bearerToken: String,
    ): JsonElement

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsRead(
        @Header("Authorization") bearerToken: String,
        @Body body: JsonObject = JsonObject(emptyMap()),
    ): ResponseBody

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

    // Поиск делянки (delyanka_item) по кварталу/выделу — рабочий находит её id так,
    // не подбирая число вручную (см. TrelevkaScreen/WorkReportScreen). Без Authorization,
    // как и остальные /api/delyanki/* — эндпоинт публичный (см. getDelyankiForMap выше).
    @GET("api/delyanki/by-location")
    suspend fun getDelyankiByLocation(
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
        @Query("lesnichestvo") lesnichestvo: String? = null,
    ): List<DelyankaLocationMatchDto>

    @GET("api/taxation/vydel")
    suspend fun getVydelCard(
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
        @Query("lesnichestvo") lesnichestvo: String? = null,
    ): JsonObject

    // Координаты выдела по кварталу/выделу — чтобы карта могла перелететь к найденному участку
    // (поиск делянки/квартала/выдела на экране "Карта"). Без Authorization, как и остальные
    // /api/map/* и /api/delyanki/* эндпоинты чтения.
    @GET("api/map/delyanka-location")
    suspend fun getDelyankaLocation(
        @Query("lesnichestvo_num") lesnichestvoNum: String,
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
    ): VydelLocationDto
}
