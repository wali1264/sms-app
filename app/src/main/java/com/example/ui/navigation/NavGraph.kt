package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.TeacherAttendanceApp
import com.example.ui.attendance.AttendanceScreen
import com.example.ui.attendance.AttendanceViewModel
import com.example.ui.messages.MessagesScreen
import com.example.ui.messages.MessagesViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.students.StudentsScreen
import com.example.ui.students.StudentsViewModel
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryContainerBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Attendance : Screen("attendance", "حضور", Icons.Default.CheckCircle)
    object Students : Screen("students", "شاگردان", Icons.Default.People)
    object Messages : Screen("messages", "پیام‌ها", Icons.Default.Message)
    object Settings : Screen("settings", "تنظیمات", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Attendance,
    Screen.Students,
    Screen.Messages,
    Screen.Settings
)

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val repository = TeacherAttendanceApp.instance.repository

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Attendance.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Attendance.route) {
                val viewModel: AttendanceViewModel = viewModel(
                    factory = AttendanceViewModel.Factory(repository)
                )
                AttendanceScreen(
                    viewModel = viewModel,
                    onNavigateToStudents = {
                        navController.navigate(Screen.Students.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Students.route) {
                val viewModel: StudentsViewModel = viewModel(
                    factory = StudentsViewModel.Factory(repository)
                )
                StudentsScreen(viewModel = viewModel)
            }

            composable(Screen.Messages.route) {
                val viewModel: MessagesViewModel = viewModel(
                    factory = MessagesViewModel.Factory(repository)
                )
                MessagesScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(repository)
                )
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Color.White,
        contentColor = TextPrimary
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(
                        text = screen.title,
                        color = if (selected) PrimaryBlue else TextSecondary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    indicatorColor = PrimaryContainerBlue,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                ),
                modifier = Modifier.testTag("nav_tab_${screen.route}")
            )
        }
    }
}
