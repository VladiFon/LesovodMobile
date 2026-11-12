package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.data.local.IgraPrefs
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.data.session.canManageLesokultury
import com.lesovod.mobile.data.session.canManageTabel
import com.lesovod.mobile.data.session.canReportBreakdown
import com.lesovod.mobile.data.session.canSeeStock
import com.lesovod.mobile.data.session.canSmena
import com.lesovod.mobile.data.session.canViewProba
import com.lesovod.mobile.ui.components.LvEmptyState
import com.lesovod.mobile.ui.components.LvSearchField
import com.lesovod.mobile.ui.components.LvSectionLabel
import com.lesovod.mobile.ui.components.LvTile
import com.lesovod.mobile.ui.components.LvTopBar
import com.lesovod.mobile.ui.components.UpdateBanner
import com.lesovod.mobile.ui.theme.LocalLv

/** Один раздел на экране «Ещё»: плитка + слова, по которым его находит поиск. */
private data class MoreItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val keywords: String = "",
    val badge: Int = 0,
)

private enum class Tone { REPORT, FOREST, ME }

/**
 * Экран «Ещё» (редизайн 12.11.2026, прототип «Ещё», вариант А): поиск «Что ищем?»,
 * плашка обновления и плитки по полочкам — ОТЧЁТЫ / ЛЕС / Я. Каждая полочка своим цветом,
 * чтобы с первого взгляда было понятно, где что. Остатки переехали сюда из нижней панели.
 */
@Composable
fun MoreScreen(
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWorkReport: () -> Unit,
    onOpenProba: () -> Unit,
    onOpenInventarizatsiya: () -> Unit,
    onOpenPerevod: () -> Unit,
    onOpenTabel: () -> Unit,
    onOpenSmena: () -> Unit = {},
    onOpenIgra: () -> Unit = {},
    onOpenBreakdown: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
) {
    val context = LocalContext.current
    val lv = LocalLv.current
    val session by SessionManager.getInstance(context).session.collectAsState()
    val badgeManager = remember { NotificationsBadgeManager.getInstance(context) }
    val unreadCount by badgeManager.unreadCount.collectAsState()
    val role = session?.role
    val igra by remember { IgraPrefs.getInstance(context) }.enabled.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val reports = buildList {
        if (role?.canSmena == true) {
            add(MoreItem("Отчёт за смену", Icons.Rounded.LocalShipping, onOpenSmena, "отчет смена трелевка вывозка кубы"))
        } else {
            add(MoreItem("Отчёт", Icons.Rounded.Description, onOpenWorkReport, "отчет работа день"))
        }
        if (role?.canReportBreakdown == true) {
            add(MoreItem("Поломка", Icons.Rounded.Build, onOpenBreakdown, "поломка ремонт техника сломался"))
        }
        if (role?.canManageTabel == true) {
            add(MoreItem("Табель", Icons.Rounded.EventNote, onOpenTabel, "табель часы явка выходы"))
        }
    }
    val forest = buildList {
        if (role?.canViewProba == true) {
            add(MoreItem("Проба рубок ухода", Icons.Rounded.Science, onOpenProba, "проба рубки ухода"))
        }
        if (role?.canManageLesokultury == true) {
            add(MoreItem("Инвента­ризация", Icons.Rounded.Park, onOpenInventarizatsiya, "инвентаризация лесные культуры приживаемость"))
            add(MoreItem("Перевод культур", Icons.Rounded.SwapHoriz, onOpenPerevod, "перевод лесные культуры покрытые"))
        }
        if (role?.canSeeStock == true) {
            add(MoreItem("Остатки", Icons.Rounded.Inventory2, onOpenStock, "остатки делянки склад кубы"))
        }
    }
    val me = buildList {
        add(MoreItem("Профиль", Icons.Rounded.Person, onOpenProfile, "профиль тема крупно выход версия"))
        add(MoreItem("Уведомления", Icons.Rounded.Notifications, onOpenNotifications, "уведомления колокольчик", badge = unreadCount))
        if (igra) add(MoreItem("Мой герой", Icons.Rounded.MilitaryTech, onOpenIgra, "игра герой рейтинг челленджи"))
        add(MoreItem("Что нового", Icons.Rounded.NewReleases, onOpenWhatsNew, "что нового обновление версия"))
    }

    fun List<MoreItem>.filtered(): List<MoreItem> {
        val q = query.trim().lowercase().replace('ё', 'е')
        if (q.isEmpty()) return this
        return filter { (it.label.replace("­", "") + " " + it.keywords).lowercase().replace('ё', 'е').contains(q) }
    }
    val groups = listOf(
        Triple("Отчёты", Tone.REPORT, reports.filtered()),
        Triple("Лес", Tone.FOREST, forest.filtered()),
        Triple("Я", Tone.ME, me.filtered()),
    ).filter { it.third.isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize().background(lv.bg)) {
        LvTopBar(
            title = "Ещё",
            help = "Тут все остальные разделы, разложены по полочкам: отчёты, лес и ваше личное. Не нашли? Напишите слово в поиске «Что ищем?».",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LvSearchField(value = query, onValueChange = { query = it }, placeholder = "Что ищем?")
            UpdateBanner()
            if (groups.isEmpty()) {
                LvEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Ничего не нашлось",
                    text = "Попробуйте другое слово, например «отчёт» или «культуры».",
                    action = "Очистить поиск",
                    onAction = { query = "" },
                )
            }
            groups.forEach { (title, tone, items) ->
                LvSectionLabel(title, modifier = Modifier.padding(top = 4.dp))
                val (bg, tint) = when (tone) {
                    Tone.REPORT -> lv.gS to lv.gT
                    Tone.FOREST -> lv.aS to lv.aT
                    Tone.ME -> lv.sf2 to lv.tx
                }
                TileGrid(items, bg, tint)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TileGrid(items: List<MoreItem>, iconBg: Color, iconTint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEach { item ->
                    LvTile(
                        icon = item.icon,
                        label = item.label,
                        onClick = item.onClick,
                        iconBg = iconBg,
                        iconTint = iconTint,
                        badge = item.badge,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
