package com.nsutrack.nsuttrial

import android.content.Intent // <-- Import Intent
import android.net.Uri // <-- Import Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
// Icon for logout button is removed, so ExitToApp is no longer needed here
// import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider // Explicit import if needed, often covered by material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color // Keep import, might be needed elsewhere or for custom colors later
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext // <-- Import LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * Google-styled Profile View with simplified animations and modified logout/contact buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountView(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val profileData by viewModel.profileData.collectAsState()
    val isLoading by viewModel.isProfileLoading.collectAsState()
    val errorMessage by viewModel.profileError.collectAsState()
    // Assuming HapticFeedback is correctly set up in your project's context
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current // <-- Get context for Intent

    // Get window size for precise calculations
    val view = LocalView.current
    val density = LocalDensity.current

    // Animation state for entry/exit
    val visible = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    // Sheet states
    val EXPANDED = 0
    val HALF_EXPANDED = 1
    val HIDDEN = 2

    // Handle back button
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
        coroutineScope.launch {
            visible.targetState = false
            delay(200) // Keep delay consistent with animations
            onDismiss()
        }
    }

    // Google-style animation curves (more subtle)
    val standardEasing = FastOutSlowInEasing

    // Dialog
    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                visible.targetState = false
                delay(200)
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false, // Keep false to prevent accidental dismiss
            usePlatformDefaultWidth = false
        )
    ) {
        // Track sheet state
        var sheetState by remember { mutableStateOf(HALF_EXPANDED) }
        var isDragging by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf(0f) }
        var velocityTracker by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim animation
            val scrimAlpha by animateFloatAsState(
                targetValue = if (sheetState == EXPANDED) 0.6f else if (sheetState == HALF_EXPANDED) 0.4f else 0f,
                animationSpec = tween(200, easing = standardEasing),
                label = "ScrimAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                        if (sheetState == EXPANDED) {
                            coroutineScope.launch {
                                sheetState = HALF_EXPANDED
                            }
                        } else {
                            coroutineScope.launch {
                                sheetState = HIDDEN
                                delay(150)
                                visible.targetState = false
                                delay(150) // Match exit animation timings
                                onDismiss()
                            }
                        }
                    }
            )

            // Main sheet animation
            AnimatedVisibility(
                visible = visible.targetState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.9f, // Slightly less bounce
                        stiffness = 350f, // Slightly adjusted stiffness
                        visibilityThreshold = null
                    )
                ) + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(150, easing = standardEasing)
                ) + fadeOut(tween(120))
            ) {
                var screenHeight by remember { mutableStateOf(0) }

                val sheetHeightFraction by animateFloatAsState(
                    targetValue = if (sheetState == EXPANDED) 0.90f else if (sheetState == HALF_EXPANDED) 0.75f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f,
                        visibilityThreshold = 0.001f
                    ),
                    label = "SheetHeight"
                )

                val sheetHeightPx = if (screenHeight > 0) {
                    (screenHeight * sheetHeightFraction).roundToInt()
                } else {
                    0 // Avoid division by zero or invalid height initially
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Calculate height dynamically, ensuring it doesn't exceed 90%
                        .height(with(density) { (sheetHeightPx.coerceAtMost((screenHeight * 0.90f).toInt())).toDp() })
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .onSizeChanged {
                            if (screenHeight == 0) { // Get height only once
                                screenHeight = view.height
                            }
                        }
                        .graphicsLayer {
                            shadowElevation = 4f
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            clip = true
                        },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp // Subtle elevation
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Draggable header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { isDragging = true },
                                        onDragEnd = {
                                            isDragging = false
                                            coroutineScope.launch {
                                                val currentVelocity = velocityTracker
                                                val velocityThreshold = 250f
                                                val offsetThreshold = 30f // Pixels to trigger state change

                                                // Determine target state based on velocity and offset
                                                val targetState = when {
                                                    currentVelocity > velocityThreshold * 1.5f && sheetState == HALF_EXPANDED -> HIDDEN
                                                    currentVelocity > velocityThreshold && sheetState == EXPANDED -> HALF_EXPANDED
                                                    currentVelocity < -velocityThreshold && sheetState == HALF_EXPANDED -> EXPANDED
                                                    dragOffset > offsetThreshold * 2 && sheetState == HALF_EXPANDED -> HIDDEN
                                                    dragOffset > offsetThreshold && sheetState == EXPANDED -> HALF_EXPANDED
                                                    dragOffset < -offsetThreshold && sheetState == HALF_EXPANDED -> EXPANDED
                                                    else -> sheetState // Snap back to current state
                                                }

                                                // Perform haptic feedback based on state change
                                                if (targetState != sheetState) {
                                                    val feedbackType = when (targetState) {
                                                        HIDDEN -> HapticFeedback.FeedbackType.MEDIUM
                                                        else -> HapticFeedback.FeedbackType.LIGHT
                                                    }
                                                    hapticFeedback.performHapticFeedback(feedbackType)
                                                }

                                                // Animate drag offset back to zero if not changing state
                                                if (targetState == sheetState && dragOffset != 0f) {
                                                    animate(
                                                        initialValue = dragOffset, targetValue = 0f,
                                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
                                                    ) { value, _ -> dragOffset = value }
                                                } else {
                                                    // Apply target state
                                                    sheetState = targetState
                                                    // If hiding, trigger dismiss flow
                                                    if (sheetState == HIDDEN) {
                                                        delay(150)
                                                        visible.targetState = false
                                                        delay(150) // Allow exit animation
                                                        onDismiss()
                                                    }
                                                }
                                                // Reset drag offset smoothly if state didn't change immediately to HIDDEN
                                                if (targetState != HIDDEN) dragOffset = 0f
                                                velocityTracker = 0f // Reset velocity
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            // Animate back to zero smoothly on cancel
                                            coroutineScope.launch {
                                                animate(
                                                    initialValue = dragOffset, targetValue = 0f,
                                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
                                                ) { value, _ -> dragOffset = value }
                                                velocityTracker = 0f
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume() // Consume the event

                                            // Calculate drag resistance based on state and direction
                                            val resistanceFactor = when {
                                                // Stronger resistance pulling down when expanded
                                                sheetState == EXPANDED && dragAmount > 0 -> 0.4f
                                                // Slightly more resistance pulling up from half-expanded
                                                sheetState == HALF_EXPANDED && dragAmount < 0 -> 0.6f
                                                // Standard resistance pulling down from half-expanded
                                                sheetState == HALF_EXPANDED && dragAmount > 0 -> 0.8f
                                                else -> 1.0f // No resistance if moving towards allowed direction
                                            }

                                            // Apply drag with resistance
                                            val newOffset = dragOffset + dragAmount * resistanceFactor

                                            // Add velocity tracking (simple moving average)
                                            velocityTracker = 0.7f * velocityTracker + 0.3f * dragAmount * (1000f / 16f) // Rough velocity calculation

                                            // Apply moderated offset, allow slight overdrag
                                            dragOffset = newOffset.coerceIn(-60f, screenHeight * 0.2f) // Allow some overdrag

                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp), // Padding below handle/title
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Drag Handle
                                Box(
                                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    )
                                }

                                // Title
                                Text(
                                    text = "Profile",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp, vertical = 12.dp)
                                )
                            }
                        }

                        // Divider
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(), // No horizontal padding
                            thickness = 0.5.dp, // Thinner divider
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Content area
                        Box(
                            modifier = Modifier
                                .weight(1f) // Takes remaining space
                                .fillMaxWidth()
                        ) {
                            when {
                                isLoading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }

                                errorMessage != null -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Text(
                                                text = "Error loading profile",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = errorMessage ?: "An unknown error occurred.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Button(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                    viewModel.fetchProfileData()
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Retry", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }

                                profileData != null -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                                        // Spacing between items (Sections, Buttons)
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        item {
                                            ProfileHeader(
                                                profileData?.studentName ?: "N/A",
                                                profileData?.studentID ?: "N/A"
                                            )
                                        }

                                        item {
                                            ProfileSection(
                                                title = "Personal Information",
                                                icon = Icons.Filled.Person,
                                                content = {
                                                    ProfileRow("Name", profileData?.studentName ?: "N/A")
                                                    ProfileRow("Student ID", profileData?.studentID ?: "N/A")
                                                    ProfileRow("Date of birth", profileData?.dob ?: "N/A")
                                                    ProfileRow("Gender", profileData?.gender ?: "N/A")
                                                }
                                            )
                                        }

                                        item {
                                            ProfileSection(
                                                title = "Academic Information",
                                                icon = Icons.Filled.School,
                                                content = {
                                                    ProfileRow("Degree", profileData?.degree ?: "N/A")
                                                    ProfileRow("Branch", profileData?.branchName ?: "N/A")

                                                    // Conditionally show Specialization
                                                    if (profileData?.specialization?.uppercase() != profileData?.branchName?.uppercase() &&
                                                        profileData?.specialization?.isNotBlank() == true
                                                    ) {
                                                        ProfileRow("Specialization", profileData?.specialization ?: "N/A")
                                                    }

                                                    ProfileRow("Section", profileData?.section ?: "N/A")
                                                }
                                            )
                                        }

                                        // --- NEW: Contact Us Button Item ---
                                        item {
                                            Button(
                                                onClick = {
                                                    // Haptic feedback for interaction
                                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                                    // Create Intent to open URL
                                                    val url = "https://nsutrack.systems"
                                                    val intent = Intent(Intent.ACTION_VIEW)
                                                    intent.data = Uri.parse(url)
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        // Handle cases where no browser is available or other errors
                                                        Log.e("AccountView", "Could not launch URL $url", e)
                                                        // Optionally show a Toast message to the user
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 32.dp, vertical = 4.dp), // Match Logout style
                                                // Bluish background, matching text color
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                shape = RoundedCornerShape(12.dp), // Match Logout style
                                                elevation = null // Match Logout style (flat)
                                            ) {
                                                Text(
                                                    text = "Contact Us",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        // --- END OF Contact Us Button Item ---


                                        // --- Logout Button Item ---
                                        // Spacing between this and Contact Us is handled by LazyColumn's verticalArrangement
                                        item {
                                            Button(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                    coroutineScope.launch {
                                                        viewModel.logout()
                                                        visible.targetState = false
                                                        delay(150)
                                                        onDismiss()
                                                        onLogout()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 32.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant, // Muted background
                                                    contentColor = MaterialTheme.colorScheme.error // Red text
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = null
                                            ) {
                                                Text(
                                                    text = "Logout",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            // Add space *after* the last button for bottom padding
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                        // --- END OF Logout Button Item ---
                                    }
                                }

                                else -> { // Fallback if data is null
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "No profile data available.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Helper Composables (ProfileHeader, ProfileSection, ProfileRow) remain the same ---

@Composable
fun ProfileHeader(name: String, studentId: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var isLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(100) // Short delay for animation trigger
            isLoaded = true
        }

        val avatarScale by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0.9f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "AvatarScale"
        )
        val avatarAlpha by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0f,
            animationSpec = tween(200),
            label = "AvatarAlpha"
        )

        Surface(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                    alpha = avatarAlpha
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 0.dp // Flat avatar
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?", // Safe first char
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { -10 },
                animationSpec = tween(250, easing = EaseOutQuad)
            )
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface // Use onSurface for primary text
            )
        }

        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(tween(250, 50)) + expandVertically(
                animationSpec = tween(250, 50, easing = EaseOutQuad)
            )
        ) {
            Text(
                text = studentId,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // Secondary text color
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit // Use ColumnScope for content
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(0.8f, Spring.StiffnessMedium))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp), // Padding around header
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer, // Use secondary for variety
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface // Primary text color
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), // Slightly more rounded card corners
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) // Subtle surface variant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat card
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp) // Vertical padding inside card
                // Horizontal padding is handled by ProfileRow
            ) {
                content() // Inject the rows here
            }
        }
    }
}


