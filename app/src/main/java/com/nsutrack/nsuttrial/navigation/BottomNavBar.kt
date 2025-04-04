package com.nsutrack.nsuttrial.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nsutrack.nsuttrial.HapticFeedback
import kotlin.math.roundToInt

@Composable
fun BottomNavBar(navController: NavController) {
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val screens = listOf(Screen.Home, Screen.Calendar, Screen.Exams, Screen.Notices)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Apply a surface with rounded top corners and proper height
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(), // This handles the navigation bar area properly
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
    ) {
        NavigationBar(
            containerColor = Color.Transparent, // Keep transparent to let the Surface color show
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Fixed height that looks good with navigation gestures
        ) {
            screens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route == screen.route
                } == true

                val labelWeight by animateFloatAsState(
                    targetValue = if (selected) FontWeight.SemiBold.weight.toFloat() else FontWeight.Normal.weight.toFloat(),
                    animationSpec = tween(200),
                    label = "NavLabelWeight"
                )

                val iconColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "NavIconColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "NavTextColor"
                )

                NavigationBarItem(
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
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight(labelWeight.roundToInt())
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        unselectedIconColor = iconColor,
                        unselectedTextColor = textColor
                    )
                )
            }
        }
    }
}