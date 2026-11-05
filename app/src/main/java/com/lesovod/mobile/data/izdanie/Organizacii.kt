package com.lesovod.mobile.data.izdanie

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.lesovod.mobile.MainActivity
import com.lesovod.mobile.data.network.ServerConfig
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Несколько организаций в одном телефоне (Влад, 05.11.2026): своё лесничество и лесопункт —
 * у каждой свой сервер, свой вход и свои делянки. Переключение одним нажатием в Профиле:
 * вход в каждую организацию запоминается, второй раз пароль не спрашивается.
 *
 * При переключении:
 *  - неотправленные отчёты должны сначала уйти на свой сервер (иначе они попали бы в чужую базу);
 *  - вход текущей организации откладывается, вход новой — достаётся (если был);
 *  - кэш делянок, остатков и слоёв карты чистится (кварталы и выделы остаются — они общие);
 *  - приложение перезапускается, чтобы ни один экран не держал данные прежней организации.
 */
object Organizacii {
    data class Org(val osnovnoy: String, val zapasnoy: String?, val nazvanie: String?)

    private const val PREFS = "lesovod_organizacii"
    private const val KEY_SPISOK = "spisok"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Сохранённые организации; текущая — всегда в списке, первой. */
    fun spisok(context: Context): List<Org> {
        ServerConfig.init(context)
        val raw = prefs(context).getString(KEY_SPISOK, null)
        val list = mutableListOf<Org>()
        runCatching {
            val arr = JSONArray(raw ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Org(o.getString("o"), o.optString("z").ifBlank { null }, o.optString("n").ifBlank { null })
            }
        }
        val tek = ServerConfig.osnovnoy
        val idx = list.indexOfFirst { it.osnovnoy == tek }
        val tekOrg = if (idx >= 0) list.removeAt(idx).copy(zapasnoy = ServerConfig.zapasnoy) else Org(tek, ServerConfig.zapasnoy, null)
        return listOf(tekOrg) + list
    }

    private fun sohranit(context: Context, list: List<Org>) {
        val arr = JSONArray()
        list.distinctBy { it.osnovnoy }.forEach {
            arr.put(JSONObject().put("o", it.osnovnoy).put("z", it.zapasnoy ?: "").put("n", it.nazvanie ?: ""))
        }
        prefs(context).edit { putString(KEY_SPISOK, arr.toString()) }
    }

    /** Запомнить название текущей (после ответа /api/izdanie). */
    fun nazvatTekushchuyu(context: Context, nazvanie: String) {
        val list = spisok(context)
        sohranit(context, listOf(list.first().copy(nazvanie = nazvanie)) + list.drop(1))
    }

    /** Добавить организацию по адресу. null — добавлено, иначе текст ошибки. */
    suspend fun dobavit(context: Context, adresRaw: String): String? {
        val adres = ServerConfig.normalize(adresRaw) ?: return "Не похоже на адрес сервера"
        if (spisok(context).any { it.osnovnoy == adres }) return "Такая организация уже есть в списке"
        ServerConfig.proverit(adres)?.let { return it }
        val info = Izdanie.obnovit(adres)
        sohranit(context, spisok(context) + Org(adres, null, info?.podpis))
        return null
    }

    fun udalit(context: Context, org: Org) {
        if (org.osnovnoy == ServerConfig.osnovnoy) return
        SessionManager.getInstance(context).zabytOtlozhennuyu(org.osnovnoy)
        sohranit(context, spisok(context).filter { it.osnovnoy != org.osnovnoy })
    }

    /** null — переключились (приложение перезапустится), иначе — почему нельзя. */
    fun pereklyuchit(context: Context, org: Org): String? {
        if (org.osnovnoy == ServerConfig.osnovnoy) return null
        val nOtpravit = OfflineQueueManager.getInstance(context).pending.value.size
        if (nOtpravit > 0) {
            return "Сначала отправьте данные ($nOtpravit) — они для текущей организации. " +
                "Профиль → «Отправка данных» → «Отправить сейчас»."
        }
        val session = SessionManager.getInstance(context)
        val list = spisok(context)
        session.otlozhit(ServerConfig.osnovnoy)
        ServerConfig.save(org.osnovnoy, org.zapasnoy.orEmpty())?.let { return it }
        session.dostat(org.osnovnoy)
        sohranit(context, list)
        ochistitKesh(context)
        perezapustit(context)
        return null
    }

    private fun ochistitKesh(context: Context) {
        val files = context.applicationContext.filesDir
        File(files, "field_cache").listFiles()?.forEach { it.delete() }
        // кварталы и выделы одни на всех — их не качаем заново
        File(files, "map_cache_v2").listFiles()?.forEach { f ->
            val n = f.name
            if (!(n.startsWith("kvartaly_") || n.startsWith("vydela_") || n.startsWith("lesnichestva"))) f.delete()
        }
    }

    private fun perezapustit(context: Context) {
        val intent = Intent(context.applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.applicationContext.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
