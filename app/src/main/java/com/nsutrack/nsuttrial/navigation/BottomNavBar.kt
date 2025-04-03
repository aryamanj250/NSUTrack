package com.nsutrack.nsuttrial.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nsutrack.nsuttrial.HapticFeedback

@Composable
fun BottomNavBar(navController: NavController) {
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val screens = listOf(Screen.Home, Screen.Calendar, Screen.Exams, Screen.Notices)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = (0).dp
    ) {
        // Top divider line
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                thickness = 0.dp,
                color = Color.Gray.copy(alpha = 0.3f)
            )

            // Navigation items
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                onClick = {
                                    if (!selected) {
                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Item content
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            val color = if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = screen.title,
                                color = color,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )

                            // Show indicator if selected
                            if (selected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(color = MaterialTheme.colorScheme.primary)
                                )
                            } else {
                                // Empty spacer to maintain consistent height
                                Spacer(modifier = Modifier.height(7.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}