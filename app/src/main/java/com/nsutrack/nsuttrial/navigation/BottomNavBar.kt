package com.nsutrack.nsuttrial.navigation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
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

    NavigationBar(
        modifier = Modifier.height(90.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 5.dp
    ) {
        screens.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

            // Animation values for iOS-like subtle animations
            val animatedScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1.0f,
                animationSpec = tween(
                    durationMillis = 200,
                    easing = FastOutSlowInEasing
                ),
                label = "scale"
            )

            // Colors based on selection state
            val iconColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            val textColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }

            NavigationBarItem(
                icon = {
                    Box(modifier = Modifier.scale(animatedScale)) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            modifier = Modifier.size(26.dp),
                            tint = iconColor
                        )
                    }
                },
                label = {
                    // Always display text labels
                    Text(
                        text = screen.title,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                },
                selected = selected,
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
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                alwaysShowLabel = true  // This ensures labels are always shown
            )
        }
    }
}