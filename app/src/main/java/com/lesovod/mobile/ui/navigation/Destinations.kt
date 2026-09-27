package com.lesovod.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.lesovod.mobile.data.session.WorkerRole
import com.lesovod.mobile.data.session.canSeeStock
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
    data object Trelevka : Screen("trelevka")
    data object Notes : Screen("notes")
    data object Notifications : Screen("notifications")
    data object Inventarizatsiya : Screen("lesokultury_inventarizatsiya")
    data object Perevod : Screen("lesokultury_perevod")
    data object Tabel : Screen("tabel")
    data object More : Screen("more")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

/**
 * Нижняя навигация редизайна «Поляна» (docs/SCREENS.md, раздел «Навигация»): постоянные
 * вкладки «Смена», «Карта», «Заметки», «Ещё» плюс «Остатки»/«Кубатурник» для ролей, у
 * которых есть доступ к этим разделам (по просьбе пользователя они вернулись в нижнюю панель,
 * а не спрятаны в «Ещё»). Остальные экраны, доступные не всем ролям (Проба рубок ухода,
 * Лесные культуры), остаются внутри «Ещё» и плавающего «+»
 * (см. [com.lesovod.mobile.ui.components.QuickActionsSheet]).
 */
fun bottomNavItems(role: WorkerRole?): List<BottomNavItem> = buildList {
    add(BottomNavItem(Screen.Tasks, "Смена", Icons.Filled.Assignment))
    if (role?.canUseKubaturnik == true) {
        add(BottomNavItem(Screen.Kubaturnik, "Кубатурник", Icons.Filled.Calculate))
    }
    if (role?.canSeeStock == true) {
        add(BottomNavItem(Screen.Stock, "Остатки", Icons.Filled.Inventory2))
    }
    add(BottomNavItem(Screen.Map, "Карта", Icons.Filled.Map))
    add(BottomNavItem(Screen.Notes, "Заметки", Icons.Filled.EditNote))
    add(BottomNavItem(Screen.More, "Ещё", Icons.Filled.MoreHoriz))
}
