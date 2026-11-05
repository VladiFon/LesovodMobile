package com.lesovod.mobile.data.izdanie

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lesovod.mobile.data.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * «Издание» сервера (Влад, 04–05.11.2026): одно приложение работает и у лесничества, и у
 * лесопункта — у каждого своя копия сервера. Сервер говорит, кто он (GET /api/izdanie), и
 * телефон показывает нужные экраны: у лесопункта нет проб рубок ухода и лесных культур,
 * зато кубатурник и остатки — всем, а в «Отчёте за смену» только вальщик, тракторист на
 * вывозке и харвестерщик.
 *
 * Ответ запоминается по адресу сервера — без связи телефон знает, чей он.
 */
object Izdanie {
    const val LESNICHESTVO = "lesnichestvo"
    const val LESOPUNKT = "lesopunkt"

    data class Info(
        val izdanie: String = LESNICHESTVO,
        val nazvanie: String = "Лесничество",
        val organizaciya: String? = null,
        val skrytEkrany: Set<String> = emptySet(),
        /** профессия (traktorist / harvester / valshik) → коды видов работ */
        val professii: Map<String, List<String>> = emptyMap(),
    ) {
        val lesopunkt get() = izdanie == LESOPUNKT
        /** Как называть организацию: «Лесопункт «Дубровка»» или просто «Лесопункт». */
        val podpis get() = organizaciya ?: nazvanie
    }

    private const val PREFS = "lesovod_izdanie"
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(Info())
    val state: StateFlow<Info> = _state.asStateFlow()

    /** Для проверок прав вне Compose (RolePermissions). */
    val lesopunkt: Boolean get() = _state.value.lesopunkt

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    }

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        ServerConfig.init(context)
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = izKesha(ServerConfig.osnovnoy) ?: Info()
    }

    private fun izKesha(adres: String): Info? =
        prefs?.getString(adres, null)?.let { runCatching { razobrat(JSONObject(it)) }.getOrNull() }

    /** Что известно о сервере по адресу (для списка организаций), без сети. */
    fun izvestno(adres: String): Info? = izKesha(adres)

    fun razobrat(o: JSONObject): Info {
        val skryt = o.optJSONArray("skryt_ekrany")
        val prof = o.optJSONObject("professii")
        val professii = mutableMapOf<String, List<String>>()
        prof?.keys()?.forEach { k ->
            val vidy = prof.optJSONObject(k)?.optJSONArray("vidy")
            if (vidy != null) professii[k] = List(vidy.length()) { vidy.optString(it) }
        }
        return Info(
            izdanie = o.optString("izdanie", LESNICHESTVO).ifBlank { LESNICHESTVO },
            nazvanie = o.optString("nazvanie", "Лесничество").ifBlank { "Лесничество" },
            organizaciya = o.optString("organizaciya", "").takeIf { it.isNotBlank() && it != "null" },
            skrytEkrany = if (skryt == null) emptySet() else List(skryt.length()) { skryt.optString(it) }.toSet(),
            professii = professii,
        )
    }

    /**
     * Спросить сервер (по основному адресу, при недоступности — по рабочему). Старый сервер
     * без /api/izdanie (404) — это лесничество. Без связи остаётся то, что было.
     */
    suspend fun obnovit(adres: String = ServerConfig.osnovnoy): Info? = withContext(Dispatchers.IO) {
        // запасной адрес — только для своей организации (не подставить чужой ответ новой)
        val kandidaty = if (adres == ServerConfig.osnovnoy) listOf(adres, ServerConfig.active.value).distinct() else listOf(adres)
        for (base in kandidaty) {
            val info = runCatching {
                client.newCall(Request.Builder().url(base + "api/izdanie").build()).execute().use { r ->
                    when {
                        r.isSuccessful -> {
                            val text = r.body?.string().orEmpty()
                            razobrat(JSONObject(text)).also { prefs?.edit { putString(adres, text) } }
                        }
                        r.code == 404 -> Info().also { prefs?.edit { remove(adres) } }
                        else -> null
                    }
                }
            }.getOrNull()
            if (info != null) {
                if (adres == ServerConfig.osnovnoy) _state.value = info
                return@withContext info
            }
        }
        null
    }
}
