package com.lesovod.mobile.data.repository

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lesovod.mobile.BuildConfig
import com.lesovod.mobile.data.network.NetworkModule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class AppUpdateInfo(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("apk_url") val apkUrl: String,
)

/**
 * Тихая проверка обновлений сайдлоуд-сборки: при старте разово запрашивает статический
 * /app/version.json на том же хосте, что и API, и если там версия новее текущей сборки —
 * отдаёт её в [update] для баннера. Любая ошибка (нет сети, сервер недоступен, битый JSON)
 * проглатывается молча — состояние просто остаётся null, экран никогда не узнаёт, что
 * проверка вообще была.
 */
class UpdateChecker private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val _update = MutableStateFlow<AppUpdateInfo?>(null)
    val update: StateFlow<AppUpdateInfo?> = _update.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var downloadId: Long = -1L

    init {
        scope.launch { checkOnce() }
    }

    private fun checkOnce() {
        runCatching {
            val request = Request.Builder().url(VERSION_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val info = json.decodeFromString<AppUpdateInfo>(body)
                if (info.versionCode > BuildConfig.VERSION_CODE) {
                    _update.value = info
                }
            }
        }
    }

    /** Запускает скачивание APK системным DownloadManager; по завершении сам открывает установку. */
    fun startUpdate() {
        val info = _update.value ?: return
        if (_downloadState.value == DownloadState.Downloading) return

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Обновление «Лесовод»")
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        // Сначала enqueue (узнаём id), потом регистрируем приёмник — иначе он бы сверял
        // приходящий id с ещё не присвоенным downloadId.
        val newDownloadId = runCatching { downloadManager.enqueue(request) }.getOrNull()
        if (newDownloadId == null) {
            _downloadState.value = DownloadState.Failed
            return
        }
        downloadId = newDownloadId
        _downloadState.value = DownloadState.Downloading
        registerCompletionReceiver(downloadManager)
    }

    private fun registerCompletionReceiver(downloadManager: DownloadManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId == -1L || completedId != downloadId) return
                runCatching { appContext.unregisterReceiver(this) }
                onDownloadFinished(downloadManager)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun onDownloadFinished(downloadManager: DownloadManager) {
        val successful = runCatching {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            cursor?.use { c ->
                c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
            } ?: false
        }.getOrDefault(false)

        if (!successful) {
            _downloadState.value = DownloadState.Failed
            return
        }

        val file = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        _downloadState.value = DownloadState.ReadyToInstall
        installApk(file)
    }

    private fun installApk(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    sealed interface DownloadState {
        data object Idle : DownloadState
        data object Downloading : DownloadState
        data object ReadyToInstall : DownloadState
        data object Failed : DownloadState
    }

    companion object {
        private val VERSION_URL = NetworkModule.BASE_URL + "app/version.json"
        private const val APK_FILE_NAME = "lesovod-update.apk"

        @Volatile
        private var instance: UpdateChecker? = null

        fun getInstance(context: Context): UpdateChecker =
            instance ?: synchronized(this) {
                instance ?: UpdateChecker(context.applicationContext).also { instance = it }
            }
    }
}
