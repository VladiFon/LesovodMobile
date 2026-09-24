package com.lesovod.mobile.ui.tabel

import com.lesovod.mobile.data.network.dto.DelyankaByLocationDto
import com.lesovod.mobile.data.network.dto.TabelDayEntryDto
import com.lesovod.mobile.data.network.dto.TabelEntryRequest
import com.lesovod.mobile.data.network.dto.TabelLesokulturyUchastokDto

/** Пять статусов дня — строго эти русские строки уходят на сервер как есть. */
enum class TabelStatus(val wireValue: String, val label: String) {
    WORK("работал", "Работал"),
    NOT_WORK("не работал", "Не работал"),
    SICK("больничный", "Больничный"),
    VACATION("отпуск", "Отпуск"),
    DAY_OFF("выходной", "Выходной");

    companion object {
        fun fromWire(value: String?): TabelStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Куда записано место работы — делянка ИЛИ участок лесных культур, никогда оба сразу. */
sealed interface TabelPlace {
    val label: String

    data class Delyanka(val itemId: Int, override val label: String) : TabelPlace
    data class Lesokultury(val uchastokId: Int, override val label: String) : TabelPlace
}

/** Одна строка табеля в UI-состоянии экрана — на сотрудника, с признаком несохранённых правок. */
data class TabelEmployee(
    val sotrudnikId: Int,
    val fio: String,
    val dolzhnost: String,
    /** "уже отметился: работаю" — только справочно, из mobile_status DTO. */
    val mobileStatusHint: String? = null,
    val status: TabelStatus? = null,
    val place: TabelPlace? = null,
    val vidRabotyId: Int? = null,
    val vidRabotyLabel: String? = null,
    val kommentariy: String = "",
    val dirty: Boolean = false,
)

fun TabelDayEntryDto.toEmployee(): TabelEmployee {
    val place: TabelPlace? = when {
        delyankaItemId != null -> TabelPlace.Delyanka(
            itemId = delyankaItemId,
            label = listOfNotNull(dKvartal?.let { "кв. $it" }, dVydel?.let { "выд. $it" })
                .joinToString(" · ").ifBlank { "делянка" },
        )
        lesokulturyUchastokId != null -> TabelPlace.Lesokultury(
            uchastokId = lesokulturyUchastokId,
            label = listOfNotNull(
                lkuKvartal?.let { "кв. $it" },
                lkuVydel?.let { "выд. $it" },
                lkuGlavnayaPoroda?.takeIf { it.isNotBlank() } ?: "культуры",
            ).joinToString(" · "),
        )
        else -> null
    }
    return TabelEmployee(
        sotrudnikId = sotrudnikId,
        fio = fio,
        dolzhnost = dolzhnost,
        mobileStatusHint = mobileStatus?.takeIf { it.isNotBlank() }?.let { "уже отметился: $it" },
        status = TabelStatus.fromWire(status),
        place = place,
        vidRabotyId = vidRabotyId,
        vidRabotyLabel = vidRabotyNazvanie,
        kommentariy = kommentariy.orEmpty(),
        dirty = false,
    )
}

/** null, если статус ещё не проставлен — такую строку вообще не включаем в сохранение. */
fun TabelEmployee.toEntryRequest(): TabelEntryRequest? {
    val status = status ?: return null
    return TabelEntryRequest(
        sotrudnikId = sotrudnikId,
        status = status.wireValue,
        delyankaItemId = (place as? TabelPlace.Delyanka)?.itemId,
        lesokulturyUchastokId = (place as? TabelPlace.Lesokultury)?.uchastokId,
        vidRabotyId = vidRabotyId,
        kommentariy = kommentariy,
    )
}

/** Совпадает ли строка сотрудника с поисковым текстом по ФИО/должности (подстрокой, без учёта регистра). */
fun TabelEmployee.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return fio.lowercase().contains(q) || dolzhnost.lowercase().contains(q)
}

fun DelyankaByLocationDto.toPlaceLabel(kvartal: String, vydel: String): String =
    listOfNotNull("кв. $kvartal", "выд. $vydel", nazvanie?.takeIf { it.isNotBlank() }).joinToString(" · ")

fun TabelLesokulturyUchastokDto.toPlaceLabel(): String =
    listOfNotNull(
        kvartal?.let { "кв. $it" },
        vydel?.let { "выд. $it" },
        "культуры",
    ).joinToString(" · ")
