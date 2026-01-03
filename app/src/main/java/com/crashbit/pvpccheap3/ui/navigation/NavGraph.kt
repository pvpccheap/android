package com.crashbit.pvpccheap3.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Rule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.crashbit.pvpccheap3.ui.screens.devices.DevicesScreen
import com.crashbit.pvpccheap3.ui.screens.login.LoginScreen
import com.crashbit.pvpccheap3.ui.screens.prices.PricesScreen
import com.crashbit.pvpccheap3.ui.screens.rules.RuleDetailScreen
import com.crashbit.pvpccheap3.ui.screens.rules.RulesScreen
import com.crashbit.pvpccheap3.ui.screens.schedule.ScheduleScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Devices : Screen("devices")
    data object Rules : Screen("rules")
    data object RuleDetail : Screen("rule/{ruleId}") {
        fun createRoute(ruleId: String?) = if (ruleId != null) "rule/$ruleId" else "rule/new"
    }
    data object Schedule : Screen("schedule")
    data object Prices : Screen("prices")
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Devices : BottomNavItem(Screen.Devices.route, "Dispositius", Icons.Default.Home)
    data object Rules : BottomNavItem(Screen.Rules.route, "Regles", Icons.Default.Rule)
    data object Schedule : BottomNavItem(Screen.Schedule.route, "Horari", Icons.Default.DateRange)
    data object Prices : BottomNavItem(Screen.Prices.route, "Preus", Icons.Default.Euro)
}

val bottomNavItems = listOf(
    BottomNavItem.Devices,
    BottomNavItem.Rules,
    BottomNavItem.Schedule,
    BottomNavItem.Prices
)

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        onLoginSuccess()
                        navController.navigate(Screen.Devices.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Devices.route) {
                DevicesScreen()
            }

            composable(Screen.Rules.route) {
                RulesScreen(
                    onNavigateToRuleDetail = { ruleId ->
                        navController.navigate(Screen.RuleDetail.createRoute(ruleId))
                    }
                )
            }

            composable(Screen.RuleDetail.route) { backStackEntry ->
                val ruleId = backStackEntry.arguments?.getString("ruleId")
                RuleDetailScreen(
                    ruleId = if (ruleId == "new") null else ruleId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Schedule.route) {
                ScheduleScreen()
            }

            composable(Screen.Prices.route) {
                PricesScreen()
            }
        }
    }
}
