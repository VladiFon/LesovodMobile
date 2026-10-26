package com.lesovod.mobile.ui.lesokultury

import com.lesovod.mobile.data.network.dto.KartaProbaDto
import com.lesovod.mobile.data.network.dto.ProbaRowIn
import com.lesovod.mobile.data.network.dto.RezultatIn
import com.lesovod.mobile.data.network.dto.UchastokPolyaRequest
import com.lesovod.mobile.ui.proba.LesokulturyUchastok
import com.lesovod.mobile.ui.proba.UchastokPolya
import java.util.Locale
import java.util.UUID

/** Одна строка таблицы проб — «Номер пробы» + «Размер пробы, м²» (ввод текстом, как везде в приложении). */
data class ProbaEntry(
    val id: String = UUID.randomUUID().toString(),
    val nomer: String = "",
    val razmer: String = "",
    /** Проба с карты (GET /api/proby): учёт на той же пробе, что и в прошлый раз. */
    val kartaProbaId: Int? = null,
)

/** Пробы с карты → строки карточки: «П1, 100 м²». Пустую строку по умолчанию заменяют. */
fun ProbaEntry.isBlank() = nomer.isBlank() && razmer.isBlank() && kartaProbaId == null

fun KartaProbaDto.toProbaEntry() = ProbaEntry(
    nomer = nomer.orEmpty(),
    razmer = ploshadM2?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.1f".format(Locale.US, it) }.orEmpty(),
    kartaProbaId = id,
)

/** Добавить пробу с карты (повторно — не добавляет); пустые строки уступают ей место. */
fun List<ProbaEntry>.withKartaProba(proba: KartaProbaDto): List<ProbaEntry> {
    if (any { it.kartaProbaId == proba.id }) return this
    return filterNot { it.isBlank() } + proba.toProbaEntry()
}

/**
 * Одна строка результатов обследования — порода из справочника. «Прижилось» — тапами +/− или
 * числом; «высажено» на пробах считается само (см. withAutoVysazheno), пока его не поправили
 * руками (vysazhenoVruchnuyu).
 */
data class RezultatEntry(
    val poroda: String,
    val vysazheno: String = "",
    val prizhilos: Int = 0,
    val vysazhenoVruchnuyu: Boolean = false,
)

/** Коды пород в формуле состава → название, как в справочнике пород. */
private val PORODA_PO_KODU = mapOf(
    "С" to "Сосна", "Е" to "Ель", "Б" to "Берёза", "Д" to "Дуб", "Ос" to "Осина",
    "Ол" to "Ольха черная", "Олч" to "Ольха черная", "Олс" to "Ольха серая", "Лц" to "Лиственница",
    "Я" to "Ясень", "Кл" to "Клён", "Лп" to "Липа", "Г" to "Граб", "Ив" to "Ива", "Ил" to "Ильм",
    "Дк" to "Дуб красный", "П" to "Пихта", "Кд" to "Кедр",
)

private fun normPoroda(s: String) = s.trim().lowercase().replace('ё', 'е')

/** «8Е2С», «5рЕ4рС1рБ» («р» — рядами), «7С3Б+Д». */
private val SOSTAV_RE = Regex("""(\+|\d{1,2})\s*р?\s*([А-ЯЁ][а-яё]*)""")

/**
 * Доли пород из формулы состава (в десятых): «8Е2С» → Е=8, С=2. Примесь «+Б» — доля 0:
 * высаженных по ней не считаем.
 */
fun parseSostavDoli(formula: String?): List<Pair<String, Int>> =
    SOSTAV_RE.findAll(formula.orEmpty()).map { m ->
        m.groupValues[2] to (m.groupValues[1].toIntOrNull() ?: 0)
    }.toList()

/** Название породы по коду из состава — как оно записано в справочнике, иначе как в PORODA_PO_KODU. */
fun porodaPoKodu(kod: String, porody: List<String>): String {
    val name = PORODA_PO_KODU[kod]
    if (name != null) return porody.firstOrNull { normPoroda(it) == normPoroda(name) } ?: name
    return porody.firstOrNull { normPoroda(it).startsWith(normPoroda(kod)) } ?: kod
}

/** Доля породы (в десятых) в составе участка; null — породы в составе нет. */
private fun dolyaPorody(poroda: String, sostav: String?): Int? =
    parseSostavDoli(sostav).firstOrNull { (kod, _) ->
        val name = PORODA_PO_KODU[kod] ?: kod
        normPoroda(name) == normPoroda(poroda) || normPoroda(kod) == normPoroda(poroda)
    }?.second

/**
 * Высажено штук на 1 га: густота посадки участка, а если её нет — по схеме посадки
 * (10 000 / (междурядье × шаг)). Густоту иногда пишут в тысячах («3,3») — такие ×1000.
 */
fun vysazhenoNaGa(polya: UchastokPolya): Double? {
    polya.gustotaPosadki?.takeIf { it > 0 }?.let { return if (it < 100) it * 1000 else it }
    val a = polya.shemaMezhduRyadami ?: return null
    val b = polya.shemaVRyadu ?: return null
    return if (a > 0 && b > 0) M2_PER_HA / (a * b) else null
}

