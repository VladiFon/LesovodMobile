package com.lesovod.mobile.data.network

import com.lesovod.mobile.data.network.dto.AttendanceMarkDto
import com.lesovod.mobile.data.network.dto.AttendanceMarkRequest
import com.lesovod.mobile.data.network.dto.BreakdownRequest
import com.lesovod.mobile.data.network.dto.CompleteWorkPlanResponseDto
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.DelyankaMapRefDto
import com.lesovod.mobile.data.network.dto.GeoJsonFeatureCollection
import com.lesovod.mobile.data.network.dto.GeoNoteCreateRequest
import com.lesovod.mobile.data.network.dto.GeoNoteDto
import com.lesovod.mobile.data.network.dto.LesokulturyMapDto
import com.lesovod.mobile.data.network.dto.MapSearchResultDto
import com.lesovod.mobile.data.network.dto.SkladDto
import com.lesovod.mobile.data.network.dto.TrackCreateRequest
import com.lesovod.mobile.data.network.dto.TrackCreatedDto
import com.lesovod.mobile.data.network.dto.VydelHistoryDto
import com.lesovod.mobile.data.network.dto.VydelLocationDto
import com.lesovod.mobile.data.network.dto.WorkColorsDto
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
import com.lesovod.mobile.data.network.dto.TabelBrigadaDto
import com.lesovod.mobile.data.network.dto.TabelDayEntryDto
import com.lesovod.mobile.data.network.dto.TabelDelyankaDto
import com.lesovod.mobile.data.network.dto.TabelDaySaveRequest
import com.lesovod.mobile.data.network.dto.TabelLesokulturyUchastokDto
import com.lesovod.mobile.data.network.dto.TrelevkaRequest
import com.lesovod.mobile.data.network.dto.WorkPlanItemDto
import com.lesovod.mobile.data.network.dto.VidRabotyCreateRequest
import com.lesovod.mobile.data.network.dto.VidRabotyDto
import com.lesovod.mobile.data.network.dto.WorkerLoginRequest
import com.lesovod.mobile.ui.proba.LesokulturyUchastokDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    // Со слешем на конце: без него сервер отвечал 307-редиректом на http://,
    // Android его блокировал и экран показывал «Нет соединения с интернетом».
    @GET("api/notes/")
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


    @POST("api/uhody/proby")
    suspend fun createProba(
        @Header("Authorization") bearerToken: String,
        @Body body: ProbaSaveRequest,
    ): ProbaResponse

    @GET("api/uhody/proby/mine")
    suspend fun listMyProby(
        @Header("Authorization") bearerToken: String,
    ): List<ProbaResponse>

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

    @GET("api/notifications/")
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

    @GET("api/taxation/vydel")
    suspend fun getVydelCard(
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
        @Query("lesnichestvo") lesnichestvo: String? = null,
    ): JsonObject

    // Карта: метки, цвета, поиск, история, склады, треки (28.09.2026) — все с токеном рабочего.
    @GET("api/bot/geo-notes")
    suspend fun listMyGeoNotes(@Header("Authorization") bearerToken: String): List<GeoNoteDto>

    @DELETE("api/bot/geo-notes/{id}")
    suspend fun deleteGeoNote(@Header("Authorization") bearerToken: String, @Path("id") id: Int): ResponseBody

    @GET("api/map/work-colors")
    suspend fun getWorkColors(
        @Header("Authorization") bearerToken: String,
        @Query("lesnichestvo_num") lesnichestvoNum: String,
    ): WorkColorsDto

    @GET("api/map/search")
    suspend fun searchMap(
        @Header("Authorization") bearerToken: String,
        @Query("q") q: String,
        @Query("lesnichestvo_num") lesnichestvoNum: String? = null,
    ): List<MapSearchResultDto>

    @GET("api/map/lesokultury")
    suspend fun getLesokulturyForMap(
        @Header("Authorization") bearerToken: String,
        @Query("lesnichestvo_num") lesnichestvoNum: String? = null,
    ): List<LesokulturyMapDto>

    @GET("api/map/vydel-history")
    suspend fun getVydelHistory(
        @Header("Authorization") bearerToken: String,
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
        @Query("lesnichestvo_num") lesnichestvoNum: String? = null,
    ): VydelHistoryDto

    @GET("api/map/delyanka-location")
    suspend fun getVydelLocation(
        @Query("lesnichestvo_num") lesnichestvoNum: String,
        @Query("kvartal") kvartal: String,
        @Query("vydel") vydel: String,
    ): VydelLocationDto

    @GET("api/map/sklady")
    suspend fun listSklady(): List<SkladDto>

    @POST("api/bot/tracks")
    suspend fun createTrack(
        @Header("Authorization") bearerToken: String,
        @Body body: TrackCreateRequest,
    ): TrackCreatedDto

    // Табель — ручной ввод (app/routers/tabel.py): require_office_or_master, тот же круг
    // ролей, что и у остальных экранов руководителей мобильного приложения.
    @GET("api/tabel/day")
    suspend fun getTabelDay(
        @Header("Authorization") bearerToken: String,
        @Query("data") data: String,
    ): List<TabelDayEntryDto>

    @POST("api/tabel/day")
    suspend fun saveTabelDay(
        @Header("Authorization") bearerToken: String,
        @Body body: TabelDaySaveRequest,
    ): List<TabelDayEntryDto>

    @GET("api/tabel/vidy-rabot")
    suspend fun listVidyRabot(
        @Header("Authorization") bearerToken: String,
    ): List<VidRabotyDto>

    @POST("api/tabel/vidy-rabot")
    suspend fun createVidRaboty(
        @Header("Authorization") bearerToken: String,
        @Body body: VidRabotyCreateRequest,
    ): VidRabotyDto

    @GET("api/tabel/delyanki")
    suspend fun searchTabelDelyanki(
        @Header("Authorization") bearerToken: String,
        @Query("search") search: String? = null,
    ): List<TabelDelyankaDto>

    @GET("api/tabel/brigady")
    suspend fun listTabelBrigady(
        @Header("Authorization") bearerToken: String,
        @Query("data") data: String,
    ): List<TabelBrigadaDto>

    @GET("api/tabel/lesokultury-uchastki")
    suspend fun listTabelLesokulturyUchastki(
        @Header("Authorization") bearerToken: String,
        @Query("search") search: String? = null,
    ): List<TabelLesokulturyUchastokDto>
}
