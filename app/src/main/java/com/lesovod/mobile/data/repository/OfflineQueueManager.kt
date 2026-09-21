package com.lesovod.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.lesovod.mobile.data.local.CompletedWorkStore
import com.lesovod.mobile.data.local.PendingAction
import com.lesovod.mobile.data.local.PendingActionStore
import com.lesovod.mobile.data.local.PendingActionType
import com.lesovod.mobile.data.local.PendingAttendancePayload
import com.lesovod.mobile.data.local.PendingBreakdownPayload
import com.lesovod.mobile.data.local.PendingReportPayload
import com.lesovod.mobile.data.local.PendingTaskCompletePayload
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.ConnectivityObserver
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.AttendanceStatus
import com.lesovod.mobile.data.session.SessionManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Офлайн-очередь для действий бота (отчёт, поломка, отметка времени, задачи): пока нет сети,
 * действие сохраняется на устройстве и отправляется автоматически, как только связь появится.
 * Экраны узнают о постановке в очередь и об успешной отправке через [pending] и [completed].
 */
class OfflineQueueManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = PendingActionStore(appContext)
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(appContext))
    private val completedStore = CompletedWorkStore(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityObserver = ConnectivityObserver(appContext) { retryNow() }

    private val _pending = MutableStateFlow(store.list())
    /** Все действия, ожидающие отправки (любого типа), самое старое — первым. */
    val pending: StateFlow<List<PendingAction>> = _pending.asStateFlow()

    private val _completed = MutableSharedFlow<PendingAction>(extraBufferCapacity = 8)
    /** Эмитит действие сразу после того, как оно успешно ушло на сервер из очереди. */
    val completed: SharedFlow<PendingAction> = _completed.asSharedFlow()

    init {
        connectivityObserver.start()
        retryNow()
    }

    /** Пробует отправить очередь сейчас (например, по нажатию «Отправить сейчас»). */
    fun retryNow() {
        scope.launch { flush() }
    }

    suspend fun enqueueReport(
        tipRaboty: String,
        kvartal: String?,
        vydels: List<String>?,
        opisanie: String?,
        photoUri: Uri?,
        uploadedPhotoPath: String?,
    ) {
        val id = UUID.randomUUID().toString()
        val localPhotoPath = photoUri?.let { copyUriToLocalFile(id, it) }
        val payload = PendingReportPayload(tipRaboty, kvartal, vydels, opisanie, uploadedPhotoPath)
        enqueue(id, PendingActionType.REPORT, json.encodeToString(payload), localPhotoPath)
    }

    suspend fun enqueueBreakdown(detailText: String, photoUri: Uri?, uploadedPhotoPath: String?) {
        val id = UUID.randomUUID().toString()
        val localPhotoPath = photoUri?.let { copyUriToLocalFile(id, it) }
        val payload = PendingBreakdownPayload(detailText, uploadedPhotoPath)
        enqueue(id, PendingActionType.BREAKDOWN, json.encodeToString(payload), localPhotoPath)
    }

    fun enqueueAttendance(status: AttendanceStatus, lat: Double?, lon: Double?) {
        val payload = PendingAttendancePayload(status.wireValue, lat, lon)
        enqueue(UUID.randomUUID().toString(), PendingActionType.ATTENDANCE, json.encodeToString(payload), null)
    }

    fun enqueueTaskComplete(id: Int, lesnichestvo: String?, kvartal: String?, vydel: String?) {
        val payload = PendingTaskCompletePayload(id, lesnichestvo, kvartal, vydel)
        enqueue(UUID.randomUUID().toString(), PendingActionType.TASK_COMPLETE, json.encodeToString(payload), null)
    }

    private fun enqueue(id: String, type: PendingActionType, payload: String, photoLocalPath: String?) {
        store.add(
            PendingAction(
                id = id,
                type = type,
                createdAt = System.currentTimeMillis(),
                payload = payload,
                photoLocalPath = photoLocalPath,
            ),
        )
        _pending.value = store.list()
    }

    private suspend fun copyUriToLocalFile(id: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appContext.filesDir, "pending_photos").apply { mkdirs() }
            val file = File(dir, "$id.jpg")
            val input = appContext.contentResolver.openInputStream(uri) ?: return@runCatching null
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            file.absolutePath
        }.getOrNull()
    }

    private suspend fun flush() {
        mutex.withLock {
            for (action in store.list().sortedBy { it.createdAt }) {
                when (val outcome = process(action)) {
                    FlushOutcome.Sent -> {
                        store.remove(action.id)
                        action.photoLocalPath?.let { runCatching { File(it).delete() } }
                        _pending.value = store.list()
                        _completed.emit(action)
                    }
                    FlushOutcome.StillOffline -> {
                        return@withLock
                    }
                    is FlushOutcome.Failed -> {
                        store.update(action.copy(attempts = action.attempts + 1, lastError = outcome.message))
                        _pending.value = store.list()
                    }
                }
            }
        }
    }

    private suspend fun process(action: PendingAction): FlushOutcome = when (action.type) {
        PendingActionType.REPORT -> processReport(action)
        PendingActionType.BREAKDOWN -> processBreakdown(action)
        PendingActionType.ATTENDANCE -> processAttendance(action)
        PendingActionType.TASK_COMPLETE -> processTaskComplete(action)
    }

    private suspend fun processReport(action: PendingAction): FlushOutcome {
        val payload = decode<PendingReportPayload>(action.payload)
            ?: return FlushOutcome.Failed("Повреждённые данные действия")
        var photoPath = payload.photoPath
        if (photoPath == null && action.photoLocalPath != null) {
            when (val photoOutcome = uploadLocalPhoto(action.photoLocalPath)) {
                is PhotoOutcome.Uploaded -> {
                    photoPath = photoOutcome.path
                    store.update(action.copy(payload = json.encodeToString(payload.copy(photoPath = photoPath))))
                }
                PhotoOutcome.StillOffline -> return FlushOutcome.StillOffline
                is PhotoOutcome.Failed -> return FlushOutcome.Failed(photoOutcome.message)
            }
        }
        val result = repository.submitReport(payload.tipRaboty, payload.kvartal, payload.vydels, payload.opisanie, photoPath)
        return result.fold(onSuccess = { FlushOutcome.Sent }, onFailure = { toOutcome(it) })
    }

    private suspend fun processBreakdown(action: PendingAction): FlushOutcome {
        val payload = decode<PendingBreakdownPayload>(action.payload)
            ?: return FlushOutcome.Failed("Повреждённые данные действия")
        var photoPath = payload.photoPath
        if (photoPath == null && action.photoLocalPath != null) {
            when (val photoOutcome = uploadLocalPhoto(action.photoLocalPath)) {
                is PhotoOutcome.Uploaded -> {
                    photoPath = photoOutcome.path
                    store.update(action.copy(payload = json.encodeToString(payload.copy(photoPath = photoPath))))
                }
                PhotoOutcome.StillOffline -> return FlushOutcome.StillOffline
                is PhotoOutcome.Failed -> return FlushOutcome.Failed(photoOutcome.message)
            }
        }
        val result = repository.submitBreakdown(payload.detailText, photoPath)
        return result.fold(onSuccess = { FlushOutcome.Sent }, onFailure = { toOutcome(it) })
    }

    private suspend fun processAttendance(action: PendingAction): FlushOutcome {
        val payload = decode<PendingAttendancePayload>(action.payload)
            ?: return FlushOutcome.Failed("Повреждённые данные действия")
        val status = AttendanceStatus.fromWireValue(payload.statusWire)
            ?: return FlushOutcome.Failed("Неизвестный статус отметки")
        val result = repository.submitAttendance(status, payload.lat, payload.lon)
        return result.fold(onSuccess = { FlushOutcome.Sent }, onFailure = { toOutcome(it) })
    }

    private suspend fun processTaskComplete(action: PendingAction): FlushOutcome {
        val payload = decode<PendingTaskCompletePayload>(action.payload)
            ?: return FlushOutcome.Failed("Повреждённые данные действия")
        val result = repository.completeWorkPlanItem(payload.id)
        return result.fold(
            onSuccess = {
                if (!payload.kvartal.isNullOrBlank() && !payload.vydel.isNullOrBlank()) {
                    completedStore.add(payload.lesnichestvo, payload.kvartal, payload.vydel)
                }
                FlushOutcome.Sent
            },
            onFailure = { toOutcome(it) },
        )
    }

    private suspend fun uploadLocalPhoto(path: String): PhotoOutcome {
        val result = repository.uploadPhotoFile(File(path))
        return result.fold(
            onSuccess = { PhotoOutcome.Uploaded(it) },
            onFailure = { err ->
                if (err is ConnectivityException) PhotoOutcome.StillOffline
                else PhotoOutcome.Failed(err.message ?: "Не удалось загрузить фото")
            },
        )
    }

    private fun toOutcome(err: Throwable): FlushOutcome =
        if (err is ConnectivityException) FlushOutcome.StillOffline
        else FlushOutcome.Failed(err.message ?: "Не удалось отправить")

    private inline fun <reified T> decode(raw: String): T? = runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private sealed class FlushOutcome {
        data object Sent : FlushOutcome()
        data object StillOffline : FlushOutcome()
        data class Failed(val message: String) : FlushOutcome()
    }

    private sealed class PhotoOutcome {
        data class Uploaded(val path: String) : PhotoOutcome()
        data object StillOffline : PhotoOutcome()
        data class Failed(val message: String) : PhotoOutcome()
    }

    companion object {
        @Volatile
        private var instance: OfflineQueueManager? = null

        fun getInstance(context: Context): OfflineQueueManager =
            instance ?: synchronized(this) {
                instance ?: OfflineQueueManager(context).also { instance = it }
            }
    }
}
