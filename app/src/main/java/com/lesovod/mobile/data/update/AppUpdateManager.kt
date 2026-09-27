package com.lesovod.mobile.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.getSystemService
import com.lesovod.mobile.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Обновления вне Google Play: приложение проверяет последний GitHub Release репозитория
 * и предлагает скачать и установить APK, если он новее текущей версии. Отдельный
 * лёгкий OkHttpClient — не переиспользует [com.lesovod.mobile.data.network.NetworkModule],
 * у которого другой базовый адрес и перехватчик авторизации сервера ботов.
 */
class AppUpdateManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().build()
    private val prefs = appContext.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkForUpdate() {
        _state.value = UpdateState.Checking
        scope.launch {
            val result = runCatching { fetchLatestRelease() }
            val release = result.getOrNull()
            _state.value = when {
                result.isFailure -> UpdateState.Error(result.exceptionOrNull()?.message ?: "Не удалось проверить обновления")
                release == null -> UpdateState.UpToDate
                !isNewerVersion(release.tagName, BuildConfig.VERSION_NAME) -> UpdateState.UpToDate
                else -> {
                    val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
                    if (apkUrl == null) UpdateState.UpToDate
                    else UpdateState.Available(
                        UpdateInfo(versionName = release.tagName.removePrefix("v"), downloadUrl = apkUrl, notes = release.body.orEmpty()),
                    )
                }
            }
        }
    }

    private fun fetchLatestRelease(): GithubRelease? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LesovodMobile")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // релизов ещё нет
            if (!response.isSuccessful) error("GitHub вернул ${response.code}")
            val body = response.body?.string() ?: return null
            return json.decodeFromString<GithubRelease>(body)
        }
    }

    /** Есть ли право на установку из этого источника (Android 8+, `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`). */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    fun requestInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    /** Ставит APK в очередь системного [DownloadManager] — прогресс и повторы при обрыве связи он ведёт сам. */
    fun startDownload(info: UpdateInfo) {
        val manager = appContext.getSystemService<DownloadManager>() ?: return
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Лесовод ${info.versionName}")
            .setDescription("Загрузка обновления")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "lesovod-${info.versionName}.apk")
        val id = manager.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        _state.value = UpdateState.Downloading(info)
    }

    companion object {
        private const val GITHUB_OWNER = "VladiFon"
        private const val GITHUB_REPO = "LesovodMobile"
        private const val KEY_DOWNLOAD_ID = "download_id"

        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager =
            instance ?: synchronized(this) {
                instance ?: AppUpdateManager(context.applicationContext).also { instance = it }
            }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class UpdateInfo(val versionName: String, val downloadUrl: String, val notes: String)

/** Сравнение версий вида «1.2.0» / «v1.2.0» по-компонентно — семвер без суффиксов (-beta и т.п.). */
internal fun isNewerVersion(remoteTag: String, currentVersionName: String): Boolean {
    fun parse(v: String) = v.removePrefix("v").substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
    val remote = parse(remoteTag)
    val current = parse(currentVersionName)
    val len = maxOf(remote.size, current.size)
    for (i in 0 until len) {
        val r = remote.getOrElse(i) { 0 }
        val c = current.getOrElse(i) { 0 }
        if (r != c) return r > c
    }
    return false
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/**
 * Ловит завершение загрузки APK из [AppUpdateManager.startDownload] и открывает системную
 * установку — `DownloadManager.getUriForDownloadedFile` уже отдаёт `content://`-URI,
 * отдельный FileProvider не нужен.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
        val expectedId = prefs.getLong("download_id", -1L)
        if (completedId == -1L || completedId != expectedId) return

        val manager = context.getSystemService<DownloadManager>() ?: return
        val uri = runCatching { manager.getUriForDownloadedFile(completedId) }.getOrNull() ?: return
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(installIntent) }
    }
}
