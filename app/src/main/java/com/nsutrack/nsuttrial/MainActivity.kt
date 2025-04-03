package com.nsutrack.nsuttrial

import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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

        // Make app truly full screen with no insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Hide system bars completely if needed
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )

        // Create ViewModel
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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        bottomBar = {
                            if (shouldShowBottomBar) {
                                BottomNavBar(navController)
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "login",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            mainGraph(navController, viewModel)
                        }
                    }
                }
            }
        }


        fun setupPredictiveBack() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Register the OnBackPressedCallback
                onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // For Android 13+, we'll use a simple implementation that doesn't rely on BackEvent.PROGRESS_RANGE
                        if (Build.VERSION.SDK_INT >= 33) {
                            try {
                                // Use the finishAfterTransition for a smoother exit animation
                                finishAfterTransition()
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Predictive back error: ${e.message}")
                                finish()
                            }
                        } else {
                            // Standard back behavior for older Android versions
                            finish()
                        }
                    }
                })
            }
        }
    }
}

