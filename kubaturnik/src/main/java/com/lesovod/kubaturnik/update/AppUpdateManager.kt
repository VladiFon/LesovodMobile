package com.lesovod.kubaturnik.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.getSystemService
import com.lesovod.kubaturnik.BuildConfig
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
 * Обновления Кубатурника и установка основного «Лесовода» — оба APK лежат в GitHub Releases
 * репозитория LesovodMobile:
 *  - Кубатурник — релизы с тегом `kubaturnik-vX.Y.Z` (не «latest»), берём самый свежий такой;
 *  - Лесовод — `releases/latest` (тег `vX.Y.Z`).
 * Скачивание идёт через системный DownloadManager, по завершении [UpdateDownloadReceiver]
 * открывает установку.
 */
class AppUpdateManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().build()
    private val prefs = appContext.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _lesovodInstalled = MutableStateFlow(isLesovodInstalled())
    val lesovodInstalled: StateFlow<Boolean> = _lesovodInstalled.asStateFlow()

    private val _lesovodDownloading = MutableStateFlow(false)
    val lesovodDownloading: StateFlow<Boolean> = _lesovodDownloading.asStateFlow()

    /** Проверка при каждом возврате в приложение: человек мог как раз поставить Лесовод. */
    fun refreshLesovodInstalled() {
        val installed = isLesovodInstalled()
        _lesovodInstalled.value = installed
        if (installed) _lesovodDownloading.value = false
    }

    fun checkForUpdate() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            val result = runCatching { fetchKubaturnikRelease() }
            val release = result.getOrNull()
            _state.value = when {
                result.isFailure -> UpdateState.Error(result.exceptionOrNull()?.message ?: "Не удалось проверить обновления")
                release == null -> UpdateState.UpToDate
                !isNewerVersion(release.tagName.removePrefix(KUBATURNIK_TAG_PREFIX), BuildConfig.VERSION_NAME) -> UpdateState.UpToDate
                else -> {
                    val apkUrl = release.apkUrl()
                    if (apkUrl == null) {
                        UpdateState.UpToDate
                    } else {
                        UpdateState.Available(
                            UpdateInfo(
                                versionName = release.tagName.removePrefix(KUBATURNIK_TAG_PREFIX),
                                downloadUrl = apkUrl,
                                notes = release.body.orEmpty(),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Самый свежий опубликованный релиз Кубатурника (список GitHub идёт от новых к старым). */
    private fun fetchKubaturnikRelease(): GithubRelease? {
        val body = get("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=50") ?: return null
        return json.decodeFromString<List<GithubRelease>>(body)
            .filter { !it.draft && !it.prerelease && it.tagName.startsWith(KUBATURNIK_TAG_PREFIX) }
            .maxWithOrNull { a, b ->
                val av = a.tagName.removePrefix(KUBATURNIK_TAG_PREFIX)
                val bv = b.tagName.removePrefix(KUBATURNIK_TAG_PREFIX)
                when {
                    isNewerVersion(av, bv) -> 1
                    isNewerVersion(bv, av) -> -1
                    else -> 0
                }
            }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LesovodKubaturnik")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("GitHub вернул ${response.code}")
            return response.body?.string()
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    fun requestInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    fun startDownload(info: UpdateInfo) {
        enqueue(info.downloadUrl, "Лесовод Кубатурник ${info.versionName}", "kubaturnik-${info.versionName}.apk")
        _state.value = UpdateState.Downloading(info)
    }

    /** Скачать и поставить основной Лесовод (последний релиз). Ошибка — в [onError]. */
    fun downloadLesovod(onError: (String) -> Unit) {
        if (_lesovodDownloading.value) return
        _lesovodDownloading.value = true
        scope.launch {
            val result = runCatching {
                val body = get("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
                    ?: error("релиз не найден")
                val release = json.decodeFromString<GithubRelease>(body)
                val url = release.apkUrl() ?: error("в релизе нет APK")
                enqueue(url, "Лесовод ${release.tagName.removePrefix("v")}", "lesovod-${release.tagName}.apk")
            }
            result.exceptionOrNull()?.let {
                _lesovodDownloading.value = false
                onError("Не удалось скачать Лесовод: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun enqueue(url: String, title: String, fileName: String) {
        val manager = appContext.getSystemService<DownloadManager>() ?: error("нет DownloadManager")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Загрузка")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = manager.enqueue(request)
        val ids = prefs.getStringSet(KEY_DOWNLOAD_IDS, emptySet()).orEmpty() + id.toString()
        prefs.edit().putStringSet(KEY_DOWNLOAD_IDS, ids).apply()
    }

    private fun isLesovodInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(LESOVOD_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Открыть установленный Лесовод. */
    fun launchLesovodIntent(): Intent? = appContext.packageManager.getLaunchIntentForPackage(LESOVOD_PACKAGE)

    companion object {
        private const val GITHUB_OWNER = "VladiFon"
        private const val GITHUB_REPO = "LesovodMobile"
        private const val KUBATURNIK_TAG_PREFIX = "kubaturnik-v"
        const val LESOVOD_PACKAGE = "com.lesovod.mobile"
        internal const val KEY_DOWNLOAD_IDS = "download_ids"

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

/** «1.2.0» новее «1.1.9»? По-компонентно, без суффиксов. */
internal fun isNewerVersion(remote: String, current: String): Boolean {
    fun parse(v: String) = v.removePrefix("v").substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
    val r = parse(remote)
    val c = parse(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
    fun apkUrl(): String? = assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
}

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/** Загрузка APK (обновление Кубатурника или Лесовод) завершилась — открываем системную установку. */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(AppUpdateManager.KEY_DOWNLOAD_IDS, emptySet()).orEmpty()
        if (completedId == -1L || completedId.toString() !in ids) return
        prefs.edit().putStringSet(AppUpdateManager.KEY_DOWNLOAD_IDS, ids - completedId.toString()).apply()

        val manager = context.getSystemService<DownloadManager>() ?: return
        val uri = runCatching { manager.getUriForDownloadedFile(completedId) }.getOrNull() ?: return
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(installIntent) }
    }
}
