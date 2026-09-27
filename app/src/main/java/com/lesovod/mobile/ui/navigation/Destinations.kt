package com.lesovod.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

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
    data object More : Screen("more")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

/**
 * Нижняя навигация редизайна «Поляна» (docs/SCREENS.md, раздел «Навигация»): три
 * постоянные вкладки + «Ещё» — одинаковый набор для всех ролей. Экраны, доступные не всем
 * ролям (Остатки, Кубатурник, Проба рубок ухода, Лесные культуры), спрятаны внутри «Ещё» и
 * плавающего «+» (см. [com.lesovod.mobile.ui.components.QuickActionsSheet]) и фильтруются там.
 */
fun bottomNavItems(): List<BottomNavItem> = listOf(
    BottomNavItem(Screen.Tasks, "Смена", Icons.Filled.Assignment),
    BottomNavItem(Screen.Map, "Карта", Icons.Filled.Map),
    BottomNavItem(Screen.Notes, "Заметки", Icons.Filled.EditNote),
    BottomNavItem(Screen.More, "Ещё", Icons.Filled.MoreHoriz),
)
