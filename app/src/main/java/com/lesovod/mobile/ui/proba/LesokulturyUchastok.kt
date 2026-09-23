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
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    @SerialName("delyanka_nazvanie") val delyankaNazvanie: String? = null,
    val ploshad: Double? = null,
)

/** Участок лесных культур — с готовой подписью для чипа/выпадающего списка и площадью для расчётов. */
data class LesokulturyUchastok(val id: Int, val label: String, val ploshad: Double? = null)

/** Подпись вида "12/5, Сосна (Делянка №3)" — название делянки в скобках, если оно есть. */
fun LesokulturyUchastokDto.toLesokulturyUchastok(): LesokulturyUchastok {
    val place = "${kvartal.orEmpty()}/${vydel.orEmpty()}".takeIf { it != "/" }
    val base = listOfNotNull(place, glavnayaPoroda?.takeIf { it.isNotBlank() }).joinToString(", ")
    val label = delyankaNazvanie?.takeIf { it.isNotBlank() }?.let { "$base ($it)" } ?: base
    return LesokulturyUchastok(id, label.ifBlank { "Участок №$id" }, ploshad)
}
