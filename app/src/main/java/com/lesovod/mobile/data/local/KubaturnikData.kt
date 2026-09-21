package com.lesovod.mobile.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Один из 4 блоков длин таблицы ГОСТ 2708-75 (шаг длины внутри блока — не всегда 0,1 м, см. lengths). */
@Serializable
data class KubaturnikBlock(
    val id: String,
    val minLength: Double,
    val maxLength: Double,
    val lengths: List<Double>,
    /** Ключ — диаметр в см строкой (3..13 — все значения, 14..120 — только чётные); значения — м³ по [lengths] в том же порядке. */
    val diameters: Map<String, List<Double>>,
)

@Serializable
data class KubaturnikTable(val blocks: List<KubaturnikBlock>)

/** Выбранная на партию длина: конкретный блок + конкретное табличное значение длины внутри него. */
data class KubaturnikLength(val blockId: String, val lengthIndex: Int, val lengthValue: Double)

/** Загружает статичную таблицу ГОСТ 2708-75 из assets (без сети, один раз за процесс). */
object KubaturnikTableLoader {
    private const val ASSET_NAME = "kubaturnik_gost2708_75.json"
    private var cached: KubaturnikTable? = null

    fun load(context: Context): KubaturnikTable {
        cached?.let { return it }
        val json = Json { ignoreUnknownKeys = true }
        val text = context.applicationContext.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return json.decodeFromString<KubaturnikTable>(text).also { cached = it }
    }
}

/**
 * Диаметр рассчитывается ТОЛЬКО по значению из таблицы: 3-13 см — берём ровно то, что нажали
 * (в таблице есть каждое целое значение), 14 см и больше — в таблице только чётные значения,
 * поэтому нечётное округляется до ближайшего чётного (как того требует сам ГОСТ). Если нечётное
 * ровно посередине между двумя чётными — округляем вверх (например, 15 -> 16).
 */
fun KubaturnikBlock.resolveDiameterKey(diameter: Int): String {
    if (diameters.containsKey(diameter.toString())) return diameter.toString()
    val rounded = if (diameter % 2 == 0) diameter else diameter + 1
    return rounded.toString()
}

/** Объём одного бревна данного диаметра при выбранной длине, м³. */
fun KubaturnikBlock.volumeFor(diameter: Int, lengthIndex: Int): Double? =
    diameters[resolveDiameterKey(diameter)]?.getOrNull(lengthIndex)

/**
 * Диаметры-кнопки для сетки подсчёта, по возрастанию.
 * Шаг 1 см — каждое целое от минимального до максимального диаметра в таблице (включая те,
 * для которых в таблице нет отдельной строки — они лишь для прицеливания глазом, см. [resolveDiameterKey]).
 * Шаг 2 см — только чётные диаметры (стандартная ступень толщины ГОСТ), кроме самого тонкого края 3 см.
 */
fun KubaturnikBlock.diameterButtons(stepCm: Int): List<Int> {
    val minDiam = diameters.keys.mapNotNull { it.toIntOrNull() }.minOrNull() ?: return emptyList()
    val maxDiam = diameters.keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: return emptyList()
    return if (stepCm <= 1) {
        (minDiam..maxDiam).toList()
    } else {
        val evens = (minDiam..maxDiam).filter { it % 2 == 0 }
        if (minDiam % 2 != 0) listOf(minDiam) + evens else evens
    }
}
