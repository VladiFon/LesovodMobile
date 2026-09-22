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
