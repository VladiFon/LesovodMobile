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
    @SerialName("vydel_staryy") val vydelStaryy: String? = null,
    val podvydel: String? = null,
    @SerialName("metod_sozdaniya") val metodSozdaniya: String? = null,
    @SerialName("sposob_obrabotki") val sposobObrabotki: String? = null,
    @SerialName("shema_mezhdu_ryadami") val shemaMezhduRyadami: Double? = null,
    @SerialName("shema_v_ryadu") val shemaVRyadu: Double? = null,
    @SerialName("gustota_posadki") val gustotaPosadki: Double? = null,
    @SerialName("posadochnyy_material") val posadochnyyMaterial: String? = null,
    @SerialName("sostav_formula") val sostavFormula: String? = null,
)

/** Поля участка для ведомостей текущих изменений — видны и дописываются в карточке на телефоне. */
data class UchastokPolya(
    val vydelStaryy: String? = null,
    val podvydel: String? = null,
    val metodSozdaniya: String? = null,
    val sposobObrabotki: String? = null,
    val shemaMezhduRyadami: Double? = null,
    val shemaVRyadu: Double? = null,
    val gustotaPosadki: Double? = null,
    val posadochnyyMaterial: String? = null,
    val sostavFormula: String? = null,
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
    val polya: UchastokPolya = UchastokPolya(),
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
        polya = UchastokPolya(
            vydelStaryy = vydelStaryy?.takeIf { it.isNotBlank() },
            podvydel = podvydel?.takeIf { it.isNotBlank() },
            metodSozdaniya = metodSozdaniya?.takeIf { it.isNotBlank() },
            sposobObrabotki = sposobObrabotki?.takeIf { it.isNotBlank() },
            shemaMezhduRyadami = shemaMezhduRyadami,
            shemaVRyadu = shemaVRyadu,
            gustotaPosadki = gustotaPosadki,
            posadochnyyMaterial = posadochnyyMaterial?.takeIf { it.isNotBlank() },
            sostavFormula = sostavFormula?.takeIf { it.isNotBlank() },
        ),
    )
}
