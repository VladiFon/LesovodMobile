package com.lesovod.mobile.ui.stock

import com.lesovod.mobile.data.network.dto.PorodaRemainingDto
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import kotlin.math.roundToInt

/**
 * Посчитанные для экрана значения одной категории (деловая древесина / дрова) одной породы.
 *
 * Остаток берётся из [VolumeBreakdownDto.ostatokSafe] сервера, а не пересчитывается по формуле
 * "лимит минус максимум из наряда/ЕГАИС" — у сервера уже есть готовая безопасная логика остатка,
 * переписывать её на клиенте не нужно. Формула "used = max(наряд, ЕГАИС)" используется только
 * для доли/процента кольца и баров — если сервер не прислал ostatok_safe (нет данных), она же
 * служит запасным вариантом расчёта остатка, чтобы экран не остался пустым.
 */
data class WoodStock(
    val limit: Double,
    val naryad: Double,
    val egais: Double,
    val remainder: Double,
    /** Остаток при допуске лимита −10% / +10% (договорной допуск по объёму заготовки). */
    val remainderMinus10: Double,
    val remainderPlus10: Double,
    /** Доля лимита, "занятая" наибольшим из наряда/ЕГАИС — на неё рисуется кольцо. */
    val usedFraction: Float,
    val percent: Int,
    val naryadFraction: Float,
    val egaisFraction: Float,
    val isDiscrepancy: Boolean,
    val difference: Double,
)

fun VolumeBreakdownDto.toWoodStock(): WoodStock {
    val limit = (limit ?: 0.0).coerceAtLeast(0.0)
    val naryad = (faktNaryad ?: 0.0).coerceAtLeast(0.0)
    val egais = (faktEgais ?: 0.0).coerceAtLeast(0.0)
    val used = maxOf(naryad, egais)
    val remainder = ostatokSafe ?: (limit - used)
    val remainderMinus10 = limit * 0.9 - used
    val remainderPlus10 = limit * 1.1 - used

    val usedFraction = fractionOf(used, limit)
    val percent = if (limit <= 0.0) 0 else (used / limit * 100).roundToInt().coerceIn(0, 100)
    val naryadFraction = fractionOf(naryad, limit)
    val egaisFraction = fractionOf(egais, limit)

    return WoodStock(
        limit = limit,
        naryad = naryad,
        egais = egais,
        remainder = remainder,
        remainderMinus10 = remainderMinus10,
        remainderPlus10 = remainderPlus10,
        usedFraction = usedFraction,
        percent = percent,
        naryadFraction = naryadFraction,
        egaisFraction = egaisFraction,
        isDiscrepancy = egais > naryad,
        difference = egais - naryad,
    )
}

private fun fractionOf(value: Double, limit: Double): Float =
    if (limit <= 0.0) 0f else (value / limit).toFloat().coerceIn(0f, 1f)

/** Посчитанные значения по одной породе — обе категории уже в виде [WoodStock] (могут отсутствовать). */
data class SpeciesStock(
    val poroda: String,
    val delovaya: WoodStock?,
    val drova: WoodStock?,
) {
    val totalRemainder: Double get() = (delovaya?.remainder ?: 0.0) + (drova?.remainder ?: 0.0)
    val totalRemainderMinus10: Double get() = (delovaya?.remainderMinus10 ?: 0.0) + (drova?.remainderMinus10 ?: 0.0)
    val totalRemainderPlus10: Double get() = (delovaya?.remainderPlus10 ?: 0.0) + (drova?.remainderPlus10 ?: 0.0)
}

fun toSpeciesStock(poroda: String, dto: PorodaRemainingDto): SpeciesStock =
    SpeciesStock(
        poroda = poroda,
        delovaya = dto.delovaya?.toWoodStock(),
        drova = dto.drova?.toWoodStock(),
    )
