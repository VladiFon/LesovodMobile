package com.lesovod.mobile.ui.smena

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lesovod.mobile.data.local.NaparnikiStore
import com.lesovod.mobile.data.network.ConnectivityException
import com.lesovod.mobile.data.network.NetworkModule
import com.lesovod.mobile.data.network.dto.SmenaCreatedDto
import com.lesovod.mobile.data.network.dto.SmenaMoiDto
import com.lesovod.mobile.data.network.dto.SmenaNaparnikDto
import com.lesovod.mobile.data.network.dto.SmenaRequest
import com.lesovod.mobile.data.network.dto.SmenaSpravochnikDto
import com.lesovod.mobile.data.repository.BotRepository
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.data.session.SmenaProfessiya
import com.lesovod.mobile.data.session.smenaProfessiya
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Вид работы в отчёте за смену. Коды — как на сервере (routers/smena.py, VIDY). */
enum class SmenaVid(val code: String, val title: String, val emoji: String) {
    TRELEVKA("trelevka", "Трелёвка", "🚜"),
    NASELENIYU("naseleniyu", "Населению", "🏠"),
    HARVESTER("harvester", "Заготовка", "🌲"),
    RUBKA_GP("rubka_gp", "Рубка (валка)", "🪓"),
    RUBKA_UHODA("rubka_uhoda", "Рубка ухода", "✂️"),
    SANRUBKA("sanrubka", "Санрубка", "🪵"),
    OCHISTKA("ochistka", "Очистка мест рубок", "🧹"),
    UHOD("uhod", "Уход за культурами", "🌱"),
    POSADKA("posadka", "Посадка", "🌿"),
    POCHVA("pochva", "Подготовка почвы", "🪚"),
    REMONT("remont", "Ремонт", "🔧"),
    DRUGOE("drugoe", "Другое", "✍️");

