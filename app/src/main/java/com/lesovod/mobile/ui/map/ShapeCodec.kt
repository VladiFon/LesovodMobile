package com.lesovod.mobile.ui.map

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Бинарный формат кэша фигур: читается на порядок быстрее и занимает меньше памяти, чем тот же GeoJSON. */
object ShapeCodec {
    private const val MAGIC = 0x4C534803 // версия формата в последнем байте
    // прежний формат (до 0.21.0, без пометок л/к) читаем как есть: выделы в кэше нужны в лесу без связи
    private const val MAGIC_V2 = 0x4C534802

    fun write(shapes: List<MapShape>, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(MAGIC)
        out.writeInt(shapes.size)
        for (s in shapes) {
            out.writeByte(s.kind.ordinal)
            out.writeUTF(s.kvartal)
            out.writeBoolean(s.vydel != null)
            s.vydel?.let { out.writeUTF(it) }
            out.writeBoolean(s.statusColor != null)
            s.statusColor?.let { out.writeInt(it) }
            out.writeBoolean(s.lk)
            out.writeBoolean(s.pometka != null)
            s.pometka?.let { out.writeUTF(it) }
            out.writeInt(s.rings.size)
            for (ring in s.rings) {
                out.writeInt(ring.size)
                for (v in ring) out.writeDouble(v)
            }
        }
        out.flush()
    }

    fun read(input: InputStream): List<MapShape> {
        val inp = DataInputStream(input)
        val magic = inp.readInt()
        require(magic == MAGIC || magic == MAGIC_V2) { "Неизвестный формат кэша" }
        val v3 = magic == MAGIC
        val count = inp.readInt()
        val result = ArrayList<MapShape>(count)
        repeat(count) {
            val kind = ShapeKind.entries[inp.readByte().toInt()]
            val kvartal = inp.readUTF()
            val vydel = if (inp.readBoolean()) inp.readUTF() else null
            val color = if (inp.readBoolean()) inp.readInt() else null
            val lk = v3 && inp.readBoolean()
            val pometka = if (v3 && inp.readBoolean()) inp.readUTF() else null
            val rings = List(inp.readInt()) { DoubleArray(inp.readInt()).also { ring -> for (i in ring.indices) ring[i] = inp.readDouble() } }
            buildShape(kind, kvartal, vydel, color, rings, lk = lk, pometka = pometka)?.let(result::add)
        }
        return result
    }
}
