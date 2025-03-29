package com.nsutrack.nsuttrial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nsutrack.nsuttrial.ui.util.clickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.draw.shadow
import kotlin.math.abs


// Define custom easing curves
// Custom easing curve similar to Material Design's standard curve
private val CustomEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
private val EaseInQuart = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountView(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val profileData by viewModel.profileData.collectAsState()
    val isLoading by viewModel.isProfileLoading.collectAsState()
    val errorMessage by viewModel.profileError.collectAsState()
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val coroutineScope = rememberCoroutineScope()

    // Animation state for fullscreen entry/exit
    val visible = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    // Handle back button press with animation
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
        coroutineScope.launch {
            visible.targetState = false
            delay(300)  // Wait for exit animation to complete
            onDismiss()
        }
    }

    // Use Dialog to ensure full screen display
    // Replace the Dialog and AnimatedVisibility section with this code
    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                visible.targetState = false
                delay(300)
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Semi-transparent background
            AnimatedVisibility(
                visible = visible.targetState,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                            coroutineScope.launch {
                                visible.targetState = false
                                delay(300)
                                onDismiss()
                            }
                        }
                )
            }

            // Card content with animation
            var isExpanded by remember { mutableStateOf(false) }
            var offsetY by remember { mutableStateOf(0f) }
            var lastDragAmount by remember { mutableStateOf(0f) }
            val dragThreshold = 80f

            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(350)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .fillMaxHeight(if (isExpanded) 1f else 0.85f)
                        .offset { IntOffset(0, offsetY.roundToInt()) }
                        .shadow(
                            elevation = if (isExpanded) 16.dp else 8.dp,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isExpanded) 6.dp else 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header area (pill handle + title) - make this entire area draggable
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            // Determine if we should expand or collapse
                                            if (offsetY < -dragThreshold && !isExpanded) {
                                                isExpanded = true
                                                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                            } else if (offsetY > dragThreshold && isExpanded) {
                                                isExpanded = false
                                                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                            }

                                            // Animate back to position
                                            coroutineScope.launch {
                                                animate(
                                                    initialValue = offsetY,
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                ) { value, _ ->
                                                    offsetY = value
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            coroutineScope.launch {
                                                animate(
                                                    initialValue = offsetY,
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                ) { value, _ ->
                                                    offsetY = value
                                                }
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            lastDragAmount = dragAmount

                                            // Calculate resistance based on expansion state and direction
                                            val resistanceFactor = when {
                                                isExpanded && dragAmount > 0 -> 0.6f // Pulling down when expanded
                                                !isExpanded && dragAmount < 0 -> 0.7f // Pulling up when collapsed
                                                isExpanded && dragAmount < 0 -> 0.2f // Pulling up when already expanded
                                                !isExpanded && dragAmount > 0 -> 0.2f // Pulling down when already collapsed
                                                else -> 0.5f
                                            }

                                            offsetY += dragAmount * resistanceFactor

                                            // Apply limits to offset
                                            offsetY = offsetY.coerceIn(-120f, 120f)
                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Pill handle at the top
                                Box(
                                    modifier = Modifier
                                        .padding(top = 12.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Animated pill width
                                    val pillWidth by animateFloatAsState(
                                        targetValue = if (abs(offsetY) > 20f) 48f else 36f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "PillWidth"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .width(pillWidth.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = if (isExpanded) 0.3f else 0.4f
                                                )
                                            )
                                    )
                                }

                                // Profile title
                                val titlePadding by animateFloatAsState(
                                    targetValue = if (isExpanded) 12f else 16f,
                                    label = "TitlePadding"
                                )

                                Text(
                                    text = "Profile",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp, vertical = titlePadding.dp)
                                )
                            }
                        }

                        // Divider
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isExpanded) 0.dp else 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = if (isExpanded) 0.2f else 0.3f
                            ),
                            thickness = 0.5.dp
                        )

                        // Content area - normal scrolling here
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    bottom = if (isExpanded) 0.dp else 8.dp
                                )
                        ) {
                            when {
                                isLoading -> {
                                    // Loading state
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp),
                                            strokeWidth = 4.dp
                                        )
                                    }
                                }

                                errorMessage != null -> {
                                    // Error state
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Text(
                                                text = "Error loading profile data",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = errorMessage ?: "",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(24.dp))

                                            Button(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedback.FeedbackType.MEDIUM
                                                    )
                                                    viewModel.fetchProfileData()
                                                },
                                                shape = RoundedCornerShape(24.dp)
                                            ) {
                                                Text(
                                                    "Retry",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                }

                                profileData != null -> {
                                    // Profile data with enhanced UI
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 20.dp,
                                            end = 20.dp,
                                            top = 8.dp,
                                            bottom = 32.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(24.dp)
                                    ) {
                                        item {
                                            ProfileHeader(
                                                profileData?.studentName ?: "",
                                                profileData?.studentID ?: ""
                                            )
                                        }

                                        item {
                                            ProfileSection(
                                                title = "Personal Information",
                                                icon = Icons.Filled.Person,
                                                content = {
                                                    ProfileRow(
                                                        label = "Name",
                                                        value = profileData?.studentName ?: ""
                                                    )
                                                    ProfileRow(
                                                        label = "Student ID",
                                                        value = profileData?.studentID ?: ""
                                                    )
                                                    ProfileRow(
                                                        label = "Date of birth",
                                                        value = profileData?.dob ?: ""
                                                    )
                                                    ProfileRow(
                                                        label = "Gender",
                                                        value = profileData?.gender ?: ""
                                                    )
                                                }
                                            )
                                        }

                                        item {
                                            ProfileSection(
                                                title = "Academic Information",
                                                icon = Icons.Filled.School,
                                                content = {
                                                    ProfileRow(
                                                        label = "Degree",
                                                        value = profileData?.degree ?: ""
                                                    )
                                                    ProfileRow(
                                                        label = "Branch",
                                                        value = profileData?.branchName ?: ""
                                                    )

                                                    if (profileData?.specialization?.uppercase() != profileData?.branchName?.uppercase() &&
                                                        profileData?.specialization?.isNotBlank() == true
                                                    ) {
                                                        ProfileRow(
                                                            label = "Specialization",
                                                            value = profileData?.specialization
                                                                ?: ""
                                                        )
                                                    }

                                                    ProfileRow(
                                                        label = "Section",
                                                        value = profileData?.section ?: ""
                                                    )

                                                    if (profileData?.ftpt?.isNotBlank() == true) {
                                                        ProfileRow(
                                                            label = "Mode",
                                                            value = profileData?.ftpt ?: ""
                                                        )
                                                    }

                                                    if (profileData?.admission?.isNotBlank() == true) {
                                                        ProfileRow(
                                                            label = "Admission",
                                                            value = profileData?.admission ?: ""
                                                        )
                                                    }
                                                }
                                            )
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
@Composable
fun ProfileHeader(name: String, studentId: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile avatar with animated entry
        var isLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(100)
            isLoaded = true
        }

        val scale by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "Avatar Scale"
        )

        Surface(
            modifier = Modifier
                .size(110.dp)
                .scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(animationSpec = tween(500)) +
                    expandVertically(animationSpec = tween(500))
        ) {
            Text(
                text = studentId,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Section content
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    vertical = 8.dp,
                    horizontal = 0.dp
                )
            ) {
                content()
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    // Staggered animation for list items
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100) // Small delay before starting animation
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInHorizontally(
                    initialOffsetX = { -20 },
                    animationSpec = tween(300)
                )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label on the left side
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.35f)
                )

                // Value on the right side with right alignment
                Text(
                    text = formatProfileValue(label, value),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(0.65f)
                        .padding(start = 8.dp)
                )
            }

            // Add divider if not the last element
            if (label != "Section" && label != "Admission" && label != "Mode" &&
                label != "Gender" && label != "Category" && label != "Specialization") {
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 0.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

// Format profile values appropriately - keeping existing functionality
fun formatProfileValue(label: String, value: String): String {
    return when (label) {
        "Student ID" -> value  // Keep student ID as is
        "Specialization" -> {
            if (value.uppercase().contains("VLSI")) {
                // Handle VLSI case (keep VLSI in uppercase)
                value.split(" ").joinToString(" ") { word ->
                    if (word.uppercase() == "VLSI") "VLSI" else word.lowercase().capitalize()
                }
            } else {
                value.lowercase().capitalize()
            }
        }
        "Mode" -> {
            when (value.uppercase()) {
                "FT" -> "Full Time"
                "PT" -> "Part Time"
                else -> value.lowercase().capitalize()
            }
        }
        else -> value.lowercase().capitalize()  // Capitalize other values
    }
}

// Extension functions for string capitalization - keeping existing functionality
fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}