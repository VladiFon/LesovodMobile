package com.lesovod.mobile.ui.stock

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU_LOCALE = Locale("ru")
private val DISPLAY_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", RU_LOCALE)

/** «120,0 м³» — русская локаль, одна цифра после запятой. */
fun formatVolume(value: Double): String = String.format(RU_LOCALE, "%.1f", value)

/**
 * Сервер отдаёт дату строкой, точный формат не подтверждён по документации — пробуем разобрать
 * как ISO-8601 (обычный вид у FastAPI/Pydantic), а если не вышло, показываем строку как есть,
 * а не роняем экран: то же решение, что уже применялось при похожих несовпадениях схемы.
 */
fun formatServerDateTime(raw: String): String {
    runCatching { return OffsetDateTime.parse(raw).toLocalDateTime().format(DISPLAY_DATE_TIME) }
    runCatching { return LocalDateTime.parse(raw).format(DISPLAY_DATE_TIME) }
    return raw
}