    /** Работа на делянке (выбор делянки/выдела). */
    val poDelyanke get() = this in setOf(TRELEVKA, NASELENIYU, HARVESTER, RUBKA_GP, RUBKA_UHODA, SANRUBKA, OCHISTKA)
    /** Работа на участке лесных культур. */
    val poUchastku get() = this == UHOD || this == POCHVA || this == POSADKA
    /** Га копятся по выделу делянки: сделано / осталось. */
    val gaPoVydelu get() = this == RUBKA_UHODA || this == SANRUBKA || this == OCHISTKA
    /** Место — проба экрана «Рубки ухода» (учёт после закладки пробы). */
    val poProbe get() = this == RUBKA_UHODA
    /** Вводят га (участок или выдел). */
    val sGa get() = poUchastku || gaPoVydelu
    /** Вводят кубы. */
    val sKubami get() = this in setOf(TRELEVKA, NASELENIYU, HARVESTER, RUBKA_GP, SANRUBKA)
    /** Кубы обязательны. */
    val kubyObyazatelny get() = this == TRELEVKA || this == NASELENIYU || this == HARVESTER
    /** Видно остаток кубов по билету. */
    val ostatokKubov get() = this == HARVESTER || this == RUBKA_GP

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

/** Набор работ и полей экрана по профессии (Влад, 04.11.2026). */
fun SmenaProfessiya.vidy(): List<SmenaVid> {
    // Лесопункт (издание сервера): набор работ присылает сервер — у вальщика валка,
    // у тракториста на вывозке трелёвка, у харвестерщика харвестер (+ ремонт).
    val izd = com.lesovod.mobile.data.izdanie.Izdanie.state.value
    if (izd.lesopunkt) {
        val kod = when (this) {
            SmenaProfessiya.TRAKTORIST -> "traktorist"
            SmenaProfessiya.HARVESTER -> "harvester"
            SmenaProfessiya.BRIGADA -> "valshik"
        }
        val svoi = izd.professii[kod].orEmpty().mapNotNull { SmenaVid.of(it) }
        if (svoi.isNotEmpty()) return svoi
    }
    return vidyLesnichestva()
}

private fun SmenaProfessiya.vidyLesnichestva(): List<SmenaVid> = when (this) {
    SmenaProfessiya.TRAKTORIST -> listOf(SmenaVid.TRELEVKA, SmenaVid.NASELENIYU, SmenaVid.UHOD, SmenaVid.POCHVA, SmenaVid.REMONT)
    SmenaProfessiya.HARVESTER -> listOf(SmenaVid.HARVESTER, SmenaVid.REMONT)
    SmenaProfessiya.BRIGADA -> listOf(
        SmenaVid.RUBKA_GP, SmenaVid.RUBKA_UHODA, SmenaVid.SANRUBKA, SmenaVid.OCHISTKA,
        SmenaVid.UHOD, SmenaVid.POSADKA, SmenaVid.POCHVA, SmenaVid.DRUGOE,
    )
}

/** Напарники — только у бригады (вальщик/лесоруб/лесовод); у машинистов своя машина. */
val SmenaProfessiya.sNaparnikami get() = this == SmenaProfessiya.BRIGADA

/** Техника и топливо — всегда у машинистов, у бригады по желанию (пила, если был на тракторе). */
val SmenaProfessiya.tehnikaVsegda get() = this != SmenaProfessiya.BRIGADA

data class SmenaForm(
    val vid: SmenaVid = SmenaVid.TRELEVKA,
    /** 0 — сегодня, 1 — вчера, 2 — позавчера… (сервер принимает до 14 дней назад). */
    val dneyNazad: Int = 0,
    val delyankaItemId: Int? = null,
    val uchastokId: Int? = null,
    val reysov: String = "",
    val obyom: String = "",
    val ga: String = "",
    val proyden: Boolean = false,
    val chasy: String = "",
    val poluchatel: String = "",
    val tehnika: String = "",
    /** Машина из справочника; null — не выбрана (или справочника нет — тогда текстом [tehnika]). */
    val tehnikaId: Int? = null,
    val toplivoRashod: String = "",
    val zapravka: String = "",
    /** Бригада: раскрыт блок «Техника и топливо». */
    val sTehnikoy: Boolean = false,
    val kommentariy: String = "",
    val naparniki: Set<Int> = emptySet(),
    /** Рубка ухода: проба «Рубок ухода». */
    val probaId: Int? = null,
    /** Показывать списанные / переведённые участки культур. */
    val pokazatVse: Boolean = false,
) {
    /** Место работы — проба рубок ухода. */
    val naProbe get() = vid.poProbe
    /** Место работы — участок лесных культур. */
    val naUchastke get() = vid.poUchastku
    /** Место работы — делянка / выдел. */
    val naDelyanke get() = vid.poDelyanke && !naProbe
}

sealed interface SmenaResult {
    /** Отправлено. [created] — ответ сервера (остаток по участку и т.п.). */
    data class Sent(val opisanie: String, val created: SmenaCreatedDto) : SmenaResult
    data class Queued(val opisanie: String) : SmenaResult
}

data class SmenaUiState(
    val loading: Boolean = true,
    val spravochnik: SmenaSpravochnikDto? = null,
    val spravochnikIzKesha: Boolean = false,
    val loadError: String? = null,
    val form: SmenaForm = SmenaForm(),
    val sending: Boolean = false,
    val error: String? = null,
    val result: SmenaResult? = null,
    val moi: SmenaMoiDto? = null,
    val deletingId: Int? = null,
)

class SmenaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(NetworkModule.api, SessionManager.getInstance(application))
    private val queue = OfflineQueueManager.getInstance(application)
    private val naparnikiStore = NaparnikiStore.getInstance(application)
    private val cache = application.getSharedPreferences("smena_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(SmenaUiState())
    val state = _state.asStateFlow()

    /** Профессия по должности; кто без своего набора (мастер и т.п.) — как бригада. */
    val professiya: SmenaProfessiya =
        SessionManager.getInstance(application).session.value?.role?.smenaProfessiya ?: SmenaProfessiya.BRIGADA
    val vidy: List<SmenaVid> = professiya.vidy()

    init {
        val lastVid = cache.getString(KEY_VID, null)?.let { SmenaVid.of(it) }?.takeIf { it in vidy }
        val lastTehnika = cache.getString(KEY_TEHNIKA, "").orEmpty()
        val lastTehnikaId = cache.getInt(KEY_TEHNIKA_ID, -1).takeIf { it > 0 }
        _state.value = _state.value.copy(
            form = SmenaForm(vid = lastVid ?: vidy.first(), tehnika = lastTehnika, tehnikaId = lastTehnikaId),
        )
        cache.getString(KEY_SPRAV, null)?.let { raw ->
            runCatching { json.decodeFromString<SmenaSpravochnikDto>(raw) }.getOrNull()?.let {
                _state.value = _state.value.copy(spravochnik = it, spravochnikIzKesha = true, loading = true)
                podstavitMashinu(it)
            }
        }
        refresh()
    }

    /** Своя (закреплённая) машина подставляется сама; выбранная раньше — если она ещё в списке. */
    private fun podstavitMashinu(s: SmenaSpravochnikDto) {
        val f = _state.value.form
        val est = f.tehnikaId?.takeIf { id -> s.mashiny.any { it.id == id } }
        val id = est ?: s.moyaMashinaId
        if (id != f.tehnikaId) _state.value = _state.value.copy(form = f.copy(tehnikaId = id))
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, loadError = null)
        viewModelScope.launch {
            repository.smenaSpravochnik().fold(
                onSuccess = { s ->
                    cache.edit { putString(KEY_SPRAV, json.encodeToString(s)) }
                    _state.value = _state.value.copy(loading = false, spravochnik = s, spravochnikIzKesha = false)
                    podstavitMashinu(s)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        loadError = if (_state.value.spravochnik == null) (e.message ?: "Не удалось загрузить") else null,
                    )
                },
            )
            loadMoi()
        }
    }

    private suspend fun loadMoi() {
        repository.smenaMoi().onSuccess { _state.value = _state.value.copy(moi = it) }
    }

    /** Напарники: сначала те, с кем чаще работал (телефон + сервер), потом остальные по алфавиту. */
    fun naparnikiSorted(): List<SmenaNaparnikDto> {
        val all = _state.value.spravochnik?.naparniki.orEmpty()
        return all.sortedWith(
            compareByDescending<SmenaNaparnikDto> { naparnikiStore.count(it.id) * 3 + it.raz }.thenBy { it.fio },
        )
    }

    fun chastye(): Set<Int> =
        _state.value.spravochnik?.naparniki.orEmpty()
            .filter { naparnikiStore.count(it.id) > 0 || it.raz > 0 }
            .map { it.id }.toSet()

    fun lastNaparniki(): List<Int> = naparnikiStore.last

    fun update(block: (SmenaForm) -> SmenaForm) {
        _state.value = _state.value.copy(form = block(_state.value.form), error = null)
    }

    fun setVid(vid: SmenaVid) {
        cache.edit { putString(KEY_VID, vid.code) }
        update { it.copy(vid = vid) }
    }

    fun toggleNaparnik(id: Int) = update {
        it.copy(naparniki = if (id in it.naparniki) it.naparniki - id else it.naparniki + id)
    }

    fun submit() {
        val st = _state.value
        val f = st.form
        fun num(s: String) = s.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val obyom = num(f.obyom)
        val ga = num(f.ga)
        val chasy = num(f.chasy)
        val reysov = f.reysov.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val sTehnikoy = professiya.tehnikaVsegda || f.sTehnikoy || f.vid == SmenaVid.REMONT
        val rashod = if (sTehnikoy) num(f.toplivoRashod) else null
        val zapravka = if (sTehnikoy) num(f.zapravka) else null
        val gaNado = f.vid == SmenaVid.RUBKA_UHODA || f.vid == SmenaVid.OCHISTKA || f.naUchastke
        val err = when {
            f.chasy.isNotBlank() && (chasy == null || chasy < 0 || chasy > 24) -> "Часы — числом от 0 до 24"
            f.reysov.isNotBlank() && (reysov == null || reysov < 0) -> "Рейсы — целым числом"
            sTehnikoy && f.toplivoRashod.isNotBlank() && (rashod == null || rashod < 0 || rashod > 2000) -> "Топливо — числом, литры"
            sTehnikoy && f.zapravka.isNotBlank() && (zapravka == null || zapravka < 0 || zapravka > 2000) -> "Заправка — числом, литры"
            f.naDelyanke && f.delyankaItemId == null -> "Выбери делянку"
            f.vid.kubyObyazatelny && (obyom == null || obyom <= 0) -> "Укажи кубы"
            f.vid == SmenaVid.NASELENIYU && f.poluchatel.isBlank() -> "Укажи, кому везли"
            f.naUchastke && f.uchastokId == null -> "Выбери участок"
            f.naProbe && f.probaId == null -> "Выбери пробу (или заложи новую)"
            gaNado && !f.proyden && (ga == null || ga <= 0) -> "Укажи, сколько га сделал (или отметь «пройден полностью»)"
            f.vid == SmenaVid.SANRUBKA && !f.proyden && (ga ?: 0.0) <= 0 && (obyom ?: 0.0) <= 0 -> "Укажи га или кубы"
            f.vid == SmenaVid.DRUGOE && f.kommentariy.isBlank() -> "Напиши, что делал"
            f.vid == SmenaVid.REMONT && (chasy == null || chasy <= 0) -> "Укажи, сколько часов был ремонт"
            f.vid == SmenaVid.REMONT && f.tehnika.isBlank() && f.tehnikaId == null && f.kommentariy.isBlank() -> "Напиши, что ремонтировали"
            else -> null
        }
        if (err != null) {
            _state.value = st.copy(error = err)
            return
        }
        val request = SmenaRequest(
            vid = f.vid.code,
            data = LocalDate.now().minusDays(f.dneyNazad.toLong()).toString(),
            delyankaItemId = if (f.naDelyanke) f.delyankaItemId else null,
            lesokulturyUchastokId = if (f.naUchastke) f.uchastokId else null,
            uhodyProbaId = if (f.naProbe) f.probaId else null,
            reysov = if (f.vid == SmenaVid.TRELEVKA || f.vid == SmenaVid.NASELENIYU) reysov else null,
            obyom = if (f.vid.sKubami) obyom else null,
            ga = if (f.vid.sGa) ga else null,
            chasy = chasy,
            poluchatel = f.poluchatel.trim().takeIf { f.vid == SmenaVid.NASELENIYU && it.isNotEmpty() },
            tehnika = f.tehnika.trim().takeIf { sTehnikoy && f.tehnikaId == null && it.isNotEmpty() },
            tehnikaId = f.tehnikaId.takeIf { sTehnikoy },
            toplivoRashod = rashod,
            zapravka = zapravka,
            kommentariy = f.kommentariy.trim().takeIf { it.isNotEmpty() },
            uchastokProyden = f.vid.sGa && f.proyden && !f.naProbe,
            naparniki = if (professiya.sNaparnikami) f.naparniki.toList() else emptyList(),
            clientId = UUID.randomUUID().toString(),
        )
        val opisanie = opisanie(f, obyom, ga, chasy, reysov, rashod, zapravka)
        _state.value = st.copy(sending = true, error = null)
        viewModelScope.launch {
            val res = repository.submitSmena(request)
            val e = res.exceptionOrNull()
            if (e is ConnectivityException) {
                queue.enqueueSmena(request, opisanie)
                posleOtpravki(f)
                _state.value = _state.value.copy(sending = false, result = SmenaResult.Queued(opisanie))
                return@launch
            }
            res.fold(
                onSuccess = { created ->
                    posleOtpravki(f)
                    _state.value = _state.value.copy(sending = false, result = SmenaResult.Sent(opisanie, created))
                    // остатки и «мои отчёты» — свежие
                    repository.smenaSpravochnik().onSuccess { s ->
                        cache.edit { putString(KEY_SPRAV, json.encodeToString(s)) }
                        _state.value = _state.value.copy(spravochnik = s, spravochnikIzKesha = false)
                    }
                    loadMoi()
                },
                onFailure = { _state.value = _state.value.copy(sending = false, error = it.message ?: "Не удалось отправить") },
            )
        }
    }

    private fun posleOtpravki(f: SmenaForm) {
        naparnikiStore.remember(f.naparniki)
        naparnikiStore.last = f.naparniki.toList()
        cache.edit {
            putString(KEY_TEHNIKA, f.tehnika.trim())
            putInt(KEY_TEHNIKA_ID, f.tehnikaId ?: -1)
        }
    }

    private fun opisanie(
        f: SmenaForm, obyom: Double?, ga: Double?, chasy: Double?, reysov: Int?, rashod: Double?, zapravka: Double?,
    ): String {
        val s = _state.value.spravochnik
        val mesto = when {
            f.naDelyanke -> s?.delyanki?.firstOrNull { it.itemId == f.delyankaItemId }?.label
            f.naProbe -> s?.proby?.firstOrNull { it.id == f.probaId }?.label
            f.naUchastke -> s?.uchastki?.firstOrNull { it.id == f.uchastokId }?.label
            else -> s?.mashiny?.firstOrNull { it.id == f.tehnikaId }?.label ?: f.tehnika.trim().ifEmpty { null }
        }
        return listOfNotNull(
            "${f.vid.emoji} ${f.vid.title}",
            mesto,
            reysov?.let { "$it рейс." },
            obyom?.let { "${fmt(it)} м³" },
            ga?.let { "${fmt(it)} га" },
            chasy?.let { "${fmt(it)} ч" },
            rashod?.let { "⛽ ${fmt(it)} л" },
            zapravka?.let { "заправка ${fmt(it)} л" },
        ).joinToString(" · ")
    }

    /** Новый отчёт после отправленного: вид, дата, техника и напарники остаются (обычно тот же день). */
    fun novyy() {
        val f = _state.value.form
        _state.value = _state.value.copy(
            result = null,
            error = null,
            form = SmenaForm(vid = f.vid, dneyNazad = f.dneyNazad, tehnika = f.tehnika, tehnikaId = f.tehnikaId,
                sTehnikoy = f.sTehnikoy, naparniki = f.naparniki, delyankaItemId = f.delyankaItemId, uchastokId = f.uchastokId,
                probaId = f.probaId, pokazatVse = f.pokazatVse),
        )
    }

    fun delete(id: Int) {
        _state.value = _state.value.copy(deletingId = id)
        viewModelScope.launch {
            repository.deleteSmena(id).fold(
                onSuccess = {
                    _state.value = _state.value.copy(deletingId = null)
                    loadMoi()
                    repository.smenaSpravochnik().onSuccess { s -> _state.value = _state.value.copy(spravochnik = s) }
                },
                onFailure = { _state.value = _state.value.copy(deletingId = null, error = it.message ?: "Не удалилось") },
            )
        }
    }

    companion object {
        private const val KEY_SPRAV = "spravochnik"
        private const val KEY_VID = "vid"
        private const val KEY_TEHNIKA = "tehnika"
        private const val KEY_TEHNIKA_ID = "tehnika_id"
    }
}

internal fun fmt(v: Double): String =
    if (v == Math.floor(v) && kotlin.math.abs(v) < 1e9) v.toLong().toString()
    else "%.2f".format(java.util.Locale("ru"), v).trimEnd('0').trimEnd(',')
