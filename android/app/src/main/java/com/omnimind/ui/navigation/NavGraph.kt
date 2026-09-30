package com.omnimind.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnimind.ui.about.AboutScreen
import com.omnimind.ui.chat.ChatScreen
import com.omnimind.ui.chat.ChatViewModel
import com.omnimind.ui.diagnostics.DiagnosticsScreen
import com.omnimind.ui.diagnostics.DiagnosticsViewModel
import com.omnimind.ui.models.ModelManagerScreen
import com.omnimind.ui.models.ModelManagerViewModel
import com.omnimind.ui.settings.SettingsScreen
import com.omnimind.ui.settings.SettingsViewModel
import com.omnimind.ui.theme.PrimaryTeal

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chat : Screen("chat", "Chat", Icons.Default.ChatBubble)
    object Models : Screen("models", "Models", Icons.Default.Storage)
    object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Default.Speed)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object About : Screen("about", "About", Icons.Default.Info)
}

@Composable
fun OmniMindAppScaffold(
    chatViewModel: ChatViewModel,
    modelManagerViewModel: ModelManagerViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Chat.route

    val screens = listOf(
        Screen.Chat,
        Screen.Models,
        Screen.Diagnostics,
        Screen.Settings,
        Screen.About
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                screens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Chat.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryTeal,
                            selectedTextColor = PrimaryTeal,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(viewModel = chatViewModel)
            }
            composable(Screen.Models.route) {
                ModelManagerScreen(viewModel = modelManagerViewModel)
            }
            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(viewModel = diagnosticsViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
            composable(Screen.About.route) {
                AboutScreen()
            }
        }
    }
}
