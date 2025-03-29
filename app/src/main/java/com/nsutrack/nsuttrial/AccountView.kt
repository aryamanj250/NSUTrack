package com.nsutrack.nsuttrial

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Optimized Profile View with faster animations and 90% max height
 */
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

    // Animation state for fast entry/exit
    val visible = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    // Sheet states with clear naming
    val EXPANDED = 0
    val HALF_EXPANDED = 1
    val HIDDEN = 2

    // Handle back button press with faster animation
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
        coroutineScope.launch {
            visible.targetState = false
            delay(200)  // Faster exit
            onDismiss()
        }
    }

    // Fast animation curves
    val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val standardEasing = FastOutSlowInEasing

    // Dialog with optimized properties
    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                visible.targetState = false
                delay(200) // Faster exit
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        // Track sheet state with fast transitions
        var sheetState by remember { mutableStateOf(HALF_EXPANDED) }
        var isDragging by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf(0f) }
        var velocityTracker by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Fast scrim animation
            val scrimAlpha by animateFloatAsState(
                targetValue = when (sheetState) {
                    EXPANDED -> 0.65f
                    HALF_EXPANDED -> 0.5f
                    else -> 0f
                },
                animationSpec = tween(
                    durationMillis = 200, // Faster fade
                    easing = standardEasing
                ),
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
                                delay(100) // Faster transitions
                                visible.targetState = false
                                delay(150)
                                onDismiss()
                            }
                        }
                    }
            )

            // Fast sheet animation
            AnimatedVisibility(
                visible = visible.targetState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 250, // Faster entry
                        easing = emphasizedEasing
                    )
                ) + fadeIn(
                    initialAlpha = 0f,
                    animationSpec = tween(
                        durationMillis = 200, // Faster fade in
                        easing = standardEasing
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 180, // Faster exit
                        easing = standardEasing
                    )
                ) + fadeOut(
                    targetAlpha = 0f,
                    animationSpec = tween(
                        durationMillis = 150 // Faster fade out
                    )
                )
            ) {
                // MAX 90% height sheet
                val sheetHeight by animateFloatAsState(
                    targetValue = when (sheetState) {
                        EXPANDED -> 0.9f
                        HALF_EXPANDED -> 0.8f
                        else -> 0f
                    }.toFloat(), // Add explicit conversion to Float
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 400f,
                        visibilityThreshold = 0.001f
                    ),
                    label = "SheetHeight"
                )

                // Fast elevation animation
                val sheetElevation by animateFloatAsState(
                    targetValue = when (sheetState) {
                        EXPANDED -> 8f
                        HALF_EXPANDED -> 4f
                        else -> 0f
                    },
                    animationSpec = tween(
                        durationMillis = 150, // Faster animation
                        easing = standardEasing
                    ),
                    label = "SheetElevation"
                )

                // Fast corner radius animation
                val cornerRadius by animateFloatAsState(
                    targetValue = when (sheetState) {
                        EXPANDED -> 16f
                        HALF_EXPANDED -> 28f
                        else -> 28f
                    },
                    animationSpec = tween(
                        durationMillis = 200, // Faster animation
                        easing = standardEasing
                    ),
                    label = "CornerRadius"
                )

                // Sheet surface
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(sheetHeight)
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .graphicsLayer {
                            this.shadowElevation = sheetElevation
                            this.shape = RoundedCornerShape(
                                topStart = cornerRadius.dp,
                                topEnd = cornerRadius.dp
                            )
                            clip = true
                        },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Fast drag handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isDragging = true
                                        },
                                        onDragEnd = {
                                            isDragging = false

                                            // Fast snap behavior
                                            coroutineScope.launch {
                                                val currentVelocity = velocityTracker
                                                val velocityThreshold = 250f // Lower threshold for faster response
                                                val offsetThreshold = 30f // Lower threshold for faster response

                                                // Determine target state quickly
                                                when {
                                                    // Fast downward fling when expanded
                                                    currentVelocity > velocityThreshold && sheetState == EXPANDED -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                                        sheetState = HALF_EXPANDED
                                                    }

                                                    // Fast downward fling when half expanded (dismiss)
                                                    currentVelocity > velocityThreshold * 1.5f && sheetState == HALF_EXPANDED -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                        sheetState = HIDDEN
                                                        delay(150) // Faster transitions
                                                        visible.targetState = false
                                                        delay(150)
                                                        onDismiss()
                                                    }

                                                    // Fast upward fling when half expanded
                                                    currentVelocity < -velocityThreshold && sheetState == HALF_EXPANDED -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                                        sheetState = EXPANDED
                                                    }

                                                    // Quick position-based decisions
                                                    dragOffset > offsetThreshold && sheetState == EXPANDED -> {
                                                        sheetState = HALF_EXPANDED
                                                    }
                                                    dragOffset < -offsetThreshold && sheetState == HALF_EXPANDED -> {
                                                        sheetState = EXPANDED
                                                    }
                                                    dragOffset > offsetThreshold * 2 && sheetState == HALF_EXPANDED -> {
                                                        sheetState = HIDDEN
                                                        delay(150) // Faster transitions
                                                        visible.targetState = false
                                                        delay(150)
                                                        onDismiss()
                                                    }

                                                    // Fast return to current state
                                                    else -> {
                                                        val springSpec = spring<Float>(
                                                            dampingRatio = 0.7f, // Less bouncy
                                                            stiffness = 500f // Stiffer spring for faster movement
                                                        )
                                                        animate(
                                                            initialValue = dragOffset,
                                                            targetValue = 0f,
                                                            animationSpec = springSpec
                                                        ) { value, _ ->
                                                            dragOffset = value
                                                        }
                                                    }
                                                }

                                                // Reset velocity tracker
                                                velocityTracker = 0f
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            coroutineScope.launch {
                                                // Fast animation to neutral
                                                animate(
                                                    initialValue = dragOffset,
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.7f,
                                                        stiffness = 500f
                                                    )
                                                ) { value, _ ->
                                                    dragOffset = value
                                                }
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()

                                            // Quick resistance calculation
                                            val baseResistance = 0.5f

                                            // State-specific resistance
                                            val stateResistance = when (sheetState) {
                                                EXPANDED -> {
                                                    if (dragAmount > 0) 1.0f else 0.3f // Allow down, resist up strongly
                                                }
                                                HALF_EXPANDED -> {
                                                    if (dragAmount < 0) 0.9f else 0.8f // Balanced resistance
                                                }
                                                else -> 0f // No dragging when hidden
                                            }

                                            // Progressive resistance
                                            val progressiveFactor = 1.0f - (abs(dragOffset) / 300f).coerceIn(0f, 0.5f)

                                            // Fast velocity tracking
                                            velocityTracker = 0.75f * velocityTracker + 0.25f * dragAmount * 16f

                                            // Apply resistance
                                            val effectiveResistance = baseResistance * stateResistance * progressiveFactor
                                            dragOffset += dragAmount * effectiveResistance

                                            // Hard limits
                                            dragOffset = dragOffset.coerceIn(-80f, 200f)
                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Fast handle animation
                                Box(
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Fast handle properties
                                    val handleWidth by animateFloatAsState(
                                        targetValue = if (isDragging) 48f else 36f,
                                        animationSpec = tween(
                                            durationMillis = 100, // Faster animation
                                            easing = LinearOutSlowInEasing
                                        ),
                                        label = "HandleWidth"
                                    )

                                    val handleOpacity by animateFloatAsState(
                                        targetValue = if (isDragging) 0.8f else 0.4f,
                                        animationSpec = tween(120), // Faster animation
                                        label = "HandleOpacity"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .width(handleWidth.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = handleOpacity
                                                )
                                            )
                                    )
                                }

                                // Title with fast animation
                                val titleScale by animateFloatAsState(
                                    targetValue = if (sheetState == EXPANDED) 1f else 0.98f,
                                    animationSpec = tween(150), // Faster animation
                                    label = "TitleScale"
                                )

                                Text(
                                    text = "Profile",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp, vertical = 12.dp)
                                        .graphicsLayer {
                                            scaleX = titleScale
                                            scaleY = titleScale
                                        }
                                )
                            }
                        }

                        // Fast divider animation
                        val dividerAlpha by animateFloatAsState(
                            targetValue = if (isDragging) 0.7f else 0.25f,
                            animationSpec = tween(100), // Faster animation
                            label = "DividerAlpha"
                        )

                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (sheetState == EXPANDED) 0.dp else 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = dividerAlpha),
                            thickness = 0.5.dp
                        )

                        // Content area with fixed bottom padding
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 16.dp) // Consistent bottom padding
                        ) {
                            when {
                                isLoading -> {
                                    // Loading state with fast animation
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val loadingAnimation = rememberInfiniteTransition(label = "LoadingAnimation")
                                        val loadingScale by loadingAnimation.animateFloat(
                                            initialValue = 1f,
                                            targetValue = 1.1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(800), // Faster animation
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "LoadingScale"
                                        )

                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .scale(loadingScale),
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
                                    // Profile data with improved LazyColumn
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 20.dp,
                                            end = 20.dp,
                                            top = 8.dp,
                                            bottom = 40.dp // Extra bottom padding to prevent empty space
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
                                    // Empty state
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
        // Profile avatar with fast enter animation
        var isLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(100)
            isLoaded = true
        }

        val avatarScale by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium // Higher stiffness for faster animation
            ),
            label = "AvatarScale"
        )

        val avatarAlpha by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0f,
            animationSpec = tween(250), // Faster fade in
            label = "AvatarAlpha"
        )

        Surface(
            modifier = Modifier
                .size(110.dp)
                .scale(avatarScale)
                .graphicsLayer {
                    alpha = avatarAlpha
                },
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

        // Fast text animations
        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(animationSpec = tween(300)) + // Faster fade in
                    slideInVertically(
                        initialOffsetY = { -20 },
                        animationSpec = tween(300, easing = EaseOutQuad) // Faster entry
                    )
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(animationSpec = tween(350, delayMillis = 80)) + // Faster fade with shorter delay
                    expandVertically(
                        animationSpec = tween(300, delayMillis = 80, easing = EaseOutQuad) // Faster expansion
                    )
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
                    stiffness = Spring.StiffnessMedium // Higher stiffness for faster animation
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
    // Fast staggered animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80) // Shorter delay for faster appearance
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(250)) + // Faster fade in
                slideInHorizontally(
                    initialOffsetX = { -20 },
                    animationSpec = tween(250, easing = EaseOutQuad) // Faster slide in
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
                // Label
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.35f)
                )

                // Value
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

// Format profile values appropriately
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

// Extension functions for string capitalization
fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}