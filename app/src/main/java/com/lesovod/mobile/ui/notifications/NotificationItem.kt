package com.lesovod.mobile.ui.notifications

import com.lesovod.mobile.ui.map.stringValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Тип события — используется только для выбора иконки, неизвестный тип просто рисуется общей. */
enum class NotificationType { BREAKDOWN, NOTE, PROBA, TRELEVKA, OTHER }

data class NotificationItem(
    val id: Int,
    val type: NotificationType,
    val text: String,
    val isRead: Boolean,
    val createdAt: String?,
)

private fun JsonObject.intValue(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.boolValue(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/**
 * Точный набор полей не был дан в задаче (только пути GET /api/notifications и
 * PATCH /api/notifications/{id}) — читаем несколько разумных вариантов имени; уведомление
 * без распознаваемого id просто не попадёт в список, а не уронит разбор остальных.
 */
fun JsonObject.toNotificationItem(): NotificationItem? {
    val id = intValue("id") ?: return null
    val typeRaw = stringValue("type") ?: stringValue("event_type") ?: stringValue("tip")
    val type = when (typeRaw?.lowercase()) {
        "breakdown", "polomka", "поломка" -> NotificationType.BREAKDOWN
        "note", "zametka", "заметка" -> NotificationType.NOTE
        "proba", "проба" -> NotificationType.PROBA
        "trelevka", "трелёвка", "трелевка" -> NotificationType.TRELEVKA
        else -> NotificationType.OTHER
    }
    val text = stringValue("text") ?: stringValue("title") ?: stringValue("message") ?: ""
    val isRead = boolValue("is_read") ?: boolValue("read") ?: boolValue("prochitano") ?: false
    val createdAt = stringValue("created_at")
    return NotificationItem(id, type, text, isRead, createdAt)
}
