package com.lesovod.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lesovod.mobile.data.repository.NotificationsBadgeManager
import com.lesovod.mobile.data.repository.OfflineQueueManager
import com.lesovod.mobile.data.session.SessionExpiryBus
import com.lesovod.mobile.data.session.SessionManager
import com.lesovod.mobile.ui.kubaturnik.KubaturnikScreen
import com.lesovod.mobile.ui.notes.NotesScreen
import com.lesovod.mobile.ui.notifications.NotificationsScreen
import com.lesovod.mobile.ui.proba.ProbaScreen
import com.lesovod.mobile.ui.screens.AttendanceScreen
import com.lesovod.mobile.ui.screens.BreakdownScreen
import com.lesovod.mobile.ui.screens.LoginScreen
import com.lesovod.mobile.ui.screens.MapScreen
import com.lesovod.mobile.ui.screens.ProfileScreen
import com.lesovod.mobile.ui.screens.RegistrationScreen
import com.lesovod.mobile.ui.screens.StockScreen
import com.lesovod.mobile.ui.screens.TasksScreen
import com.lesovod.mobile.ui.screens.WorkReportScreen
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.trelevka.TrelevkaScreen

@Composable
fun LesovodNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination = remember {
        if (SessionManager.getInstance(context).isLoggedIn) Screen.Tasks.route else Screen.Login.route
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        SessionExpiryBus.events.collect {
            navController.navigate(Screen.Login.route) { popUpTo(0) }
            snackbarHostState.showSnackbar("Сессия истекла, войдите заново")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { outerPadding ->
    Box(modifier = Modifier.padding(outerPadding)) {
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
                TasksScreen(
                    onOpenAttendance = { navController.navigate(Screen.Attendance.route) },
                    onOpenProba = { navController.navigate(Screen.Proba.route) },
                )
            }
        }
        composable(Screen.Attendance.route) {
            AttendanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.WorkReport.route) {
            MainScaffold(navController) {
                WorkReportScreen(
                    onReportBreakdown = { navController.navigate(Screen.Breakdown.route) },
                    onReportTrelevka = { navController.navigate(Screen.Trelevka.route) },
                )
            }
        }
        composable(Screen.Breakdown.route) {
            BreakdownScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Trelevka.route) {
            TrelevkaScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Proba.route) {
            ProbaScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Stock.route) { MainScaffold(navController) { StockScreen() } }
        composable(Screen.Kubaturnik.route) { MainScaffold(navController) { KubaturnikScreen() } }
        composable(Screen.Map.route) { MainScaffold(navController) { MapScreen() } }
        composable(Screen.Notes.route) { MainScaffold(navController) { NotesScreen() } }
        composable(Screen.Profile.route) {
            MainScaffold(navController) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    },
                    onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                )
            }
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = {
                    navController.navigate(Screen.Notes.route) {
                        popUpTo(Screen.Notifications.route) { inclusive = true }
                    }
                },
            )
        }
    }
    }
    }
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val queueManager = remember { OfflineQueueManager.getInstance(context) }
    val pending by queueManager.pending.collectAsState()
    val session by SessionManager.getInstance(context).session.collectAsState()
    val navItems = bottomNavItemsFor(session?.role)
    val badgeManager = remember { NotificationsBadgeManager.getInstance(context) }
    val unreadCount by badgeManager.unreadCount.collectAsState()
    LaunchedEffect(Unit) { badgeManager.refresh() }

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                navItems.forEach { item ->
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
                        icon = {
                            if (item.screen == Screen.Profile && unreadCount > 0) {
                                BadgedBox(badge = { Badge { Text(unreadCount.coerceAtMost(99).toString()) } }) {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            } else {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (pending.isNotEmpty()) {
                PendingSyncBanner(count = pending.size, onRetryNow = queueManager::retryNow)
            }
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
private fun PendingSyncBanner(count: Int, onRetryNow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ForestAccent.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Нет сети: $count " + pluralActionsRu(count) + " ждут отправки",
                style = MaterialTheme.typography.bodyMedium,
                color = ForestAccent,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetryNow) {
                Text("Повторить")
            }
        }
    }
}

private fun pluralActionsRu(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "действий"
        mod10 == 1 -> "действие"
        mod10 in 2..4 -> "действия"
        else -> "действий"
    }
}
