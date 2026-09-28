package com.lesovod.mobile.ui.proba

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Подтверждённая схема ответа GET /api/lesokultury/uchastki. Здесь только поля, нужные экрану
 * пробы и карточкам инвентаризации/перевода — остальные (status, last_uhod_* и т.д.) сервер тоже
 * отдаёт, но они пока не нужны, а лишние ключи JSON-парсер просто игнорирует.
 */
@Serializable
data class LesokulturyUchastokDto(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("delyanka_nazvanie") val delyankaNazvanie: String? = null,
    val ploshad: Double? = null,
    @SerialName("god_sozdaniya") val godSozdaniya: String? = null,
)

/**
 * Участок лесных культур — с готовой подписью для списка/чипа, площадью для расчётов, кварталом/
 * выделом (подставляются в пробу рубок ухода вместо ручного ввода) и годом создания культур
 * (для фильтра по годам).
 */
data class LesokulturyUchastok(
    val id: Int,
    val label: String,
    val ploshad: Double? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    val god: String? = null,
)

/** Год из god_sozdaniya: поле текстовое, встречается и "2023", и "2023 (весна)". */
fun extractGod(raw: String?): String? = raw?.let { Regex("""(19|20)\d{2}""").find(it)?.value }

/** Подпись вида "12/5, Сосна, 2021 г. (Делянка №3)" — название делянки в скобках, если оно есть. */
fun LesokulturyUchastokDto.toLesokulturyUchastok(): LesokulturyUchastok {
    val place = "${kvartal.orEmpty()}/${vydel.orEmpty()}".takeIf { it != "/" }
    val god = extractGod(godSozdaniya)
    val base = listOfNotNull(
        place,
        glavnayaPoroda?.takeIf { it.isNotBlank() },
        god?.let { "$it г." },
        lesnichestvo?.takeIf { it.isNotBlank() },
    ).joinToString(", ")
    val label = delyankaNazvanie?.takeIf { it.isNotBlank() }?.let { "$base ($it)" } ?: base
    return LesokulturyUchastok(
        id = id,
        label = label.ifBlank { "Участок №$id" },
        ploshad = ploshad,
        kvartal = kvartal?.takeIf { it.isNotBlank() },
        vydel = vydel?.takeIf { it.isNotBlank() },
        god = god,
    )
}
