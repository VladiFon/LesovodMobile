package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Строка GET /api/delyanki/for-map: где на карте уже заведена делянка (по кварталу и выделу). */
@Serializable
data class DelyankaMapRefDto(
    val kvartal: String? = null,
    val vydel: String? = null,
    val lesnichestvo: String? = null,
    @SerialName("delyanka_id") val delyankaId: Int,
    val nazvanie: String? = null,
    @SerialName("status_rabot") val statusRabot: String? = null,
    /** Вид рубки (ССР, УЗ, ПРЖ…) и вид пользования с цветами — сервер 29.09.2026+, у старого null. */
    @SerialName("vid_rubki_kod") val vidRubkiKod: String? = null,
    @SerialName("vid_rubki") val vidRubki: String? = null,
    @SerialName("vid_rubki_color") val vidRubkiColor: String? = null,
    @SerialName("gruppa_label") val gruppaLabel: String? = null,
    @SerialName("gruppa_color") val gruppaColor: String? = null,
    /** Сторонняя заготовка (сервер 03.11.2026+): кто рубит; null — делянка наша. */
    val zagotovitel: String? = null,
    /** false — наряды по сторонней делянке пишет сам заготовитель: для отчёта не предлагаем. */
    @SerialName("nashi_naryady") val nashiNaryady: Boolean = true,
)
