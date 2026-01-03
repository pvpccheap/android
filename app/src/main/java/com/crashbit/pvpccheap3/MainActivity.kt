package com.crashbit.pvpccheap3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crashbit.pvpccheap3.data.local.TokenManager
import com.crashbit.pvpccheap3.data.repository.GoogleHomeRepository
import com.crashbit.pvpccheap3.ui.navigation.NavGraph
import com.crashbit.pvpccheap3.ui.navigation.Screen
import com.crashbit.pvpccheap3.ui.navigation.bottomNavItems
import com.crashbit.pvpccheap3.ui.theme.PvpccheapTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var googleHomeRepository: GoogleHomeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicialitzar i registrar Google Home ABANS de setContent
        runBlocking {
            googleHomeRepository.initialize()
        }
        googleHomeRepository.registerActivity(this)

        // Check if user is logged in
        val isLoggedIn = runBlocking { tokenManager.isLoggedIn.first() }
        val startDestination = if (isLoggedIn) Screen.Devices.route else Screen.Login.route

        setContent {
            PvpccheapTheme {
                MainScreen(startDestination = startDestination)
            }
        }
    }
}

@Composable
fun MainScreen(startDestination: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show bottom nav only for main screens (not login or rule detail)
    val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

    var isLoggedIn by remember { mutableStateOf(startDestination != Screen.Login.route) }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            startDestination = startDestination,
            onLoginSuccess = { isLoggedIn = true },
            modifier = Modifier.padding(innerPadding)
        )
    }
}
