package com.nsutrack.nsuttrial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign


@Composable
fun AccountView(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val profileData by viewModel.profileData.collectAsState()
    val isLoading by viewModel.isProfileLoading.collectAsState()
    val errorMessage by viewModel.profileError.collectAsState()
    val hapticFeedback = HapticFeedback.getHapticFeedback()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                when {
                    isLoading -> {
                        // Loading state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    errorMessage != null -> {
                        // Error state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Error loading profile data",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = errorMessage ?: "",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                        viewModel.fetchProfileData()
                                    },
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    profileData != null -> {
                        // Profile data
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            item {
                                SectionTitle(
                                    title = "Personal Information",
                                    icon = Icons.Filled.Person
                                )

                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.elevatedCardElevation(
                                        defaultElevation = 2.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        ProfileRow(label = "Name", value = profileData?.studentName ?: "")
                                        ProfileRow(label = "Student ID", value = profileData?.studentID ?: "")
                                        ProfileRow(label = "Date of birth", value = profileData?.dob ?: "")
                                        ProfileRow(label = "Gender", value = profileData?.gender ?: "")
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                SectionTitle(
                                    title = "Academic Information",
                                    icon = Icons.Filled.School
                                )

                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.elevatedCardElevation(
                                        defaultElevation = 2.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        ProfileRow(label = "Degree", value = profileData?.degree ?: "")
                                        ProfileRow(label = "Branch", value = profileData?.branchName ?: "")

                                        // Only show specialization if different from branch
                                        if (profileData?.specialization?.uppercase() != profileData?.branchName?.uppercase()) {
                                            ProfileRow(label = "Specialization", value = profileData?.specialization ?: "")
                                        }

                                        ProfileRow(label = "Section", value = profileData?.section ?: "")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // No data state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No profile data available",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Close button
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label on the left side
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        // Value on the right side with right alignment
        Text(
            text = formatProfileValue(label, value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,  // Right-align the text
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)  // Take up equal space as the label
                .padding(start = 8.dp)  // Add some spacing between label and value
        )
    }
}

// Format profile values appropriately
fun formatProfileValue(label: String, value: String): String {
    return when (label) {
        "Student ID" -> value  // Keep student ID as is
        "Specialization" -> {
            if (value.uppercase().contains("VLSI")) {
                // Handle VLSI case (keep VLSI in uppercase)
                value.split(" ").joinToString(" ") { word ->
                    if (word.uppercase() == "VLSI") "VLSI" else word.toLowerCase().capitalize()
                }
            } else {
                value.toLowerCase().capitalize()
            }
        }
        else -> value.toLowerCase().capitalize()  // Capitalize other values
    }
}

// Extension functions for string capitalization
fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

fun String.toLowerCase(): String {
    return this.lowercase()
}