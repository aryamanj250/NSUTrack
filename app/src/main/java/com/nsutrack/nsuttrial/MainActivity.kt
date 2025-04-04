package com.nsutrack.nsuttrial

import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
// Removed unused navigationBarsPadding and statusBarsPadding from here, handled by Scaffold/innerPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false) // Correct

        val viewModel = ViewModelProvider(this)[AttendanceViewModel::class.java]
        viewModel.initializeSharedPreferences(this)

        setContent {
            NSUTrackTheme(viewModel = viewModel) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val shouldShowBottomBar = currentDestination?.hierarchy?.any {
                    it.route == Screen.Home.route ||
                            it.route == Screen.Calendar.route ||
                            it.route == Screen.Exams.route ||
                            it.route == Screen.Notices.route
                } ?: false

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (shouldShowBottomBar) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    // Get the layout direction from composition
                    val layoutDirection = LocalLayoutDirection.current

                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                // Only apply padding for other edges, not bottom when bottom bar is visible
                                bottom = if (shouldShowBottomBar) 0.dp else innerPadding.calculateBottomPadding(),
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection)
                            )
                    ) {
                        mainGraph(navController, viewModel)
                    }
                }
            }
        }
        setupPredictiveBack()
    }

    private fun setupPredictiveBack() {
        // Your existing predictive back setup... (unchanged)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Simple finish for now
                    finish()
                }
            })
        }
    }
}