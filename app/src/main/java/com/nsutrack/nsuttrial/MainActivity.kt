package com.nsutrack.nsuttrial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.nsutrack.nsuttrial.ui.theme.NSUTrackTheme
import mainGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create ViewModel at the Activity level for persistence across navigation
        val viewModel = ViewModelProvider(this)[AttendanceViewModel::class.java]

        setContent {
            NSUTrackTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
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