@Composable
fun ProfileRow(label: String, value: String) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50) // Quick delay for stagger effect
        isVisible = true
    }

    // Improved check for last rows in sections
    val isLastInPersonal = label == "Gender"
    val isLastInAcademic = label == "Section"
    val isLastRow = isLastInPersonal || isLastInAcademic

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInHorizontally(
            initialOffsetX = { -10 },
            animationSpec = tween(200, easing = EaseOutQuad)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp) // Horizontal padding for row content
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp), // Adjusted vertical padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f) // Adjust weight split if needed
                )
                Text(
                    text = formatProfileValue(label, value),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal, // Use Normal weight for values
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(start = 8.dp)
                )
            }

            // Add divider conditionally, excluding the identified last rows
            if (!isLastRow) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(), // Span full width within padding
                    thickness = 0.5.dp, // Thin divider
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// --- Utility Functions ---

// Updated formatter, keeping capitalize logic but removing Mode handling
fun formatProfileValue(label: String, value: String): String {
    if (value.isBlank() || value.equals("N/A", ignoreCase = true)) return "N/A" // Handle empty/NA case

    return when (label) {
        "Student ID" -> value // Keep as is
        "Specialization" -> { // Handle specific capitalization like VLSI
            if (value.uppercase().contains("VLSI")) {
                value.split(" ").joinToString(" ") { word ->
                    if (word.uppercase() == "VLSI") "VLSI" else word.lowercase().capitalizeWord()
                }
            } else {
                value.lowercase().capitalizeWord() // Capitalize first letter
            }
        }
        // Removed "Mode" case
        else -> value.lowercase().capitalizeWord() // Default: Capitalize first letter
    }
}

// Renamed extension function for clarity
fun String.capitalizeWord(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

// REMOVED Placeholder HapticFeedback object - Ensure you have a real implementation or import