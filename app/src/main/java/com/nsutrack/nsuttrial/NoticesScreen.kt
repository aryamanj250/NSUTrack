package com.nsutrack.nsuttrial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun IMSNoticesScreen(viewModel: AttendanceViewModel) {
    // Get the IMS Notifications ViewModel
    val imsViewModel: IMSNotificationsViewModel = viewModel()

    // Get haptic feedback handler
    val hapticFeedback = HapticFeedback.getHapticFeedback()

    Scaffold(
        topBar = {
            // Use EnhancedTopAppBar with null profileData to hide profile icon
            EnhancedTopAppBar(
                title = "Notices",
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            IMSNoticesView(imsViewModel)
        }
    }
}