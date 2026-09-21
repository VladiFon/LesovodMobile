package com.lesovod.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.screens.AttendanceScreen
import com.lesovod.mobile.ui.screens.BreakdownScreen
import com.lesovod.mobile.ui.screens.LoginScreen
import com.lesovod.mobile.ui.screens.MapScreen
import com.lesovod.mobile.ui.screens.ProfileScreen
import com.lesovod.mobile.ui.screens.RegistrationScreen
import com.lesovod.mobile.ui.screens.StockScreen
import com.lesovod.mobile.ui.screens.TasksScreen
import com.lesovod.mobile.ui.screens.WorkReportScreen

@Composable
fun LesovodNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination = remember {
        if (SessionManager.getInstance(context).isLoggedIn) Screen.Tasks.route else Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Tasks.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClick = { navController.navigate(Screen.Registration.route) },
            )
        }
        composable(Screen.Registration.route) {
            RegistrationScreen(onBackToLogin = { navController.popBackStack() })
        }
        composable(Screen.Tasks.route) {
            MainScaffold(navController) {
                TasksScreen(onOpenAttendance = { navController.navigate(Screen.Attendance.route) })
            }
        }
        composable(Screen.Attendance.route) {
            AttendanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.WorkReport.route) {
            MainScaffold(navController) {
                WorkReportScreen(onReportBreakdown = { navController.navigate(Screen.Breakdown.route) })
            }
        }
        composable(Screen.Breakdown.route) {
            BreakdownScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Stock.route) { MainScaffold(navController) { StockScreen() } }
        composable(Screen.Map.route) { MainScaffold(navController) { MapScreen() } }
        composable(Screen.Profile.route) {
            MainScaffold(navController) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
