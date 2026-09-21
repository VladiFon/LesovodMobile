package com.lesovod.mobile.data.session

enum class WorkerRole(val displayName: String) {
    LESORUB("Лесоруб"),
    TRAKTORIST("Тракторист"),
    HARVESTERSHIK("Харвестерщик"),
    VALSHIK("Вальщик"),
    MASTER("Мастер"),
    POMOSHNIK_LESNICHEGO("Помощник лесничего"),
    LESNICHIY("Лесничий"),
    UNKNOWN("Сотрудник");

    companion object {
        /**
         * Начиная с введения фиксированного списка должностей на бэкенде
         * (Literal в CreateWorkerIn, backend/app/routers/auth.py) dolzhnost
         * новых сотрудников — ровно одна из этих строк, сравниваем точно.
         * Нечёткий разбор ниже остаётся только как запасной вариант для
         * записей, заведённых до этого изменения (свободный текст вроде
         * "Тракторист на вывозке леса").
         */
        fun fromDolzhnost(dolzhnost: String?): WorkerRole {
            val trimmed = dolzhnost?.trim().orEmpty()
            entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }?.let { return it }

            val value = trimmed.lowercase()
            return when {
                value.isEmpty() -> UNKNOWN
                "харвестер" in value -> HARVESTERSHIK
                "трактор" in value -> TRAKTORIST
                "вальщик" in value -> VALSHIK
                "лесоруб" in value -> LESORUB
                "помощник" in value && "леснич" in value -> POMOSHNIK_LESNICHEGO
                "леснич" in value -> LESNICHIY
                "мастер" in value -> MASTER
                else -> UNKNOWN
            }
        }
    }
}
