package com.lesovod.mobile.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Пробная площадь на карте (сервер 26.10.2026+, GET/POST /api/proby). Тех. приёмка,
 * инвентаризация 1-го и 3-го года идут на одной и той же пробе — поэтому она хранится на
 * сервере и видна всем, а не только тому, кто её поставил.
 */
@Serializable
data class KartaProbaDto(
    val id: Int,
    val nomer: String? = null,
    @SerialName("uchastok_id") val uchastokId: Int? = null,
    val uchastok: KartaProbaUchastokDto? = null,
    val lesnichestvo: String? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    val lat: Double,
    val lon: Double,
    val forma: String = "krug",
    @SerialName("forma_label") val formaLabel: String? = null,
    @SerialName("radius_m") val radiusM: Double? = null,
    @SerialName("dlina_m") val dlinaM: Double? = null,
    @SerialName("shirina_m") val shirinaM: Double? = null,
    val azimut: Double? = null,
    @SerialName("ploshad_m2") val ploshadM2: Double? = null,
    val geometry: GeoJsonGeometry? = null,
    @SerialName("tochnost_m") val tochnostM: Double? = null,
    val orientir: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("avtor_fio") val avtorFio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val etapy: List<KartaProbaEtapDto> = emptyList(),
    val istoriya: List<KartaProbaIstoriyaDto> = emptyList(),
    @SerialName("mozhno_pravit") val mozhnoPravit: Boolean = false,
    @SerialName("uchastok_avto") val uchastokAvto: Boolean = false,
)

@Serializable
data class KartaProbaUchastokDto(
    val id: Int,
    val kvartal: String? = null,
    val vydel: String? = null,
    @SerialName("god_sozdaniya") val godSozdaniya: String? = null,
    @SerialName("glavnaya_poroda") val glavnayaPoroda: String? = null,
    val status: String? = null,
)

/** Тех. приёмка / инвентаризация 1-го / 3-го года: была ли на участке и была ли именно на этой пробе. */
@Serializable
data class KartaProbaEtapDto(
    val tip: String,
    val data: String? = null,
    @SerialName("prizhivaemost_pct") val prizhivaemostPct: Double? = null,
    val vypolneno: Boolean = false,
    @SerialName("na_probe") val naProbe: Boolean = false,
)

@Serializable
data class KartaProbaIstoriyaDto(
    val id: Int,
    val tip: String? = null,
    val data: String? = null,
    @SerialName("prizhivaemost_pct") val prizhivaemostPct: Double? = null,
)

/** POST /api/proby. Для обхода — tochki [[lon, lat], …], для круга/прямоугольника — центр и размеры. */
@Serializable
data class KartaProbaCreateRequest(
    val forma: String,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("radius_m") val radiusM: Double? = null,
    @SerialName("dlina_m") val dlinaM: Double? = null,
    @SerialName("shirina_m") val shirinaM: Double? = null,
    val azimut: Double? = null,
    val tochki: List<List<Double>>? = null,
    @SerialName("tochnost_m") val tochnostM: Double? = null,
    val nomer: String? = null,
    @SerialName("uchastok_id") val uchastokId: Int? = null,
    val lesnichestvo: String? = null,
    val kvartal: String? = null,
    val vydel: String? = null,
    val orientir: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
)

/** PATCH /api/proby/{id} — «перенести пробу в моё место»: новый центр, форма та же. */
@Serializable
data class KartaProbaMoveRequest(
    val lat: Double,
    val lon: Double,
    @SerialName("tochnost_m") val tochnostM: Double? = null,
)
