// navigation/Screen.kt
package com.nsutrack.nsuttrial.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )

    object Calendar : Screen(
        route = "calendar",
        title = "Calendar",
        icon = Icons.Default.DateRange
    )

    object Exams : Screen(
        route = "exams",
        title = "Exams",
        icon = Icons.Default.School
    )

    object Notices : Screen(
        route = "notices",
        title = "Notices",
        icon = Icons.Default.Notifications
    )

    object Login : Screen(
        route = "login",
        title = "Login",
        icon = Icons.Default.Home // Placeholder, not used
    )
}