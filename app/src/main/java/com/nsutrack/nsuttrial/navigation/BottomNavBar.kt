package com.nsutrack.nsuttrial.navigation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // Create breathing animation
    val breathingAnimation = rememberInfiniteTransition(label = "BreathingAnimation")
    val breathScale by breathingAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingScale"
    )

    // Animate background opacity
    val bgOpacityAnimation by breathingAnimation.animateFloat(
        initialValue = 0.88f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgOpacity"
    )

    // Enhanced floating bottom bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp) // Increased bottom padding to lift it up
    ) {
        // Blurred background effect with enhanced shadow for floating effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(28.dp))
                .blur(radius = 3.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = bgOpacityAnimation - 0.03f),
                            MaterialTheme.colorScheme.surface.copy(alpha = bgOpacityAnimation + 0.05f)
                        )
                    )
                )
        )

        // Glass effect overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Actual navigation bar
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            screens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                // Enhanced animations for nav items
                val animatedScale by animateFloatAsState(
                    targetValue = if (selected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "NavItemScale"
                )

                // Colors based on selection state with more vibrant contrasts
                val iconColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }

                val textColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }

                // Apply subtle breathing animation only to selected item
                val itemScale = if (selected) animatedScale * breathScale else animatedScale

                // NavItem with enhanced effects
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .scale(itemScale)
                        .graphicsLayer {
                            alpha = if (selected) 1f else 0.88f
                        }
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
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    // Icon with subtle glow for selected item
                    Box(
                        modifier = Modifier
                            .size(if (selected) 30.dp else 26.dp)
                            .graphicsLayer {
                                if (selected) {
                                    shadowElevation = 10f
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Subtle background for selected icon
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            )
                        }

                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = iconColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Always display text labels with improved style
                    Text(
                        text = screen.title,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}