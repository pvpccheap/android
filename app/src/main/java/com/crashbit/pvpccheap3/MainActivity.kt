package com.crashbit.pvpccheap3

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crashbit.pvpccheap3.data.local.AuthEvent
import com.crashbit.pvpccheap3.data.local.AuthStateManager
import com.crashbit.pvpccheap3.data.local.TokenManager
import com.crashbit.pvpccheap3.data.repository.GoogleHomeRepository
import com.crashbit.pvpccheap3.ui.navigation.NavGraph
import com.crashbit.pvpccheap3.ui.navigation.Screen
import com.crashbit.pvpccheap3.ui.navigation.bottomNavItems
import com.crashbit.pvpccheap3.ui.theme.PvpccheapTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var googleHomeRepository: GoogleHomeRepository

    @Inject
    lateinit var authStateManager: AuthStateManager

    // Estat per forçar navegació a login
    private val _forceLogout = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicialitzar i registrar Google Home ABANS de setContent
        runBlocking {
            googleHomeRepository.initialize()
        }
        googleHomeRepository.registerActivity(this)

        // Escoltar events d'autenticació
        observeAuthEvents()

        // Check if user is logged in
        val isLoggedIn = runBlocking { tokenManager.isLoggedIn.first() }
        val startDestination = if (isLoggedIn) Screen.Devices.route else Screen.Login.route

        setContent {
            PvpccheapTheme {
                val forceLogout by _forceLogout
                MainScreen(
                    startDestination = startDestination,
                    forceLogout = forceLogout,
                    onLogoutHandled = { _forceLogout.value = false }
                )
            }
        }
    }

    private fun observeAuthEvents() {
        lifecycleScope.launch {
            authStateManager.authEvents.collect { event ->
                when (event) {
                    is AuthEvent.AuthenticationRequired -> {
                        // Netejar dades d'auth i forçar login
                        tokenManager.clearAuthData()
                        _forceLogout.value = true
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Sessió expirada. Si us plau, torna a iniciar sessió.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    is AuthEvent.TokenRefreshed -> {
                        // Token refrescat correctament, no cal fer res
                    }
                    is AuthEvent.AuthError -> {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Error d'autenticació: ${event.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    startDestination: String,
    forceLogout: Boolean = false,
    onLogoutHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show bottom nav only for main screens (not login or rule detail)
    val showBottomNav = currentDestination?.route in bottomNavItems.map { it.route }

    var isLoggedIn by remember { mutableStateOf(startDestination != Screen.Login.route) }

    // Navegar a login quan el token expira
    LaunchedEffect(forceLogout) {
        if (forceLogout) {
            isLoggedIn = false
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
            onLogoutHandled()
        }
    }

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
