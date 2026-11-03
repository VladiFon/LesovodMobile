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
    UHOD("uhod", "Уход за культурами", "🌱"),
    POCHVA("pochva", "Подготовка почвы", "🪚"),
    REMONT("remont", "Ремонт", "🔧");

    val poDelyanke get() = this == TRELEVKA || this == NASELENIYU
    val poUchastku get() = this == UHOD || this == POCHVA

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

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
    val kommentariy: String = "",
    val naparniki: Set<Int> = emptySet(),
)

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

    init {
        val lastVid = cache.getString(KEY_VID, null)?.let { SmenaVid.of(it) }
        val lastTehnika = cache.getString(KEY_TEHNIKA, "").orEmpty()
        _state.value = _state.value.copy(form = SmenaForm(vid = lastVid ?: SmenaVid.TRELEVKA, tehnika = lastTehnika))
        cache.getString(KEY_SPRAV, null)?.let { raw ->
            runCatching { json.decodeFromString<SmenaSpravochnikDto>(raw) }.getOrNull()?.let {
                _state.value = _state.value.copy(spravochnik = it, spravochnikIzKesha = true, loading = true)
            }
        }
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, loadError = null)
        viewModelScope.launch {
            repository.smenaSpravochnik().fold(
                onSuccess = { s ->
                    cache.edit { putString(KEY_SPRAV, json.encodeToString(s)) }
                    _state.value = _state.value.copy(loading = false, spravochnik = s, spravochnikIzKesha = false)
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
        val err = when {
            f.chasy.isNotBlank() && (chasy == null || chasy < 0 || chasy > 24) -> "Часы — числом от 0 до 24"
            f.reysov.isNotBlank() && (reysov == null || reysov < 0) -> "Рейсы — целым числом"
            f.vid.poDelyanke && f.delyankaItemId == null -> "Выбери делянку"
            f.vid.poDelyanke && (obyom == null || obyom <= 0) -> "Укажи кубы"
            f.vid == SmenaVid.NASELENIYU && f.poluchatel.isBlank() -> "Укажи, кому везли"
            f.vid.poUchastku && f.uchastokId == null -> "Выбери участок"
            f.vid.poUchastku && !f.proyden && (ga == null || ga <= 0) -> "Укажи, сколько га сделал (или отметь «прошёл весь»)"
            f.vid == SmenaVid.REMONT && (chasy == null || chasy <= 0) -> "Укажи, сколько часов был ремонт"
            f.vid == SmenaVid.REMONT && f.tehnika.isBlank() && f.kommentariy.isBlank() -> "Напиши, что ремонтировали"
            else -> null
        }
        if (err != null) {
            _state.value = st.copy(error = err)
            return
        }
        val request = SmenaRequest(
            vid = f.vid.code,
            data = LocalDate.now().minusDays(f.dneyNazad.toLong()).toString(),
            delyankaItemId = if (f.vid.poDelyanke) f.delyankaItemId else null,
            lesokulturyUchastokId = if (f.vid.poUchastku) f.uchastokId else null,
            reysov = if (f.vid.poDelyanke) reysov else null,
            obyom = if (f.vid.poDelyanke) obyom else null,
            ga = if (f.vid.poUchastku) ga else null,
            chasy = chasy,
            poluchatel = f.poluchatel.trim().takeIf { f.vid == SmenaVid.NASELENIYU && it.isNotEmpty() },
            tehnika = f.tehnika.trim().takeIf { it.isNotEmpty() },
            kommentariy = f.kommentariy.trim().takeIf { it.isNotEmpty() },
            uchastokProyden = f.vid.poUchastku && f.proyden,
            naparniki = f.naparniki.toList(),
            clientId = UUID.randomUUID().toString(),
        )
        val opisanie = opisanie(f, obyom, ga, chasy, reysov)
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
        cache.edit { putString(KEY_TEHNIKA, f.tehnika.trim()) }
    }

    private fun opisanie(f: SmenaForm, obyom: Double?, ga: Double?, chasy: Double?, reysov: Int?): String {
        val s = _state.value.spravochnik
        val mesto = when {
            f.vid.poDelyanke -> s?.delyanki?.firstOrNull { it.itemId == f.delyankaItemId }?.label
            f.vid.poUchastku -> s?.uchastki?.firstOrNull { it.id == f.uchastokId }?.label
            else -> f.tehnika.trim().ifEmpty { null }
        }
        return listOfNotNull(
            "${f.vid.emoji} ${f.vid.title}",
            mesto,
            reysov?.let { "$it рейс." },
            obyom?.let { "${fmt(it)} м³" },
            ga?.let { "${fmt(it)} га" },
            chasy?.let { "${fmt(it)} ч" },
        ).joinToString(" · ")
    }

    /** Новый отчёт после отправленного: вид, дата, техника и напарники остаются (обычно тот же день). */
    fun novyy() {
        val f = _state.value.form
        _state.value = _state.value.copy(
            result = null,
            error = null,
            form = SmenaForm(vid = f.vid, dneyNazad = f.dneyNazad, tehnika = f.tehnika, naparniki = f.naparniki,
                delyankaItemId = f.delyankaItemId, uchastokId = f.uchastokId),
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
    }
}

internal fun fmt(v: Double): String =
    if (v == Math.floor(v) && kotlin.math.abs(v) < 1e9) v.toLong().toString()
    else "%.2f".format(java.util.Locale("ru"), v).trimEnd('0').trimEnd(',')
