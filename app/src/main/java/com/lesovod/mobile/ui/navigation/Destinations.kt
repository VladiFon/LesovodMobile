package com.lesovod.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
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
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

/**
 * Доступные роли видят разный набор вкладок (см. таблицу прав в задаче):
 * «Остатки по делянкам» и «Кубатурник» — только мастер / пом. лесничего / лесничий.
 * Задачи, отчёт, карта, заметки и профиль доступны всем ролям.
 */
fun bottomNavItemsFor(role: WorkerRole?): List<BottomNavItem> {
    val canSeeStock = role?.canSeeStock == true
    val canUseKubaturnik = role?.canUseKubaturnik == true
    return listOfNotNull(
        BottomNavItem(Screen.Tasks, "Задачи", Icons.Filled.Assignment),
        BottomNavItem(Screen.WorkReport, "Отчёт", Icons.Filled.PostAdd),
        BottomNavItem(Screen.Stock, "Остатки", Icons.Filled.Inventory2).takeIf { canSeeStock },
        BottomNavItem(Screen.Kubaturnik, "Кубатурник", Icons.Filled.Calculate).takeIf { canUseKubaturnik },
        BottomNavItem(Screen.Map, "Карта", Icons.Filled.Map),
        BottomNavItem(Screen.Notes, "Заметки", Icons.Filled.Notifications),
        BottomNavItem(Screen.Profile, "Профиль", Icons.Filled.Person),
    )
}
