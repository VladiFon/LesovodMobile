package com.lesovod.mobile.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class ShapeCodecTest {

    private val ring = doubleArrayOf(54.0, 27.0, 54.0, 27.1, 54.1, 27.1, 54.0, 27.0)

    @Test
    fun pometkaILkSohranyayutsya() {
        val shapes = listOf(
            buildShape(ShapeKind.LESOSEKA, "71", "2", 0x112233, listOf(ring), lk = true, pometka = "gisleshoz:10")!!,
            buildShape(ShapeKind.VYDEL, "71", "3", null, listOf(ring))!!,
        )
        val out = ByteArrayOutputStream()
        ShapeCodec.write(shapes, out)
        val back = ShapeCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, back.size)
        assertTrue(back[0].lk)
        assertEquals("gisleshoz:10", back[0].pometka)
        assertFalse(back[1].lk)
        assertNull(back[1].pometka)
    }

    @Test
    fun staryyKeshChitaetsya() {
        // формат до 0.21.0: без признаков л/к и пометки — карта без связи не должна пропасть
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(0x4C534802)
            writeInt(1)
            writeByte(ShapeKind.VYDEL.ordinal)
            writeUTF("71")
            writeBoolean(true); writeUTF("2")
            writeBoolean(false)
            writeInt(1)
            writeInt(ring.size)
            ring.forEach { writeDouble(it) }
            flush()
        }
        val back = ShapeCodec.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, back.size)
        assertEquals("2", back[0].vydel)
        assertFalse(back[0].lk)
    }
}
