package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Игровой режим (GET /api/bot/igra/moy, 03.11.2026): карточка героя, рейтинг с соперником
 * и челленджи команды. Режим включается в Профиле, по умолчанию выключен — сервер отдаёт
 * данные всегда, игровой вид только показывает их.
 */
@Serializable
data class IgraDto(
    val geroy: GeroyDto? = null,
    val reyting: IgraReytingDto = IgraReytingDto(),
    val chellendzhi: List<IgraChellendzhDto> = emptyList(),
    @SerialName("zvaniya_komandy") val zvaniyaKomandy: List<String> = emptyList(),
)

@Serializable
data class GeroyDto(
    val id: Int,
    val fio: String,
    val dolzhnost: String? = null,
    val uroven: Int = 1,
    val zvanie: String = "",
    val ochki: Double = 0.0,
    val ot: Double = 0.0,
    @SerialName("do") val doOchkov: Double = 0.0,
    val pct: Double = 0.0,
    val klass: String = "",
    val god: GeroyGodDto = GeroyGodDto(),
    val navyki: List<GeroyNavykDto> = emptyList(),
    val mesta: List<GeroyMestoDto> = emptyList(),
    val seychas: GeroySeychasDto? = null,
    val naparnik: GeroyNaparnikDto? = null,
    val forma: List<GeroyFormaDto> = emptyList(),
    @SerialName("mesto_mesyaca") val mestoMesyaca: Int? = null,
    @SerialName("v_igre_mesyaca") val vIgreMesyaca: Int? = null,
    val zveno: GeroyZvenoDto? = null,
    val achivki: List<GeroyAchivkaDto> = emptyList(),
    val harakteristiki: Map<String, GeroyHarDto> = emptyMap(),
    val achivok: Int = 0,
)

@Serializable
data class GeroyGodDto(val dni: Double = 0.0, val kuby: Double = 0.0, val ochki: Double = 0.0)

@Serializable
data class GeroyNavykDto(val vid: String, val dni: Double = 0.0)

@Serializable
data class GeroyMestoDto(val mesto: String, val dni: Double = 0.0, val posledniy: String? = null)

@Serializable
data class GeroySeychasDto(val mesto: String? = null, val vid: String? = null, val data: String? = null)

@Serializable
data class GeroyNaparnikDto(val fio: String, val dney: Double = 0.0)

@Serializable
data class GeroyFormaDto(val nedelya: String, val ochki: Double = 0.0)

@Serializable
data class GeroyZvenoDto(val nazvanie: String, val emoji: String = "")

@Serializable
data class GeroyAchivkaDto(
    val kod: String,
    val z: String = "",
    val title: String = "",
    val opis: String = "",
    val est: Boolean = false,
    val progress: Double = 0.0,
    val tek: Double = 0.0,
    val nado: Double = 0.0,
)

@Serializable
data class GeroyHarDto(val znach: Double = 0.0, val pct: Double = 0.0)

@Serializable
data class IgraReytingDto(
    val period: String = "mesyac",
    val periody: Map<String, String> = emptyMap(),
    val ot: String? = null,
    @SerialName("do") val doDaty: String? = null,
    val top: List<IgraIgrokDto> = emptyList(),
    val ya: IgraYaDto? = null,
    @SerialName("v_igre") val vIgre: Int = 0,
    val zvenya: List<IgraZvenoDto> = emptyList(),
)

@Serializable
data class IgraIgrokDto(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val fio: String,
    val mesto: Int = 0,
    val ochki: Double = 0.0,
    val znachki: List<String> = emptyList(),
)

@Serializable
data class IgraOtryvDto(val fio: String = "", val otryv: Double = 0.0)

@Serializable
data class IgraYaDto(
    @SerialName("sotrudnik_id") val sotrudnikId: Int,
    val fio: String,
    val mesto: Int = 0,
    val ochki: Double = 0.0,
    val znachki: List<String> = emptyList(),
    val sopernik: IgraOtryvDto? = null,
    val dogonyaet: IgraOtryvDto? = null,
    val sdvig: Double? = null,
    @SerialName("ochki_po") val ochkiPo: Map<String, Double> = emptyMap(),
)

@Serializable
data class IgraZvenoDto(val nazvanie: String, val emoji: String = "", val srednee: Double = 0.0)

@Serializable
data class IgraChellendzhDto(
    val nazvanie: String,
    val emoji: String? = null,
    val pct: Double = 0.0,
    val status: String? = null,
    val temp: String? = null,
    val fakt: Double? = null,
    val cel: Double? = null,
    val ed: String? = null,
    @SerialName("do") val doDaty: String? = null,
    val priz: String? = null,
)
