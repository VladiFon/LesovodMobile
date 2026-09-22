package com.lesovod.mobile.ui.proba

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Подтверждённая схема ответа GET /api/lesokultury/uchastki. Здесь только поля, нужные для
 * подписи чипа в пробе — остальные (ploshad, status, last_uhod_* и т.д.) сервер тоже отдаёт,
 * но экрану они пока не нужны, а лишние ключи JSON-парсер просто игнорирует.
 */
@Serializable
data class LesokulturyUchastokDto(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("delyanka_nazvanie") val delyankaNazvanie: String? = null,
)

/** Участок лесных культур для выбора в пробе — с готовой подписью для чипа. */
data class LesokulturyUchastok(val id: Int, val label: String)

/** Подпись вида "12/5, Сосна (Делянка №3)" — название делянки в скобках, если оно есть. */
fun LesokulturyUchastokDto.toLesokulturyUchastok(): LesokulturyUchastok {
    val place = "${kvartal.orEmpty()}/${vydel.orEmpty()}".takeIf { it != "/" }
    val base = listOfNotNull(place, glavnayaPoroda?.takeIf { it.isNotBlank() }).joinToString(", ")
    val label = delyankaNazvanie?.takeIf { it.isNotBlank() }?.let { "$base ($it)" } ?: base
    return LesokulturyUchastok(id, label.ifBlank { "Участок №$id" })
}
