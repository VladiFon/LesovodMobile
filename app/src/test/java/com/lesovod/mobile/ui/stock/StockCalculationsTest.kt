package com.lesovod.mobile.ui.stock

import com.lesovod.mobile.data.network.dto.PorodaRemainingDto
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCalculationsTest {

    @Test
    fun `наряд равен ЕГАИС - без расхождения, остаток берётся из сервера`() {
        val stock = VolumeBreakdownDto(limit = 120.0, faktNaryad = 74.5, faktEgais = 74.5, ostatokSafe = 45.5).toWoodStock()

        assertEquals(45.5, stock.remainder, 0.0001)
        assertFalse(stock.isDiscrepancy)
        assertEquals(0.0, stock.difference, 0.0001)
        assertEquals(62, stock.percent)
    }

    @Test
    fun `ЕГАИС больше наряда - расхождение и разница считаются верно`() {
        val stock = VolumeBreakdownDto(limit = 40.0, faktNaryad = 22.0, faktEgais = 25.4, ostatokSafe = 14.6).toWoodStock()

        assertTrue(stock.isDiscrepancy)
        assertEquals(3.4, stock.difference, 0.0001)
        assertEquals(14.6, stock.remainder, 0.0001)
        assertEquals(64, stock.percent)
    }

    @Test
    fun `ЕГАИС меньше наряда - это НЕ расхождение`() {
        val stock = VolumeBreakdownDto(limit = 30.0, faktNaryad = 12.8, faktEgais = 9.6, ostatokSafe = 17.2).toWoodStock()

        assertFalse(stock.isDiscrepancy)
        assertEquals(43, stock.percent)
    }

    @Test
    fun `лимит 0 - доли и процент нулевые, деление на ноль не происходит`() {
        val stock = VolumeBreakdownDto(limit = 0.0, faktNaryad = 5.0, faktEgais = 3.0, ostatokSafe = 0.0).toWoodStock()

        assertEquals(0f, stock.usedFraction)
        assertEquals(0f, stock.naryadFraction)
        assertEquals(0f, stock.egaisFraction)
        assertEquals(0, stock.percent)
    }

    @Test
    fun `used больше лимита - доли зажаты в диапазоне 0-1, остаток может быть отрицательным`() {
        val stock = VolumeBreakdownDto(limit = 10.0, faktNaryad = 15.0, faktEgais = 12.0, ostatokSafe = -5.0).toWoodStock()

        assertEquals(1f, stock.naryadFraction)
        assertEquals(1f, stock.egaisFraction)
        assertEquals(100, stock.percent)
        assertTrue(stock.remainder < 0)
        assertEquals(-5.0, stock.remainder, 0.0001)
    }

    @Test
    fun `ostatokSafe не пришёл от сервера - считаем сами как лимит минус максимум из наряда и ЕГАИС`() {
        val stock = VolumeBreakdownDto(limit = 100.0, faktNaryad = 40.0, faktEgais = 30.0, ostatokSafe = null).toWoodStock()

        assertEquals(60.0, stock.remainder, 0.0001)
    }

    @Test
    fun `все поля null - не падает, всё нулевое`() {
        val stock = VolumeBreakdownDto(limit = null, faktNaryad = null, faktEgais = null, ostatokSafe = null).toWoodStock()

        assertEquals(0.0, stock.limit, 0.0001)
        assertEquals(0.0, stock.remainder, 0.0001)
        assertEquals(0, stock.percent)
        assertFalse(stock.isDiscrepancy)
    }

    @Test
    fun `итого по породе - сумма остатков деловой древесины и дров`() {
        val species = toSpeciesStock(
            "Сосна",
            PorodaRemainingDto(
                delovaya = VolumeBreakdownDto(limit = 120.0, faktNaryad = 74.5, faktEgais = 74.5, ostatokSafe = 45.5),
                drova = VolumeBreakdownDto(limit = 40.0, faktNaryad = 22.0, faktEgais = 25.4, ostatokSafe = 14.6),
            ),
        )

        assertEquals(60.1, species.totalRemainder, 0.0001)
    }

    @Test
    fun `отсутствующая категория (дрова = null) не ломает итог`() {
        val species = toSpeciesStock(
            "Дуб",
            PorodaRemainingDto(
                delovaya = VolumeBreakdownDto(limit = 10.0, faktNaryad = 2.0, faktEgais = 2.0, ostatokSafe = 8.0),
                drova = null,
            ),
        )

        assertEquals(8.0, species.totalRemainder, 0.0001)
    }
}
