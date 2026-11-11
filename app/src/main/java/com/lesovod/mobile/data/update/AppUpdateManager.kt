package com.lesovod.mobile.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lesovod.mobile.MainActivity
import com.lesovod.mobile.R
import java.util.concurrent.TimeUnit
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File
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
 * и предлагает скачать и установить APK, если он новее текущей версии. С 0.14.0 (11.11.2026,
 * просьба Влада: «не все старики понимают, как это работает») APK качаем сами и сразу
 * отдаём системному установщику через [PackageInstaller]: установка начинается сама, а на
 * Android 12+ со второго обновления проходит вообще без вопросов. Отдельный
 * лёгкий OkHttpClient — не переиспользует [com.lesovod.mobile.data.network.NetworkModule],
 * у которого другой базовый адрес и перехватчик авторизации сервера ботов.
 */
class AppUpdateManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val prefs = appContext.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Проверка без мигания «Проверка…», если уже знаем ответ — для главного экрана. */
    fun checkQuietly() {
        if (_state.value is UpdateState.Available || zanyat() || _state.value is UpdateState.Checking) return
        checkForUpdate()
    }

    /** Синхронная проверка для фонового WorkManager — только уведомление. */
    fun checkInBackground() {
        val release = runCatching { fetchLatestRelease() }.getOrNull() ?: return
        if (!isNewerVersion(release.tagName, BuildConfig.VERSION_NAME)) return
        val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl ?: return
        val info = UpdateInfo(release.tagName.removePrefix("v"), apkUrl, release.body.orEmpty())
        if (zanyat()) return
        // Телефон лежит, Wi-Fi есть, и система разрешает ставить без вопросов — обновляемся сами.
        if (!foreground && mozhnoTiho() && !setPlatnaya()) {
            _state.value = UpdateState.Downloading(info, 0)
            val ok = runCatching { install(downloadApk(info) { }, info) }.isSuccess
            if (ok) {
                _state.value = UpdateState.Installing(info)
                return
            }
        }
        notifyOnce(info)
        _state.value = UpdateState.Available(info)
    }

    private fun zanyat() = _state.value is UpdateState.Downloading || _state.value is UpdateState.Installing

    /**
     * Android 12+ ставит обновление без подтверждения, если прошлую версию ставили мы же
     * (то есть со второго обновления новым способом) и разрешена установка из приложения.
     */
    private fun mozhnoTiho(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !canInstallPackages()) return false
        val kto = runCatching { appContext.packageManager.getInstallSourceInfo(appContext.packageName).installingPackageName }.getOrNull()
        return kto == appContext.packageName
    }

    private fun setPlatnaya(): Boolean =
        appContext.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: true

    /**
     * Уведомление Android «Вышла новая версия» — один раз на версию (часть людей не замечала
     * обновления в Профиле, просьба 03.11.2026). Нажатие открывает приложение, где на главном
     * экране висит плашка с кнопкой «Обновить».
     */
    private fun notifyOnce(info: UpdateInfo) {
        if (prefs.getString(KEY_NOTIFIED, null) == info.versionName) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Обновления приложения", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            appContext,
            7,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "Сейчас у вас ${BuildConfig.VERSION_NAME}. Откройте Лесовод и нажмите «Обновить» на главном экране."
        val n = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Вышла новая версия Лесовода ${info.versionName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, n) }
        prefs.edit().putString(KEY_NOTIFIED, info.versionName).apply()
    }

    fun checkForUpdate() {
        if (zanyat()) return
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
                    else UpdateInfo(versionName = release.tagName.removePrefix("v"), downloadUrl = apkUrl, notes = release.body.orEmpty())
                        .also { notifyOnce(it) }
                        .let { UpdateState.Available(it) }
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

    /**
     * Качает APK и сразу запускает установку. Дальше человеку остаётся (и то не всегда) нажать
     * «Обновить» в системном окне — искать файл в загрузках не нужно.
     */
    fun startDownload(info: UpdateInfo) {
        if (zanyat()) return
        _state.value = UpdateState.Downloading(info, 0)
        scope.launch {
            val file = runCatching { downloadApk(info) { p -> _state.value = UpdateState.Downloading(info, p) } }.getOrNull()
            if (file == null) {
                _state.value = UpdateState.Available(info, "Не удалось скачать: проверьте связь и нажмите «Обновить» ещё раз")
                return@launch
            }
            _state.value = UpdateState.Installing(info)
            runCatching { install(file, info) }.onFailure {
                file.delete()
                _state.value = UpdateState.Available(info, "Не получилось начать установку. Нажмите «Обновить» ещё раз")
            }
        }
    }

    private fun apkDir(): File =
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(appContext.cacheDir, "update").apply { mkdirs() }

    /** Скачивает APK в свою папку; готовый файл той же версии не качает повторно. */
    private fun downloadApk(info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = apkDir()
        val gotov = File(dir, "lesovod-${info.versionName}.apk")
        dir.listFiles()?.forEach { if (it.name.startsWith("lesovod-") && it != gotov) it.delete() }
        if (gotov.exists() && gotov.length() > 0) return gotov
        val chast = File(dir, "lesovod-${info.versionName}.apk.part")
        val request = Request.Builder().url(info.downloadUrl).header("User-Agent", "LesovodMobile").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub вернул ${response.code}")
            val body = response.body ?: error("Пустой ответ")
            val vsego = body.contentLength()
            var skachano = 0L
            var proshlyy = -1
            body.byteStream().use { input ->
                chast.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        skachano += n
                        if (vsego > 0) {
                            val p = (skachano * 100 / vsego).toInt()
                            if (p != proshlyy) {
                                proshlyy = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            if (vsego > 0 && skachano != vsego) error("Файл скачан не полностью")
        }
        if (!chast.renameTo(gotov)) error("Не удалось сохранить файл")
        return gotov
    }

    /** Отдаёт APK системному установщику. Чем кончилось — приходит в [UpdateInstallReceiver]. */
    private fun install(file: File, info: UpdateInfo) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(appContext.packageName)
        params.setSize(file.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("lesovod.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(
                appContext,
                id,
                Intent(appContext, UpdateInstallReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .putExtra(EXTRA_VERSION, info.versionName),
                flags,
            )
            session.commit(status.intentSender)
        }
        prefs.edit().putString(KEY_INSTALLING, info.versionName).apply()
    }

    /** Системе нужно подтверждение человека: открываем её окно сами, а если нельзя — уведомлением. */
    internal fun nuzhnoPodtverdit(confirm: Intent, version: String) {
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val otkryto = foreground && runCatching { appContext.startActivity(confirm) }.isSuccess
        if (!otkryto) {
            val tap = PendingIntent.getActivity(appContext, 8, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            pokazat("Обновление Лесовода $version скачано", "Нажмите сюда и потом «Обновить» — больше ничего делать не нужно.", tap)
        }
    }

    internal fun ustanovkaNeUdalas(prichina: String?) {
        prefs.edit().remove(KEY_INSTALLING).apply()
        val info = (_state.value as? UpdateState.Installing)?.info ?: return
        _state.value = UpdateState.Available(info, "Установка не прошла${prichina?.let { ": $it" } ?: ""}. Нажмите «Обновить» ещё раз")
    }

    /** После замены приложения: говорим, что всё получилось, и убираем скачанный файл. */
    internal fun posleObnovleniya() {
        runCatching { apkDir().listFiles()?.forEach { if (it.name.startsWith("lesovod-")) it.delete() } }
        if (prefs.getString(KEY_INSTALLING, null) == null) return
        prefs.edit().remove(KEY_INSTALLING).apply()
        val open = PendingIntent.getActivity(
            appContext,
            9,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        pokazat("Лесовод обновлён до ${BuildConfig.VERSION_NAME}", "Всё установилось само. Нажмите, чтобы открыть.", open)
    }

    private fun pokazat(title: String, text: String, tap: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Обновления приложения", NotificationManager.IMPORTANCE_HIGH))
        }
        val n = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, n) }
    }

    companion object {
        private const val GITHUB_OWNER = "VladiFon"
        private const val GITHUB_REPO = "LesovodMobile"
        private const val KEY_INSTALLING = "installing_version"
        internal const val ACTION_INSTALL_STATUS = "com.lesovod.mobile.UPDATE_INSTALL_STATUS"
        internal const val EXTRA_VERSION = "version"

        /** Приложение на экране: отсюда можно открыть системное окно установки. Ставит MainActivity. */
        @Volatile
        var foreground: Boolean = false
        private const val KEY_NOTIFIED = "notified_version"
        private const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 770_001
        private const val WORK_NAME = "app_update_check"

        /** Раз в 6 часов спрашиваем GitHub в фоне — уведомление придёт, даже если приложение не открывали. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

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
    data class Available(val info: UpdateInfo, val problem: String? = null) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int = -1) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
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

/** Ответ системного установщика на сессию из [AppUpdateManager]. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppUpdateManager.ACTION_INSTALL_STATUS) return
        val manager = AppUpdateManager.getInstance(context)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                manager.nuzhnoPodtverdit(confirm, intent.getStringExtra(AppUpdateManager.EXTRA_VERSION).orEmpty())
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> manager.ustanovkaNeUdalas("установку отменили")
            PackageInstaller.STATUS_FAILURE_STORAGE -> manager.ustanovkaNeUdalas("на телефоне мало места")
            else -> manager.ustanovkaNeUdalas(null)
        }
    }
}

/** Приложение только что заменили новой версией. */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppUpdateManager.getInstance(context).posleObnovleniya()
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { AppUpdateManager.getInstance(applicationContext).checkInBackground() }
        return Result.success()
    }
}