/** Как посчитано «высажено» на пробах — для подсказки под полем. */
data class VysazhenoRaschet(val naGa: Double, val dolya: Int, val razmerM2: Double, val shtuk: Int)

/**
 * Высажено породы на пробах: всего на участке (шт/га × площадь) × доля породы × площадь проб /
 * площадь участка = шт/га × доля / 10 × площадь проб, м² / 10 000. null — не из чего считать.
 */
fun vysazhenoRaschet(poroda: String, uchastok: LesokulturyUchastok?, proby: List<ProbaEntry>): VysazhenoRaschet? {
    uchastok ?: return null
    val naGa = vysazhenoNaGa(uchastok.polya) ?: return null
    val dolya = dolyaPorody(poroda, uchastok.polya.sostavFormula)?.takeIf { it > 0 } ?: return null
    val razmer = totalRazmerM2(proby).takeIf { it > 0 } ?: return null
    val shtuk = Math.round(naGa * dolya / 10.0 * razmer / M2_PER_HA).toInt()
    return VysazhenoRaschet(naGa, dolya, razmer, shtuk)
}

/** Строки с «высажено», посчитанным само; правленные руками остаются как есть. */
fun List<RezultatEntry>.withAutoVysazheno(uchastok: LesokulturyUchastok?, proby: List<ProbaEntry>): List<RezultatEntry> =
    map { r ->
        if (r.vysazhenoVruchnuyu) r
        else r.copy(vysazheno = vysazhenoRaschet(r.poroda, uchastok, proby)?.shtuk?.toString().orEmpty())
    }

/** Породы из состава участка (кроме примесей «+») — сразу строками в карточку. */
fun rezultatyIzSostava(uchastok: LesokulturyUchastok, porody: List<String>): List<RezultatEntry> =
    parseSostavDoli(uchastok.polya.sostavFormula)
        .filter { it.second > 0 }
        .map { RezultatEntry(poroda = porodaPoKodu(it.first, porody)) }
        .distinctBy { normPoroda(it.poroda) }

/** Поля участка после «Сохранить данные участка» — чтобы расчёт «высажено» сразу взял новую густоту/схему. */
fun LesokulturyUchastok.withPolya(r: UchastokPolyaRequest): LesokulturyUchastok = copy(
    polya = polya.copy(
        podvydel = r.podvydel ?: polya.podvydel,
        metodSozdaniya = r.metodSozdaniya ?: polya.metodSozdaniya,
        sposobObrabotki = r.sposobObrabotki ?: polya.sposobObrabotki,
        shemaMezhduRyadami = r.shemaMezhduRyadami ?: polya.shemaMezhduRyadami,
        shemaVRyadu = r.shemaVRyadu ?: polya.shemaVRyadu,
        gustotaPosadki = r.gustotaPosadki ?: polya.gustotaPosadki,
        posadochnyyMaterial = r.posadochnyyMaterial ?: polya.posadochnyyMaterial,
    ),
)

/** Единица размера пробы не задана явно на сервере (хранит число как есть) — принята как м². */
private const val M2_PER_HA = 10_000.0

/** Предварительный локальный расчёт для экрана — сервер при отправке считает приживаемость сам. */
data class LesokulturyPreview(val shtNaGa: Double?, val naPloshad: Double?)

/** Сумма размеров всех проб, м² — общий знаменатель для пересчёта на 1 га. */
fun totalRazmerM2(proby: List<ProbaEntry>): Double =
    proby.sumOf { it.razmer.trim().replace(',', '.').toDoubleOrNull() ?: 0.0 }

/** Множитель для перевода «штук на пробах» в «штук на 1 га» — null, пока размер проб не заполнен. */
fun perHectareMultiplier(proby: List<ProbaEntry>): Double? {
    val total = totalRazmerM2(proby)
    return if (total > 0.0) M2_PER_HA / total else null
}

fun computePreview(proby: List<ProbaEntry>, rezultaty: List<RezultatEntry>, ploshadUchastka: Double?): LesokulturyPreview {
    val totalPrizhilos = rezultaty.sumOf { it.prizhilos }
    val multiplier = perHectareMultiplier(proby) ?: return LesokulturyPreview(null, null)
    val shtNaGa = totalPrizhilos * multiplier
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
        if (r.prizhilos > vysazheno) return "«${r.poroda}»: прижилось (${r.prizhilos}) больше, чем высажено ($vysazheno)"
    }
    return null
}

fun List<ProbaEntry>.toProbaRowsIn(): List<ProbaRowIn> = map {
    ProbaRowIn(nomer = it.nomer.trim(), razmer = it.razmer.trim().replace(',', '.').toDouble(), kartaProbaId = it.kartaProbaId)
}

fun List<RezultatEntry>.toRezultatyIn(): List<RezultatIn> = map {
    RezultatIn(poroda = it.poroda, vysazheno = it.vysazheno.trim().toInt(), prizhilos = it.prizhilos)
}
