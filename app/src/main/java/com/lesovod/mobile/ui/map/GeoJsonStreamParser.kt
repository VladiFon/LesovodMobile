package com.lesovod.mobile.ui.map

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Читает GeoJSON потоком и сразу собирает [MapShape] — без дерева JsonElement, которое для
 * слоёв выделов раздувало память в разы (на повторном открытии из кэша это давало зависание и вылеты).
 */
object GeoJsonStreamParser {
    /** layerNameContains — оставить только объекты, у которых properties.layer_name содержит подстроку. */
    fun parse(input: InputStream, kind: ShapeKind, layerNameContains: String? = null): List<MapShape> {
        val shapes = ArrayList<MapShape>()
        JsonReader(InputStreamReader(input, Charsets.UTF_8).buffered(64 * 1024)).use { reader ->
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "features" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray()
                    while (reader.hasNext()) readFeature(reader, kind, layerNameContains)?.let(shapes::add)
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return shapes
    }

    private fun readFeature(r: JsonReader, kind: ShapeKind, layerNameContains: String?): MapShape? {
        var rings: List<DoubleArray> = emptyList()
        val props = HashMap<String, String>()
        var rawNumVds: String? = null

        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "geometry" -> if (r.peek() == JsonToken.BEGIN_OBJECT) rings = readGeometry(r) else r.skipValue()
                "properties" -> if (r.peek() == JsonToken.BEGIN_OBJECT) rawNumVds = readProperties(r, props) else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject()

        if (layerNameContains != null && props["layer_name"]?.contains(layerNameContains, ignoreCase = true) != true) return null
        return shapeFromProperties(kind, props::get, rawNumVds, rings)
    }

    private fun readGeometry(r: JsonReader): List<DoubleArray> {
        val acc = CoordAcc()
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "coordinates" && r.peek() == JsonToken.BEGIN_ARRAY) readNode(r, acc) else r.skipValue()
        }
        r.endObject()
        return acc.outers
    }

    /** Плоские скалярные свойства → props; из вложенного raw достаём только num_vds. */
    private fun readProperties(r: JsonReader, props: MutableMap<String, String>): String? {
        var rawNumVds: String? = null
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            when (r.peek()) {
                JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN -> readScalar(r)?.let { props[name] = it }
                JsonToken.BEGIN_OBJECT -> if (name == "raw") {
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() == "num_vds") rawNumVds = readScalar(r) else r.skipValue()
                    }
                    r.endObject()
                } else {
                    r.skipValue()
                }
                JsonToken.NULL -> r.nextNull()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return rawNumVds
    }

    private fun readScalar(r: JsonReader): String? = when (r.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        JsonToken.NULL -> { r.nextNull(); null }
        else -> { r.skipValue(); null }
    }

    private class CoordAcc {
        var buf = DoubleArray(256)
        var n = 0
        var lastRing: DoubleArray = EMPTY
        val outers = ArrayList<DoubleArray>()

        fun push(lat: Double, lon: Double) {
            if (n + 2 > buf.size) buf = buf.copyOf(buf.size * 2)
            buf[n++] = lat
            buf[n++] = lon
        }
    }

    /**
     * Обходит вложенные массивы координат, не строя дерева. Возвращает глубину прочитанного массива:
     * 1 — точка, 2 — кольцо, 3 — полигон, 4 — мультиполигон. У каждого полигона берём только внешнее
     * кольцо (без дырок): для контура на телефоне этого достаточно.
     */
    private fun readNode(r: JsonReader, acc: CoordAcc): Int {
        r.beginArray()
        if (r.peek() == JsonToken.NUMBER) {
            val lon = r.nextDouble()
            val lat = r.nextDouble()
            while (r.hasNext()) r.skipValue() // высота и прочее
            r.endArray()
            acc.push(lat, lon)
            return 1
        }

        var childDepth = 0
        var firstRing: DoubleArray? = null
        while (r.hasNext()) {
            childDepth = readNode(r, acc)
            if (childDepth == 2 && firstRing == null) firstRing = acc.lastRing
        }
        r.endArray()

        return when (childDepth) {
            0 -> { acc.lastRing = EMPTY; 2 }
            1 -> { acc.lastRing = acc.buf.copyOf(acc.n); acc.n = 0; 2 }
            2 -> { firstRing?.takeIf { it.isNotEmpty() }?.let(acc.outers::add); 3 }
            else -> childDepth + 1
        }
    }

    private val EMPTY = DoubleArray(0)
}
