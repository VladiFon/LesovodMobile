package com.lesovod.mobile.data.session

import android.content.Context
import androidx.core.content.edit
import com.lesovod.mobile.data.network.dto.LoginResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkerSession(
    val token: String,
    val login: String,
    val fio: String,
    val dolzhnost: String,
    val role: WorkerRole,
    val appIdentity: String?,
)

class SessionManager private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("lesovod_session", Context.MODE_PRIVATE)

    init {
        // адрес сервера нужен раньше первого запроса; SessionManager создаётся везде,
        // где есть сеть (экраны, фоновая очередь, напоминания)
        com.lesovod.mobile.data.network.ServerConfig.init(context)
        com.lesovod.mobile.data.izdanie.Izdanie.init(context)
    }

    private val _session = MutableStateFlow(readFromPrefs())
    val session = _session.asStateFlow()

    val isLoggedIn: Boolean get() = _session.value != null

    /** Логин, запомненный по галочке «Запомнить меня» — переживает logout, в отличие от сессии. */
    val rememberedLogin: String? get() = prefs.getString(KEY_REMEMBERED_LOGIN, null)

    fun rememberLogin(login: String) {
        prefs.edit { putString(KEY_REMEMBERED_LOGIN, login) }
    }

    fun forgetLogin() {
        prefs.edit { remove(KEY_REMEMBERED_LOGIN) }
    }

    fun save(response: LoginResponseDto) {
        val worker = response.worker
        val login = worker?.login.orEmpty()
        val fio = worker?.fio.orEmpty()
        val dolzhnost = worker?.dolzhnost.orEmpty()
        val appIdentity = worker?.appIdentity

        prefs.edit {
            putString(KEY_TOKEN, response.token)
            putString(KEY_LOGIN, login)
            putString(KEY_FIO, fio)
            putString(KEY_DOLZHNOST, dolzhnost)
            putString(KEY_APP_IDENTITY, appIdentity)
        }

        _session.value = WorkerSession(
            token = response.token,
            login = login,
            fio = fio,
            dolzhnost = dolzhnost,
            role = WorkerRole.fromDolzhnost(dolzhnost),
            appIdentity = appIdentity,
        )
    }

    fun clear() {
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_LOGIN)
            remove(KEY_FIO)
            remove(KEY_DOLZHNOST)
            remove(KEY_APP_IDENTITY)
        }
        _session.value = null
    }

    /**
     * Переключение организаций (data/izdanie/Organizacii.kt): вход текущей организации
     * откладывается под её адресом, чтобы при возврате не вводить пароль заново.
     */
    fun otlozhit(adres: String) {
        val token = prefs.getString(KEY_TOKEN, null)
        prefs.edit {
            if (token == null) {
                remove(KEY_OTLOZH + adres)
            } else {
                val o = org.json.JSONObject()
                    .put(KEY_TOKEN, token)
                    .put(KEY_LOGIN, prefs.getString(KEY_LOGIN, "").orEmpty())
                    .put(KEY_FIO, prefs.getString(KEY_FIO, "").orEmpty())
                    .put(KEY_DOLZHNOST, prefs.getString(KEY_DOLZHNOST, "").orEmpty())
                    .put(KEY_APP_IDENTITY, prefs.getString(KEY_APP_IDENTITY, null) ?: "")
                putString(KEY_OTLOZH + adres, o.toString())
            }
        }
        clear()
    }

    /** Достать отложенный вход организации (если она уже была на этом телефоне). */
    fun dostat(adres: String) {
        val raw = prefs.getString(KEY_OTLOZH + adres, null) ?: return
        val o = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return
        prefs.edit {
            putString(KEY_TOKEN, o.optString(KEY_TOKEN))
            putString(KEY_LOGIN, o.optString(KEY_LOGIN))
            putString(KEY_FIO, o.optString(KEY_FIO))
            putString(KEY_DOLZHNOST, o.optString(KEY_DOLZHNOST))
            putString(KEY_APP_IDENTITY, o.optString(KEY_APP_IDENTITY).ifBlank { null })
            remove(KEY_OTLOZH + adres)
        }
        _session.value = readFromPrefs()
    }

    fun zabytOtlozhennuyu(adres: String) {
        prefs.edit { remove(KEY_OTLOZH + adres) }
    }

    private fun readFromPrefs(): WorkerSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val dolzhnost = prefs.getString(KEY_DOLZHNOST, "").orEmpty()
        return WorkerSession(
            token = token,
            login = prefs.getString(KEY_LOGIN, "").orEmpty(),
            fio = prefs.getString(KEY_FIO, "").orEmpty(),
            dolzhnost = dolzhnost,
            role = WorkerRole.fromDolzhnost(dolzhnost),
            appIdentity = prefs.getString(KEY_APP_IDENTITY, null),
        )
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_LOGIN = "login"
        private const val KEY_FIO = "fio"
        private const val KEY_DOLZHNOST = "dolzhnost"
        private const val KEY_APP_IDENTITY = "app_identity"
        private const val KEY_REMEMBERED_LOGIN = "remembered_login"
        private const val KEY_OTLOZH = "otlozh_"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context).also { instance = it }
            }

        /** Для мест без Context (сетевой interceptor) — null, если ни один экран ещё не создал сессию. */
        fun peekInstance(): SessionManager? = instance
    }
}
