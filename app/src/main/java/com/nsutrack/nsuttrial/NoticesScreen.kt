package com.nsutrack.nsuttrial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nsutrack.nsuttrial.AttendanceViewModel
import com.nsutrack.nsuttrial.EnhancedTopAppBar
import com.nsutrack.nsuttrial.HapticFeedback
import com.nsutrack.nsuttrial.IMSNotificationsViewModel
import com.nsutrack.nsuttrial.NoticesView

@Composable
fun IMSNoticesScreen(viewModel: AttendanceViewModel) {
    // Get the IMS Notifications ViewModel
    val imsViewModel: IMSNotificationsViewModel = viewModel()

    // Get haptic feedback handler
    val hapticFeedback = HapticFeedback.getHapticFeedback()

    // Get profile data from your existing ViewModel for the top bar
    val profileData by viewModel.profileData.collectAsState()

    Scaffold(
        topBar = {
            // Use your existing EnhancedTopAppBar component
            EnhancedTopAppBar(
                title = "Notices", // Use a string literal instead of a resource
                profileData = profileData,
                onProfileClick = {
                    // Your existing profile click handler logic here
                },
                hapticFeedback = hapticFeedback
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Use the NoticesView composable from IMSNotificationsView.kt
            NoticesView(imsViewModel)
        }
    }
}