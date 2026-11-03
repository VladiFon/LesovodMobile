package com.lesovod.mobile.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

/**
 * Адрес сервера — основной и запасной (шаг 5 разбора 03.11.2026).
 *
 * Раньше адрес был зашит в приложение: когда в октябре 2026 домен заблокировали у
 * регистратора, телефоны вне конторы остались без связи до нового APK. Теперь адреса
 * задаются в Профиле (и на экране входа), а если текущий не отвечает — запрос сам
 * уходит на другой, и дальше приложение работает через него, пока тот жив.
 *
 * Переключаемся только когда до сервера не удалось ДОСТУЧАТЬСЯ (нет такого адреса, не
 * соединилось, сертификат): тогда запрос точно не дошёл, и повтор на другом адресе не
 * задвоит наряд или отчёт. Если же сервер принял запрос, но долго отвечал — не повторяем.
 */
object ServerConfig {
    const val DEFAULT_OSNOVNOY = "https://lesovodapipom.store/"

    /** Заглушка для Retrofit: настоящий адрес подставляет [rewriteInterceptor]. */
    const val PLACEHOLDER = "http://lesovod.server/"

    private const val PREFS = "lesovod_server"
    private const val KEY_OSNOVNOY = "osnovnoy"
    private const val KEY_ZAPASNOY = "zapasnoy"
    private const val KEY_ACTIVE = "active"

    private var prefs: SharedPreferences? = null

    @Volatile var osnovnoy: String = DEFAULT_OSNOVNOY
        private set
    @Volatile var zapasnoy: String? = null
        private set

    private val _active = MutableStateFlow(DEFAULT_OSNOVNOY)
    /** Адрес, через который приложение работает сейчас. */
    val active = _active.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        osnovnoy = normalize(p.getString(KEY_OSNOVNOY, null)) ?: DEFAULT_OSNOVNOY
        zapasnoy = normalize(p.getString(KEY_ZAPASNOY, null))
        val a = normalize(p.getString(KEY_ACTIVE, null))
        _active.value = if (a != null && (a == osnovnoy || a == zapasnoy)) a else osnovnoy
    }

    /** Текущий адрес с «/» на конце — для ссылок (фото, руководство). */
    fun baseUrl(): String = _active.value

    /**
     * «lesovod.local», «192.168.1.10:8000», «https://x.by» → полный адрес со «/» на конце.
     * Без схемы: локальные адреса (IP, .local, localhost) — http, остальные — https.
     * null — пусто или не похоже на адрес.
     */
    fun normalize(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            val host = s.substringBefore('/').substringBefore(':')
            val lokalnyy = host.endsWith(".local") || host == "localhost" ||
                host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
            s = (if (lokalnyy) "http://" else "https://") + s
        }
        if (!s.endsWith("/")) s += "/"
        return s.toHttpUrlOrNull()?.toString()
    }

    /** Сохранить адреса из Профиля. Возвращает текст ошибки или null. */
    fun save(osnovnoyRaw: String, zapasnoyRaw: String): String? {
        val o = normalize(osnovnoyRaw) ?: return "Основной адрес не похож на адрес сервера"
        val z = if (zapasnoyRaw.isBlank()) null else normalize(zapasnoyRaw)
            ?: return "Запасной адрес не похож на адрес сервера"
        osnovnoy = o
        zapasnoy = z?.takeIf { it != o }
        prefs?.edit {
            putString(KEY_OSNOVNOY, o)
            if (zapasnoy == null) remove(KEY_ZAPASNOY) else putString(KEY_ZAPASNOY, zapasnoy)
        }
        setActive(o)
        return null
    }

    private fun setActive(url: String) {
        if (_active.value == url) return
        _active.value = url
        prefs?.edit { putString(KEY_ACTIVE, url) }
    }

    private fun drugoy(current: String): String? =
        if (current == osnovnoy) zapasnoy else osnovnoy

    /** До сервера не достучались — запрос точно не ушёл, можно повторить на другом адресе. */
    private fun nedostupen(e: IOException): Boolean = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is SSLHandshakeException -> true
        is SocketTimeoutException -> e.message?.contains("connect", ignoreCase = true) == true
        is InterruptedIOException -> false
        else -> false
    }

    private fun naAdres(request: Request, base: String): Request {
        val b = base.toHttpUrlOrNull() ?: return request
        val src = request.url
        val prefix = b.encodedPath.trimEnd('/')
        val url: HttpUrl = src.newBuilder()
            .scheme(b.scheme)
            .host(b.host)
            .port(b.port)
            .encodedPath(prefix + src.encodedPath)
            .build()
        return request.newBuilder().url(url).build()
    }

    /**
     * Ставить первым в цепочку OkHttp: заменяет заглушку [PLACEHOLDER] на текущий адрес
     * и при недоступности пробует другой. Запросы к чужим хостам (GitHub, карты) не трогает.
     */
    val rewriteInterceptor = Interceptor { chain ->
        val original = chain.request()
        val placeholder = PLACEHOLDER.toHttpUrlOrNull()
        if (placeholder == null || original.url.host != placeholder.host) {
            return@Interceptor chain.proceed(original)
        }
        val tekushchiy = _active.value
        try {
            chain.proceed(naAdres(original, tekushchiy))
        } catch (e: IOException) {
            val drugoy = drugoy(tekushchiy)
            if (drugoy == null || !nedostupen(e)) throw e
            val response: Response = chain.proceed(naAdres(original, drugoy))
            setActive(drugoy)
            response
        }
    }

    private val proverkaClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Проверить адрес: GET /api/health. null — сервер ответил, иначе текст ошибки. */
    suspend fun proverit(raw: String): String? = withContext(Dispatchers.IO) {
        val base = normalize(raw) ?: return@withContext "Не похоже на адрес сервера"
        try {
            proverkaClient.newCall(Request.Builder().url(base + "api/health").build()).execute().use { r ->
                if (r.isSuccessful) null else "Сервер ответил ошибкой ${r.code}"
            }
        } catch (e: UnknownHostException) {
            "Адрес не найден (нет такого имени или нет интернета)"
        } catch (e: SSLHandshakeException) {
            "Ошибка сертификата — попробуйте http:// вместо https://"
        } catch (e: IOException) {
            "Не отвечает: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
