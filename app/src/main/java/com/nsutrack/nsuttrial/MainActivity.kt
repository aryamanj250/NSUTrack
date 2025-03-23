package com.nsutrack.nsuttrial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nsutrack.nsuttrial.navigation.BottomNavBar
import com.nsutrack.nsuttrial.navigation.Screen
import com.nsutrack.nsuttrial.navigation.mainGraph
import com.nsutrack.nsuttrial.ui.theme.NSUTrackTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create ViewModel at the Activity level for persistence across navigation
        val viewModel = ViewModelProvider(this)[AttendanceViewModel::class.java]

        setContent {
            NSUTrackTheme(viewModel = viewModel) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()

                    // Get current destination
                    val currentDestination = navBackStackEntry?.destination

                    // Determine whether to show bottom navigation
                    val shouldShowBottomBar = currentDestination?.hierarchy?.any {
                        it.route == Screen.Home.route ||
                                it.route == Screen.Calendar.route ||
                                it.route == Screen.Exams.route ||
                                it.route == Screen.Notices.route
                    } ?: false

                    Scaffold(
                        bottomBar = {
                            if (shouldShowBottomBar) {
                                BottomNavBar(navController)
                            }
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = "login"
                            ) {
                                // Pass the viewModel instance to the navigation graph
                                mainGraph(navController, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}