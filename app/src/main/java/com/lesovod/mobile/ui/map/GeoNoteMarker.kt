package com.lesovod.mobile.ui.map

import android.graphics.Color as AndroidColor
import com.lesovod.mobile.data.network.dto.GeoNoteDto
import kotlinx.serialization.Serializable

/** Метка рабочего на карте (POST /api/bot/geo-notes) — точка с необязательным текстом и/или фото. */
@Serializable
data class GeoNoteMarker(
    val lat: Double,
    val lon: Double,
    val noteText: String? = null,
    val photoPath: String? = null,
    val authorFio: String? = null,
    val createdAt: String? = null,
    val id: Int = 0,
    val kategoriya: String? = null,
    /** Относительный адрес фото на сервере (GET /api/bot/geo-notes/{id}/photo). */
    val photoUrl: String? = null,
    /** Метка ещё лежит в офлайн-очереди («⏳ ждёт связи») — на сервер не ушла. */
    val pending: Boolean = false,
) {
    val category: GeoNoteCategory get() = GeoNoteCategory.fromCode(kategoriya)
}

fun GeoNoteDto.toMarker() = GeoNoteMarker(
    lat = lat,
    lon = lon,
    noteText = noteText,
    authorFio = authorFio,
    createdAt = createdAt,
    id = id,
    kategoriya = kategoriya,
    photoUrl = photoUrl?.takeIf { hasPhoto },
)

/**
 * Типы меток — те же коды и цвета, что на сервере (app/map_features.py:GEO_NOTE_CATEGORIES)
 * и в стиле слоя QGIS. Буква рисуется внутри кружка, чтобы тип был виден и без цвета.
 */
enum class GeoNoteCategory(val code: String, val label: String, val colorHex: String, val glyph: String) {
    ZAMETKA("zametka", "Заметка", "#DF964E", "З"),
    VETROVAL("vetroval", "Ветровал / бурелом", "#8D6E63", "В"),
    POZHAR("pozhar", "Пожар / гарь", "#E53935", "П"),
    SAMOVOLNAYA_RUBKA("samovolnaya_rubka", "Самовольная рубка", "#8E24AA", "С"),
    VREDITELI("vrediteli", "Вредители / болезни", "#FB8C00", "Б"),
    DOROGA("doroga", "Плохая дорога / проезд", "#546E7A", "Д"),
    SKLAD("sklad", "Склад / штабель", "#1E88E5", "Ш"),
    GRANICA("granica", "Граница / столб", "#00897B", "Г");

    val color: Int get() = AndroidColor.parseColor(colorHex)

    companion object {
        fun fromCode(code: String?): GeoNoteCategory = entries.firstOrNull { it.code == code } ?: ZAMETKA
    }
}
