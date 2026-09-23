package com.lesovod.mobile.ui.lesokultury

import com.lesovod.mobile.data.network.dto.ProbaRowIn
import com.lesovod.mobile.data.network.dto.RezultatIn
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import java.util.UUID

/** Одна строка таблицы проб — «Номер пробы» + «Размер пробы, м²» (ввод текстом, как везде в приложении). */
data class ProbaEntry(
    val id: String = UUID.randomUUID().toString(),
    val nomer: String = "",
    val razmer: String = "",
)

/**
 * Одна строка результатов обследования — порода из справочника, «высажено» вводится вручную,
 * «прижилось» растёт тапом по чипу породы (см. LesokulturyFormComponents.PorodySection).
 */
data class RezultatEntry(
    val poroda: String,
    val vysazheno: String = "",
    val prizhilos: Int = 0,
)

/** Единица размера пробы не задана явно на сервере (хранит число как есть) — принята как м². */
private const val M2_PER_HA = 10_000.0

/** Предварительный локальный расчёт для экрана — сервер при отправке считает приживаемость сам. */
data class LesokulturyPreview(val shtNaGa: Double?, val naPloshad: Double?)

fun computePreview(proby: List<ProbaEntry>, rezultaty: List<RezultatEntry>, ploshadUchastka: Double?): LesokulturyPreview {
    val totalRazmerM2 = proby.sumOf { it.razmer.trim().replace(',', '.').toDoubleOrNull() ?: 0.0 }
    val totalPrizhilos = rezultaty.sumOf { it.prizhilos }
    if (totalRazmerM2 <= 0.0) return LesokulturyPreview(null, null)
    val shtNaGa = totalPrizhilos / (totalRazmerM2 / M2_PER_HA)
    val naPloshad = ploshadUchastka?.let { shtNaGa * it }
    return LesokulturyPreview(shtNaGa, naPloshad)
}

/** Общая проверка перед отправкой — обе таблицы непустые и заполнены корректно, участок выбран. */
fun validateLesokulturyForm(
    uchastok: LesokulturyUchastok?,
    proby: List<ProbaEntry>,
    rezultaty: List<RezultatEntry>,
): String? {
    if (uchastok == null) return "Выберите участок"
    if (proby.isEmpty()) return "Добавьте хотя бы одну пробу"
    for (p in proby) {
        if (p.nomer.isBlank()) return "Укажите номер каждой пробы"
        val razmer = p.razmer.trim().replace(',', '.').toDoubleOrNull()
        if (razmer == null || razmer <= 0) return "Укажите размер каждой пробы числом больше нуля"
    }
    if (rezultaty.isEmpty()) return "Отметьте хотя бы одну породу в результатах обследования"
    for (r in rezultaty) {
        val vysazheno = r.vysazheno.trim().toIntOrNull()
        if (vysazheno == null || vysazheno <= 0) return "Укажите «высажено» числом больше нуля для породы «${r.poroda}»"
    }
    return null
}

fun List<ProbaEntry>.toProbaRowsIn(): List<ProbaRowIn> = map {
    ProbaRowIn(nomer = it.nomer.trim(), razmer = it.razmer.trim().replace(',', '.').toDouble())
}

fun List<RezultatEntry>.toRezultatyIn(): List<RezultatIn> = map {
    RezultatIn(poroda = it.poroda, vysazheno = it.vysazheno.trim().toInt(), prizhilos = it.prizhilos)
}
