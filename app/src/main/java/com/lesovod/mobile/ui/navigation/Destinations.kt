package com.lesovod.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.ui.graphics.vector.ImageVector
import com.lesovod.mobile.data.session.WorkerRole
import com.lesovod.mobile.data.session.canUseKubaturnik

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Registration : Screen("registration")
    data object Tasks : Screen("tasks")
    data object WorkReport : Screen("work_report")
    data object Stock : Screen("stock")
    data object Map : Screen("map")
    data object Profile : Screen("profile")
    data object Breakdown : Screen("breakdown")
    data object Attendance : Screen("attendance")
    data object Kubaturnik : Screen("kubaturnik")
    data object Proba : Screen("proba")
    /** «Отчёт за смену» (бывш. «Трелёвка», маршрут прежний). */
    data object Trelevka : Screen("trelevka")
    data object Igra : Screen("igra")
    data object Notes : Screen("notes")
    data object Notifications : Screen("notifications")
    data object Inventarizatsiya : Screen("lesokultury_inventarizatsiya")
    data object Perevod : Screen("lesokultury_perevod")
    data object Tabel : Screen("tabel")
    data object More : Screen("more")
    data object WhatsNew : Screen("whats_new")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

/**
 * Нижняя навигация редизайна 12.11.2026 (прототип «Лесовод»): Смена, Кубатурник (мастерам),
 * Карта, Заметки, Ещё. Остатки переехали в «Ещё» (полочка «Лес»): Влад смотрит их на сайте,
 * в телефоне они нужны редко. Остальные разделы по ролям тоже в «Ещё».
 */
fun bottomNavItems(role: WorkerRole?): List<BottomNavItem> = buildList {
    add(BottomNavItem(Screen.Tasks, "Смена", Icons.Rounded.Badge))
    if (role?.canUseKubaturnik == true) {
        add(BottomNavItem(Screen.Kubaturnik, "Кубатурник", Icons.Rounded.Straighten))
    }
    add(BottomNavItem(Screen.Map, "Карта", Icons.Rounded.Map))
    add(BottomNavItem(Screen.Notes, "Заметки", Icons.Rounded.Chat))
    add(BottomNavItem(Screen.More, "Ещё", Icons.Rounded.Apps))
}
