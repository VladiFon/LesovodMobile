package com.lesovod.mobile.data.session

/**
 * Единая таблица прав по должностям (см. задачу «Разные должности должны видеть разное меню»).
 * Отчёт, задачи, отметка времени, отправка заметок и карта — доступны всем ролям без проверки.
 */
val WorkerRole.isMasterGroup: Boolean
    get() = this == WorkerRole.MASTER || this == WorkerRole.POMOSHNIK_LESNICHEGO || this == WorkerRole.LESNICHIY

/** Поломка техники — тракторист, харвестерщик. */
val WorkerRole.canReportBreakdown: Boolean
    get() = this == WorkerRole.TRAKTORIST || this == WorkerRole.HARVESTERSHIK

/** Остатки по делянкам — только мастер / пом. лесничего / лесничий. */
val WorkerRole.canSeeStock: Boolean
    get() = isMasterGroup

/** Кубатурник — только мастер / пом. лесничего / лесничий. */
val WorkerRole.canUseKubaturnik: Boolean
    get() = isMasterGroup

/** Пробы рубок ухода — ввод: лесоруб, вальщик. */
val WorkerRole.canInputProba: Boolean
    get() = this == WorkerRole.LESORUB || this == WorkerRole.VALSHIK

/** Пробы рубок ухода — просмотр: те же, кто вводит, плюс мастер/пом./лесничий (только просмотр). */
val WorkerRole.canViewProba: Boolean
    get() = canInputProba || isMasterGroup

/** Трелёвка — только тракторист. */
val WorkerRole.canTrelevka: Boolean
    get() = this == WorkerRole.TRAKTORIST

/** Входящие заметки — только мастер / пом. лесничего / лесничий; отправка доступна всем ролям. */
val WorkerRole.canViewNotesInbox: Boolean
    get() = isMasterGroup

/** Инвентаризация / перевод лесных культур — только мастер / пом. лесничего / лесничий. */
val WorkerRole.canManageLesokultury: Boolean
    get() = isMasterGroup

/** Табель — ручной ввод — только мастер / пом. лесничего / лесничий (см. backend require_office_or_master). */
val WorkerRole.canEditTabel: Boolean
    get() = isMasterGroup
