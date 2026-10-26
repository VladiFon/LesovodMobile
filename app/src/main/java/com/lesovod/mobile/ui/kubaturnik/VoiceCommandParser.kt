package com.lesovod.mobile.ui.kubaturnik

/**
 * Голосовые команды кубатурника. Одна фраза может содержать несколько команд подряд:
 * «прицеп 24 26 две штуки» = переключиться на прицеп, +1 бревно Ø24, +2 бревна Ø26.
 */
sealed interface VoiceCommand {
    /** [count] > 0 — добавить, < 0 — удалить столько брёвен диаметра [diameter]. */
    data class Count(val diameter: Int, val count: Int) : VoiceCommand
    data class SetDestination(val destination: KubaturnikDestination) : VoiceCommand
    /** Сорт по номеру в списке (1 — первый) или по названию — разрешается во ViewModel по списку сортов. */
    data class SetSort(val number: Int?, val name: String?) : VoiceCommand
    data object Undo : VoiceCommand
    data object Total : VoiceCommand
    data object Stop : VoiceCommand
}

/**
 * Разбор русской фразы (как её вернул распознаватель) в команды. Числа понимаются и цифрами («24»),
 * и словами («двадцать четыре», «сто двадцать»). Правила:
 *  - число — диаметр, +1 бревно;
 *  - число и сразу «штук/штуки/штука/шт/раз» — количество для предыдущего диаметра («24 две штуки»);
 *  - «удали/убери/минус» — дальше в этой фразе диаметры убираются, «добавь/плюс» — снова добавляются;
 *  - «машина», «прицеп», «сорт <номер или название>», «отмена», «итого», «стоп».
 * Нераспознанные слова пропускаются (их вернёт [unknownWords], чтобы показать на экране).
 */
object VoiceCommandParser {

    data class Result(val commands: List<VoiceCommand>, val unknownWords: List<String>)

