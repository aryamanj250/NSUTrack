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

fun NavGraphBuilder.mainGraph(navController: NavController, viewModel: AttendanceViewModel) {
    composable("login") {
        Log.d("NavGraph", "Login screen with ViewModel: ${viewModel.hashCode()}")

        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = {
                navController.navigate("home") {
                    // Remove login screen from back stack
                    popUpTo("login") { inclusive = true }
                }
            }
        )
    }

    composable("home") {
        Log.d("NavGraph", "Home screen with ViewModel: ${viewModel.hashCode()}")

        HomeScreen(
            navController = navController,
            viewModel = viewModel
        )
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