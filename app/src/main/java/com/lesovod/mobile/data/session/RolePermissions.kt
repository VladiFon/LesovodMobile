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

/** У лесопункта (издание сервера, data/izdanie/Izdanie.kt) свой набор экранов. */
private val lesopunkt: Boolean get() = com.lesovod.mobile.data.izdanie.Izdanie.lesopunkt

/** Остатки по делянкам — мастер / пом. лесничего / лесничий; у лесопункта — все. */
val WorkerRole.canSeeStock: Boolean
    get() = isMasterGroup || lesopunkt

/** Кубатурник — мастер / пом. лесничего / лесничий; у лесопункта — все (Влад, 04.11.2026). */
val WorkerRole.canUseKubaturnik: Boolean
    get() = isMasterGroup || lesopunkt

/** Пробы рубок ухода — ввод: лесоруб, вальщик. */
val WorkerRole.canInputProba: Boolean
    get() = !lesopunkt && (this == WorkerRole.LESORUB || this == WorkerRole.VALSHIK || this == WorkerRole.LESOVOD)

/** Пробы рубок ухода — просмотр: те же, кто вводит, плюс мастер/пом./лесничий (только просмотр). */
val WorkerRole.canViewProba: Boolean
    get() = !lesopunkt && (canInputProba || isMasterGroup)

/** Трелёвка — только тракторист. */
val WorkerRole.canTrelevka: Boolean
    get() = this == WorkerRole.TRAKTORIST

/** Входящие заметки — только мастер / пом. лесничего / лесничий; отправка доступна всем ролям. */
val WorkerRole.canViewNotesInbox: Boolean
    get() = isMasterGroup

/** Инвентаризация / перевод лесных культур — только мастер / пом. лесничего / лесничий. */
val WorkerRole.canManageLesokultury: Boolean
    get() = isMasterGroup && !lesopunkt

/** Табель — ручной ввод — только мастер / пом. лесничего / лесничий (см. require_office_or_master в app/routers/tabel.py). */
val WorkerRole.canManageTabel: Boolean
    get() = isMasterGroup

/**
 * «Отчёт за смену» по профессиям (Влад, 04.11.2026): у тракториста, харвестерщика и
 * вальщика/лесоруба/лесовода он заменяет старый «Отчёт о работе», у каждого свой набор работ.
 */
enum class SmenaProfessiya { TRAKTORIST, HARVESTER, BRIGADA }

val WorkerRole.smenaProfessiya: SmenaProfessiya?
    get() = when (this) {
        WorkerRole.TRAKTORIST -> SmenaProfessiya.TRAKTORIST
        WorkerRole.HARVESTERSHIK -> SmenaProfessiya.HARVESTER
        WorkerRole.LESORUB, WorkerRole.VALSHIK, WorkerRole.LESOVOD -> SmenaProfessiya.BRIGADA
        else -> null
    }

/** Есть экран «Отчёт за смену» (вместо старого отчёта). */
val WorkerRole.canSmena: Boolean
    get() = smenaProfessiya != null
