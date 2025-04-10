package com.nsutrack.nsuttrial

import android.content.Intent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.roundToInt
import androidx.core.net.toUri

// Define custom easing curves for smooth animations
private val SmoothDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val SmoothAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

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
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    // Define sheet states - Only Expanded (visible) and Hidden
    val EXPANDED = 0
    val HIDDEN = 2

    // Animation state for entry/exit
    val visible = remember { MutableTransitionState(false) }

    // Sheet state and drag state - Start in EXPANDED state
    var sheetState by remember { mutableStateOf(EXPANDED) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var velocityTracker by remember { mutableStateOf(0f) }

    // Trigger visibility animation shortly after composition
    LaunchedEffect(Unit) {
        delay(50) // Small delay before starting animation
        visible.targetState = true
    }

    // Common dismiss logic
    val dismissSheet: () -> Unit = {
        coroutineScope.launch {
            sheetState = HIDDEN // Ensure state changes first
            visible.targetState = false
            // Use exit animation duration for delay
            delay(300) // Match the slideOutVertically duration
            onDismiss()
        }
    }

    // Handle back button press
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
        dismissSheet()
    }

    Dialog(
        onDismissRequest = {
            // Also handle dismiss request (e.g., system back gesture)
            dismissSheet()
        },
        properties = DialogProperties(
            dismissOnBackPress = true, // Should trigger BackHandler
            dismissOnClickOutside = false, // Prevent accidental dismiss
            usePlatformDefaultWidth = false
        )
    ) {
        var screenHeight by remember { mutableStateOf(0) } // Store screen height

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim animation (simplified)
            val scrimAlpha by animateFloatAsState(
                targetValue = if (sheetState == EXPANDED) 0.6f else 0f, // Only depends on expanded state
                animationSpec = tween(300), // Match exit duration
                label = "ScrimAlpha"
            )

            // Clickable Scrim Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Click outside dismisses the sheet
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                        dismissSheet()
                    }
            )

            // Main sheet animation
            AnimatedVisibility(
                visible = visible.targetState,
                enter = slideInVertically(
                    initialOffsetY = { it }, // Start from bottom
                    animationSpec = tween(
                        durationMillis = 400, // Duration for smooth entry
                        easing = SmoothDecelerateEasing // Custom decelerate curve
                    )
                ) + fadeIn(animationSpec = tween(150)), // Quick fade in
                exit = slideOutVertically(
                    targetOffsetY = { it }, // Slide down
                    animationSpec = tween(
                        durationMillis = 300, // Exit duration
                        easing = SmoothAccelerateEasing // Custom accelerate curve
                    )
                ) + fadeOut(tween(100)) // Faster fade out
            ) {
                // Calculate sheet height based on state (85% or 0)
                val sheetHeightFraction by animateFloatAsState(
                    targetValue = if (sheetState == EXPANDED) 0.85f else 0f,
                    animationSpec = spring( // Use spring for height change during drag/dismiss for feel
                        dampingRatio = 0.8f,
                        stiffness = 400f,
                        visibilityThreshold = 0.001f
                    ),
                    label = "SheetHeight"
                )

                val sheetHeightPx = if (screenHeight > 0) {
                    (screenHeight * sheetHeightFraction).roundToInt()
                } else {
                    0 // Avoid division by zero initially
                }

                // Animate corner radius (subtle polish during dismiss drag)
                val cornerRadius by animateDpAsState(
                    targetValue = if (sheetState == EXPANDED) 16.dp else 24.dp,
                    animationSpec = tween(200),
                    label = "SheetCornerRadius"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Set height, ensuring it doesn't exceed 85%
                        .height(with(density) { (sheetHeightPx.coerceAtMost((screenHeight * 0.85f).toInt())).toDp() })
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .onSizeChanged {
                            // Capture screen height once
                            if (screenHeight == 0) {
                                screenHeight = view.height
                            }
                        }
                        .graphicsLayer {
                            shadowElevation = 4f // Consistent shadow
                            shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                            clip = true
                        },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Draggable header area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isDragging = true
                                            // Optional: Tiny haptic feedback on drag start
                                            // hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.TOUCH)
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            coroutineScope.launch {
                                                val currentVelocity = velocityTracker
                                                // Thresholds for dismissing downwards
                                                val velocityDismissThreshold = 350f
                                                val offsetDismissThreshold = 80f // Needs to be dragged down this much

                                                when {
                                                    // Check if drag/velocity meets dismiss criteria
                                                    currentVelocity > velocityDismissThreshold || dragOffset > offsetDismissThreshold -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                        // Trigger the common dismiss logic
                                                        dismissSheet()
                                                    }
                                                    // Otherwise, snap back to 0 offset
                                                    else -> {
                                                        animate(
                                                            initialValue = dragOffset,
                                                            targetValue = 0f,
                                                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
                                                        ) { value, _ -> dragOffset = value }
                                                    }
                                                }
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
                                            change.consume() // Consume the drag event

                                            // Apply resistance when trying to drag UP (negative dragAmount)
                                            val resistanceFactor = if (dragAmount < 0) 0.3f else 1.0f

                                            // Update velocity tracker
                                            velocityTracker = 0.7f * velocityTracker + 0.3f * dragAmount * (1000f / 16f) // Rough velocity

                                            // Calculate new offset with resistance
                                            val newOffset = dragOffset + dragAmount * resistanceFactor

                                            // Clamp dragOffset: Prevent significant upward drag, allow downward drag
                                            val maxDownwardDrag = if (screenHeight > 0) screenHeight * 0.3f else 200f // Allow dragging down ~30%
                                            dragOffset = newOffset.coerceIn(-30f, maxDownwardDrag) // Clamp between -30f (up) and maxDownwardDrag (down)
                                        }
                                    )
                                }
                        ) {
                            // Header Content (Handle, Title) - No structural changes needed
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
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

                        // Divider - No structural changes needed
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Main Content Area (LazyColumn)
                        Box(
                            modifier = Modifier
                                .weight(1f) // Takes remaining space
                                .fillMaxWidth()
                        ) {
                            // Conditional content based on loading/error/data state
                            when {
                                // Loading State
                                isLoading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                                // Error State
                                errorMessage != null -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        // ... (Error Column with Text and Retry Button - unchanged) ...
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
                                // Data Loaded State
                                profileData != null -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Profile Header Item
                                        item {
                                            ProfileHeader(
                                                profileData?.studentName ?: "N/A",
                                                profileData?.studentID ?: "N/A"
                                            )
                                        }
                                        // Personal Info Section Item
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
                                        // Academic Info Section Item
                                        item {
                                            ProfileSection(
                                                title = "Academic Information",
                                                icon = Icons.Filled.School,
                                                content = {
                                                    ProfileRow("Degree", profileData?.degree ?: "N/A")
                                                    ProfileRow("Branch", profileData?.branchName ?: "N/A")
                                                    if (profileData?.specialization?.uppercase() != profileData?.branchName?.uppercase() &&
                                                        profileData?.specialization?.isNotBlank() == true
                                                    ) {
                                                        ProfileRow("Specialization", profileData?.specialization ?: "N/A")
                                                    }
                                                    ProfileRow("Section", profileData?.section ?: "N/A")
                                                }
                                            )
                                        }
                                        // Contact Us Button Item
                                        item {
                                            Button(
                                                onClick = { /* ... (Intent logic unchanged) ... */
                                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                                    val url = "https://nsutrack.systems"
                                                    val intent = Intent(Intent.ACTION_VIEW)
                                                    intent.data = url.toUri()
                                                    try { context.startActivity(intent) } catch (e: Exception) { Log.e("AccountView", "Could not launch URL $url", e) }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 32.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = null
                                            ) {
                                                Text("Contact Us", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        // Logout Button Item
                                        item {
                                            Button(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                    // Trigger dismiss FIRST, then logout
                                                    dismissSheet()
                                                    coroutineScope.launch {
                                                        // Delay slightly longer than dismiss animation
                                                        delay(350)
                                                        viewModel.logout()
                                                        onLogout() // Call logout callback after dismiss animation finishes
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 32.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = null
                                            ) {
                                                Text("Logout", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                                            }
                                            Spacer(modifier = Modifier.height(16.dp)) // Space after last button
                                        }
                                    } // End LazyColumn
                                }
                                // Fallback for null data
                                else -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No profile data available.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            } // End When
                        } // End Content Box
                    } // End Main Column
                } // End Surface
            } // End AnimatedVisibility
        } // End Root Box
    } // End Dialog
} // End AccountView Composable

// --- Helper Composables (ProfileHeader, ProfileSection, ProfileRow) ---
// These remain unchanged from your provided code. Ensure they are present below.
@Composable
fun ProfileHeader(name: String, studentId: String) { /* ... (Your existing code) ... */
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
    content: @Composable ColumnScope.() -> Unit
) { /* ... (Your existing code) ... */
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
fun ProfileRow(label: String, value: String) { /* ... (Your existing code) ... */
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
// These remain unchanged from your provided code. Ensure they are present below.
fun formatProfileValue(label: String, value: String): String { /* ... (Your existing code) ... */
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
fun String.capitalizeWord(): String { /* ... (Your existing code) ... */
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}