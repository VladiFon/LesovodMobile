package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Строка табеля на один день (GET/POST /api/tabel/day) — одна на каждого активного
 * сотрудника, независимо от того, заполнена она уже или нет (zapisId == null, если ещё
 * не заполнена на этот день). Место работы — либо делянка (delyankaItemId), либо участок
 * лесных культур (lesokulturyUchastokId), никогда оба сразу — см. app/routers/tabel.py.
 */
@Serializable
data class TabelDayEntryDto(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val fio: String,
    /** В справочнике сотрудников должность может быть не указана (NULL). */
    val dolzhnost: String? = null,
    @SerialName("zapis_id") val zapisId: Int? = null,
    val status: String? = null,
    val kommentariy: String? = null,
    @SerialName("entered_by") val enteredBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("delyanka_item_id") val delyankaItemId: Int? = null,
    @SerialName("d_kvartal") val dKvartal: String? = null,
    @SerialName("d_vydel") val dVydel: String? = null,
    @SerialName("lesokultury_uchastok_id") val lesokulturyUchastokId: Int? = null,
    @SerialName("lku_kvartal") val lkuKvartal: String? = null,
    @SerialName("lku_vydel") val lkuVydel: String? = null,
    @SerialName("lku_glavnaya_poroda") val lkuGlavnayaPoroda: String? = null,
    @SerialName("vid_raboty_id") val vidRabotyId: Int? = null,
    @SerialName("vid_raboty_nazvanie") val vidRabotyNazvanie: String? = null,
    @SerialName("brigada_id") val brigadaId: Int? = null,
    @SerialName("brigada_nazvanie") val brigadaNazvanie: String? = null,
    @SerialName("is_brigadir") val isBrigadir: Boolean = false,
)

/** Выдел делянки для места работы (GET /api/tabel/delyanki) — itemId и сохраняется в табель. */
@Serializable
data class TabelDelyankaDto(
    @SerialName("item_id") val itemId: Int,
    @SerialName("delyanka_id") val delyankaId: Int? = null,
    val nazvanie: String? = null,
    val lesnichestvo: String? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    @SerialName("lesoseka_nomer") val lesosekaNomer: String? = null,
    val status: String? = null,
) {
    val label: String
        get() = listOfNotNull(
            "кв. ${kvartal.orEmpty()} · выд. ${vydel.orEmpty()}",
            lesosekaNomer?.takeIf { it.isNotBlank() }?.let { "лесосека $it" },
            nazvanie?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
}

@Serializable
data class TabelBrigadaMemberDto(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val fio: String,
    val dolzhnost: String? = null,
)

/** Бригада на дату (GET /api/tabel/brigady) — состав, бригадир и выделы делянки из назначения. */
@Serializable
data class TabelBrigadaDto(
    val id: Int,
    val nazvanie: String,
    @SerialName("brigadir_sotrudnik_id") val brigadirSotrudnikId: Int? = null,
    @SerialName("brigadir_fio") val brigadirFio: String? = null,
    val sostav: List<TabelBrigadaMemberDto> = emptyList(),
    @SerialName("delyanka_nazvanie") val delyankaNazvanie: String? = null,
    val mesta: List<TabelDelyankaDto> = emptyList(),
)

/** Одна строка пакетного сохранения — см. TabelEntryIn на бэкенде. */
@Serializable
data class TabelEntrySaveDto(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val status: String,
    @SerialName("delyanka_item_id") val delyankaItemId: Int? = null,
    @SerialName("lesokultury_uchastok_id") val lesokulturyUchastokId: Int? = null,
    @SerialName("vid_raboty_id") val vidRabotyId: Int? = null,
    val kommentariy: String = "",
)

/** Тело POST /api/tabel/day — пакетное сохранение табеля на один день. */
@Serializable
data class TabelDaySaveRequest(
    val data: String,
    val entries: List<TabelEntrySaveDto>,
)

/** Строка справочника видов работ (GET/POST /api/tabel/vidy-rabot). */
@Serializable
data class VidRabotyDto(
    val id: Int,
    val nazvanie: String,
)

/** Тело POST /api/tabel/vidy-rabot — создание (или получение уже существующего, по имени) вида работы. */
@Serializable
data class VidRabotyCreateRequest(
    val nazvanie: String,
)

/**
 * Строка GET /api/tabel/lesokultury-uchastki — участок лесных культур для пикера
 * «место работы». Поля не типизированы жёстко на бэкенде (legacy_db.get_lesokultury_uchastki),
 * поэтому берём только то, что нужно для отображения и выбора; лишние поля ответа игнорируются.
 */
@Serializable
data class TabelLesokulturyUchastokDto(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("delyanka_nazvanie") val nazvanie: String? = null,
)