    private val units = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одно" to 1, "два" to 2, "две" to 2, "три" to 3,
        "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
    )
    private val teens = mapOf(
        "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14,
        "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
    )
    private val tens = mapOf(
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
        "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90,
    )
    private val hundreds = mapOf("сто" to 100, "двести" to 200)
    private val ordinals = mapOf(
        "первый" to 1, "первая" to 1, "первое" to 1, "второй" to 2, "вторая" to 2, "второе" to 2,
        "третий" to 3, "третья" to 3, "третье" to 3, "четвертый" to 4, "четвертая" to 4, "четвертое" to 4,
        "пятый" to 5, "пятая" to 5, "пятое" to 5,
    )

    private val pieceWords = setOf("штук", "штуки", "штука", "штуку", "шт", "раз", "раза")
    private val removeWords = setOf("удали", "удалить", "удаляй", "убери", "убрать", "минус", "сними", "снять")
    private val addWords = setOf("добавь", "добавить", "добавляй", "плюс", "еще")
    private val undoWords = setOf("отмена", "отмени", "отменить", "назад")
    private val totalWords = setOf("итого", "итог", "сколько", "всего")
    private val stopWords = setOf("стоп", "хватит", "выключи")
    private val machineWords = setOf("машина", "машину", "машины", "машине")
    private val trailerWords = setOf("прицеп", "прицепа", "прицепе")
    private val sortWords = setOf("сорт", "сорта", "сортамент", "сортимент")
    /** Связки, которые люди вставляют между словами, — не считаем их «непонятыми». */
    private val fillerWords = setOf("и", "на", "в", "по", "ну", "так", "диаметр", "диаметром", "бревно", "бревна", "запиши")

    /** Слова, которые можно передать распознавателю как словарь (для Vosk — ограниченная грамматика). */
    val vocabulary: Set<String>
        get() = units.keys + teens.keys + tens.keys + hundreds.keys + pieceWords + removeWords + addWords +
            undoWords + totalWords + stopWords + machineWords + trailerWords + sortWords + fillerWords + ordinals.keys

    private sealed interface Token {
        data class Num(val value: Int) : Token
        data class Word(val text: String) : Token
    }

    fun parse(phrase: String): Result {
        val tokens = tokenize(phrase)
        val commands = mutableListOf<VoiceCommand>()
        val unknown = mutableListOf<String>()
        var sign = 1
        var i = 0
        while (i < tokens.size) {
            when (val t = tokens[i]) {
                is Token.Num -> {
                    val next = tokens.getOrNull(i + 1)
                    val isCount = next is Token.Word && next.text in pieceWords
                    if (isCount) {
                        val last = commands.lastOrNull()
                        if (last is VoiceCommand.Count && t.value > 0) {
                            commands[commands.lastIndex] = last.copy(count = sign * t.value)
                        } else {
                            // «24 штуки» без диаметра перед ним — скорее всего сам диаметр, а «штуки» лишнее.
                            commands += VoiceCommand.Count(t.value, sign)
                        }
                        i += 2
                        continue
                    }
                    commands += VoiceCommand.Count(t.value, sign)
                }
                is Token.Word -> when (t.text) {
                    in removeWords -> sign = -1
                    in addWords -> sign = 1
                    in undoWords -> commands += VoiceCommand.Undo
                    in totalWords -> commands += VoiceCommand.Total
                    in stopWords -> commands += VoiceCommand.Stop
                    in machineWords -> commands += VoiceCommand.SetDestination(KubaturnikDestination.MACHINE)
                    in trailerWords -> commands += VoiceCommand.SetDestination(KubaturnikDestination.TRAILER)
                    in sortWords -> {
                        // «сорт два» / «сорт второй» — по номеру; «сорт пиловочник» — по названию (одно слово после «сорт»).
                        when (val arg = tokens.getOrNull(i + 1)) {
                            is Token.Num -> commands += VoiceCommand.SetSort(arg.value, null)
                            is Token.Word -> commands += ordinals[arg.text]
                                ?.let { VoiceCommand.SetSort(it, null) }
                                ?: VoiceCommand.SetSort(null, arg.text)
                            null -> unknown += t.text
                        }
                        i += 2
                        continue
                    }
                    in pieceWords, in fillerWords -> Unit
                    else -> unknown += t.text
                }
            }
            i++
        }
        return Result(commands, unknown)
    }

    /** Нижний регистр, «ё»→«е», цифры отдельно от букв («24шт» → «24 шт»), числа-слова склеены в одно число. */
    private fun tokenize(phrase: String): List<Token> {
        val words = phrase.lowercase()
            .replace('ё', 'е')
            .replace(Regex("(\\d)(\\p{L})"), "$1 $2")
            .replace(Regex("(\\p{L})(\\d)"), "$1 $2")
            .split(Regex("[^\\p{L}\\d]+"))
            .filter { it.isNotEmpty() }

        val out = mutableListOf<Token>()
        var i = 0
        while (i < words.size) {
            val digits = words[i].toIntOrNull()
            if (digits != null) {
                out += Token.Num(digits)
                i++
                continue
            }
            val (value, used) = readNumberWords(words, i)
            if (used > 0) {
                out += Token.Num(value)
                i += used
            } else {
                out += Token.Word(words[i])
                i++
            }
        }
        return out
    }

    /**
     * Читает число словами начиная с [start]: [сотни] [десятки | 10–19] [единицы].
     * «двадцать четыре два» → 24 (и отдельно потом 2): после единиц число закончено.
     */
    private fun readNumberWords(words: List<String>, start: Int): Pair<Int, Int> {
        var i = start
        var value = 0
        hundreds[words.getOrNull(i)]?.let { value += it; i++ }
        val teen = teens[words.getOrNull(i)]
        if (teen != null) {
            return (value + teen) to (i + 1 - start)
        }
        tens[words.getOrNull(i)]?.let { value += it; i++ }
        units[words.getOrNull(i)]?.let { value += it; i++ }
        return value to (i - start)
    }
}

/** «одна штука», «две штуки», «пять штук» — для голосового ответа и подписей. */
fun piecesPhrase(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "штук"
        n % 10 == 1 -> "штука"
        n % 10 in 2..4 -> "штуки"
        else -> "штук"
    }
    val number = when (n) {
        1 -> "одна"
        2 -> "две"
        else -> n.toString()
    }
    return "$number $word"
}
