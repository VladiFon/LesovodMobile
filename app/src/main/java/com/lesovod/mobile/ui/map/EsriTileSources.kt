package com.lesovod.mobile.ui.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

/**
 * Esri ArcGIS Online — публичные тайлы без API-ключа и без аккаунта
 * (обсуждали отдельно: Google Maps SDK потребовал бы свой платёжный
 * аккаунт и ключ, который я не могу завести сама). Комбинация базового
 * слоя World_Imagery (спутник) и прозрачного оверлея с подписями/
 * границами и даёт "гибридный" режим — то же самое, что MAP_TYPE_HYBRID
 * у Google, только собранное из двух слоёв вручную.
 *
 * URL-схема ArcGIS — .../tile/{z}/{y}/{x} (порядок y/x обратный обычному
 * z/x/y у большинства тайл-серверов), поэтому нельзя взять готовый
 * XYTileSource — нужен свой getTileURLString.
 */
class EsriTileSource(name: String, private val baseUrl: String) : OnlineTileSourceBase(
    name,
    0,
    19,
    256,
    "",
    arrayOf(baseUrl),
    "Esri, Maxar, Earthstar Geographics",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
            TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
    ),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl/tile/$zoom/$y/$x"
    }
}

val EsriSatelliteTileSource = EsriTileSource(
    "EsriWorldImagery",
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer",
)

val EsriLabelsTileSource = EsriTileSource(
    "EsriBoundariesAndPlaces",
    "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer",
)
