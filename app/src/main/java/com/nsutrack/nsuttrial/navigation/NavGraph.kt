package com.nsutrack.nsuttrial.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import android.util.Log
import com.nsutrack.nsuttrial.AttendanceViewModel
import com.nsutrack.nsuttrial.LoginScreen
import com.nsutrack.nsuttrial.HomeScreen
import com.nsutrack.nsuttrial.ui.CalendarScreen
import com.nsutrack.nsuttrial.ui.ExamsScreen
import com.nsutrack.nsuttrial.ui.NoticesScreen

fun NavGraphBuilder.mainGraph(navController: NavController, viewModel: AttendanceViewModel) {
    composable(Screen.Login.route) {
        Log.d("NavGraph", "Login screen with ViewModel: ${viewModel.hashCode()}")

        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = {
                navController.navigate(Screen.Home.route) {
                    // Remove login screen from back stack
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        )
    }

    composable(Screen.Home.route) {
        Log.d("NavGraph", "Home screen with ViewModel: ${viewModel.hashCode()}")

        HomeScreen(
            navController = navController,
            viewModel = viewModel
        )
    }

    composable(Screen.Calendar.route) {
        CalendarScreen(viewModel = viewModel)
    }

    composable(Screen.Exams.route) {
        ExamsScreen(viewModel = viewModel)
    }

    composable(Screen.Notices.route) {
        NoticesScreen(viewModel = viewModel)
    }

    // Keep this for backward compatibility
    composable(
        route = "attendance_detail/{subjectName}",
        arguments = listOf(
            navArgument("subjectName") {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        // We don't need this anymore since we're using dialog-based detailed view
        // Just redirect back to home
        LaunchedEffect(key1 = Unit) {
            navController.navigateUp()
        }
    }
}