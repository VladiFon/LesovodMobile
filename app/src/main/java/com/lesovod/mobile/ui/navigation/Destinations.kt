package com.lesovod.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
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
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Tasks, "Задачи", Icons.Filled.Assignment),
    BottomNavItem(Screen.WorkReport, "Отчёт", Icons.Filled.PostAdd),
    BottomNavItem(Screen.Stock, "Остатки", Icons.Filled.Inventory2),
    BottomNavItem(Screen.Map, "Карта", Icons.Filled.Map),
    BottomNavItem(Screen.Profile, "Профиль", Icons.Filled.Person),
)
