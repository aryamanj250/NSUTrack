package com.nsutrack.nsuttrial.navigation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nsutrack.nsuttrial.HapticFeedback
import com.nsutrack.nsuttrial.ui.util.clickable

@Composable
fun BottomNavBar(navController: NavController) {
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val screens = listOf(Screen.Home, Screen.Calendar, Screen.Exams, Screen.Notices)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(85.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            screens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                val animatedWeight by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 1f,
                    animationSpec = tween(durationMillis = 200),
                    label = "NavItemWeight"
                )

                val iconColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .weight(animatedWeight)
                        .fillMaxHeight()
                        .clickable(enabled = !selected) {
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
                        }
                        // Reduced the top padding to move content up further
                        .padding(top = 5.dp)
                ) {
                    // Selected indicator - positioned higher
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = iconColor,
                        modifier = Modifier.size(26.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = screen.title,
                        color = iconColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}
