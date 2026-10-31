package com.lesovod.kubaturnik.ui

import com.lesovod.kubaturnik.ui.VoiceCommand.Count
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {

    private fun parse(phrase: String) = VoiceCommandParser.parse(phrase).commands

    @Test
    fun diameterAloneAddsOne() {
        assertEquals(listOf(Count(24, 1)), parse("24"))
        assertEquals(listOf(Count(24, 1)), parse("двадцать четыре"))
        assertEquals(listOf(Count(120, 1)), parse("сто двадцать"))
        assertEquals(listOf(Count(40, 1)), parse("сорок"))
    }

    @Test
    fun removeAndAddWithPieces() {
        assertEquals(listOf(Count(24, -1)), parse("удали 24"))
        assertEquals(listOf(Count(24, -2)), parse("удали двадцать четыре две штуки"))
        assertEquals(listOf(Count(24, 2)), parse("добавь 24 2 штуки"))
        assertEquals(listOf(Count(34, 5)), parse("тридцать четыре пять штук"))
    }

    @Test
    fun severalDiametersInOnePhrase() {
        assertEquals(listOf(Count(24, 1), Count(26, 1), Count(28, 1)), parse("24 26 28"))
        assertEquals(listOf(Count(24, 1), Count(26, -1)), parse("24 удали 26"))
    }

    @Test
    fun piecesWithoutDiameterIsDiameter() {
        assertEquals(listOf(Count(22, 1)), parse("двадцать две штуки"))
        assertEquals(listOf(Count(24, 1)), parse("24шт"))
    }

    @Test
    fun destinationSortAndService() {
        assertEquals(
            listOf(VoiceCommand.SetDestination(KubaturnikDestination.TRAILER), Count(120, 1)),
            parse("прицеп сто двадцать"),
        )
        assertEquals(listOf(VoiceCommand.SetSort(2, null)), parse("сорт два"))
        assertEquals(listOf(VoiceCommand.SetSort(2, null)), parse("сорт второй"))
        assertEquals(listOf(VoiceCommand.SetSort(null, "пиловочник"), Count(30, 1)), parse("сорт пиловочник 30"))
        // Буквы фиксированных сортов А/Б/С/Д
        assertEquals(listOf(VoiceCommand.SetSort(1, null)), parse("сорт а"))
        assertEquals(listOf(VoiceCommand.SetSort(2, null), Count(24, 1)), parse("сорт бэ 24"))
        assertEquals(listOf(VoiceCommand.SetSort(3, null)), parse("сорт эс"))
        assertEquals(listOf(VoiceCommand.SetSort(3, null)), parse("сорт цэ"))
        assertEquals(listOf(VoiceCommand.SetSort(4, null)), parse("сорт дэ"))
        assertEquals(listOf(VoiceCommand.SetSort(4, null)), parse("сорт четыре"))
        assertEquals(listOf(VoiceCommand.Undo), parse("отмена"))
        assertEquals(listOf(VoiceCommand.Total), parse("итого"))
        assertEquals(listOf(VoiceCommand.Stop), parse("стоп"))
    }

    @Test
    fun unknownWordsAreReported() {
        val result = VoiceCommandParser.parse("ёлки 24")
        assertEquals(listOf(Count(24, 1)), result.commands)
        assertEquals(listOf("елки"), result.unknownWords)
    }

    @Test
    fun piecesPhraseForms() {
        assertEquals("одна штука", piecesPhrase(1))
        assertEquals("две штуки", piecesPhrase(2))
        assertEquals("5 штук", piecesPhrase(5))
        assertEquals("12 штук", piecesPhrase(12))
        assertEquals("21 штука", piecesPhrase(21))
        assertEquals("24 штуки", piecesPhrase(24))
    }
}
