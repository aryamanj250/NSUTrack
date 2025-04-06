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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.animation.core.EaseOutQuint


/**
 * Google-styled Profile View with simplified animations
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
            delay(200)
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
            dismissOnClickOutside = false,
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
            // Scrim animation - Google uses a more subtle scrim
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
                                delay(150)
                                onDismiss()
                            }
                        }
                    }
            )

            // Main sheet with Google-style animation (faster, cleaner)
            // Main sheet with Google-style animation
            AnimatedVisibility(
                visible = visible.targetState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 300f,
                        visibilityThreshold = null
                    )
                ) + fadeIn(
                    animationSpec = tween(150)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(150, easing = standardEasing)
                ) + fadeOut(tween(120))
            ){
                // Get exact screen height
                var screenHeight by remember { mutableStateOf(0) }

                // Sheet height with precise calculation - Google uses standard fractions
                val sheetHeightFraction by animateFloatAsState(
                    targetValue = if (sheetState == EXPANDED) 0.90f else if (sheetState == HALF_EXPANDED) 0.75f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.8f, // Less bouncy, more Google-like
                        stiffness = 400f,
                        visibilityThreshold = 0.001f
                    ),
                    label = "SheetHeight"
                )

                // Calculate height in pixels
                val sheetHeightPx = if (screenHeight > 0) {
                    (screenHeight * sheetHeightFraction).roundToInt()
                } else {
                    0
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (sheetHeightPx.coerceAtMost((screenHeight * 0.90f).toInt())).toDp() })
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .onSizeChanged {
                            // Store screen height
                            if (screenHeight == 0) {
                                screenHeight = view.height
                            }
                        }
                        .graphicsLayer {
                            this.shadowElevation = 4f // Google uses consistent elevation
                            this.shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp // Google uses consistent corner radius
                            )
                            clip = true
                        },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Draggable header in Google style - simpler
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

                                            coroutineScope.launch {
                                                val currentVelocity = velocityTracker
                                                val velocityThreshold = 250f
                                                val offsetThreshold = 30f

                                                when {
                                                    // Fast downward fling when expanded
                                                    currentVelocity > velocityThreshold && sheetState == EXPANDED -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                                        sheetState = HALF_EXPANDED
                                                    }

                                                    // Fast downward fling when half expanded
                                                    currentVelocity > velocityThreshold * 1.5f && sheetState == HALF_EXPANDED -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                        sheetState = HIDDEN
                                                        delay(150)
                                                        visible.targetState = false
                                                        delay(150)
                                                        onDismiss()
                                                    }

                                                    // Fast upward fling when half expanded
                                                    currentVelocity < -velocityThreshold && sheetState == HALF_EXPANDED -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                                        sheetState = EXPANDED
                                                    }

                                                    // Position-based decisions
                                                    dragOffset > offsetThreshold && sheetState == EXPANDED -> {
                                                        sheetState = HALF_EXPANDED
                                                    }
                                                    dragOffset < -offsetThreshold && sheetState == HALF_EXPANDED -> {
                                                        sheetState = EXPANDED
                                                    }
                                                    dragOffset > offsetThreshold * 2 && sheetState == HALF_EXPANDED -> {
                                                        sheetState = HIDDEN
                                                        delay(150)
                                                        visible.targetState = false
                                                        delay(150)
                                                        onDismiss()
                                                    }

                                                    else -> {
                                                        val springSpec = spring<Float>(
                                                            dampingRatio = 0.8f, // Less bouncy
                                                            stiffness = 500f
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

                                                velocityTracker = 0f
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            coroutineScope.launch {
                                                animate(
                                                    initialValue = dragOffset,
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.8f,
                                                        stiffness = 500f
                                                    )
                                                ) { value, _ ->
                                                    dragOffset = value
                                                }
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()

                                            val baseResistance = 0.5f

                                            val stateResistance = when (sheetState) {
                                                EXPANDED -> if (dragAmount > 0) 1.0f else 0.0f
                                                HALF_EXPANDED -> if (dragAmount < 0) 0.9f else 0.8f
                                                else -> 0f
                                            }

                                            val progressiveFactor = 1.0f - (abs(dragOffset) / 300f).coerceIn(0f, 0.5f)

                                            velocityTracker = 0.75f * velocityTracker + 0.25f * dragAmount * 16f

                                            val effectiveResistance = baseResistance * stateResistance * progressiveFactor
                                            dragOffset += dragAmount * effectiveResistance

                                            dragOffset = dragOffset.coerceIn(-50f, 150f)
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
                                // Handle - Google style (thinner, less animated)
                                Box(
                                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.4f
                                                )
                                            )
                                    )
                                }

                                // Title - Google style (simpler)
                                Text(
                                    text = "Profile",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium, // Google uses medium weight
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp, vertical = 12.dp)
                                )
                            }
                        }

                        // Divider - Google style (thinner)
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 0.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Content area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when {
                                isLoading -> {
                                    // Loading state - Google style
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }

                                errorMessage != null -> {
                                    // Error state - Google style
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
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = errorMessage ?: "",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(24.dp))

                                            // Google-style button
                                            Button(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedback.FeedbackType.MEDIUM
                                                    )
                                                    viewModel.fetchProfileData()
                                                },
                                                shape = RoundedCornerShape(8.dp) // Google uses less rounded corners
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
                                    // Profile data - Google style layout
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 100.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    // Empty state - Google style
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
        // Avatar in Google style - simpler animation
        var isLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(100)
            isLoaded = true
        }

        val avatarScale by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0.9f, // Less dramatic scale
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "AvatarScale"
        )

        val avatarAlpha by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0f,
            animationSpec = tween(200), // Faster fade in
            label = "AvatarAlpha"
        )

        Surface(
            modifier = Modifier
                .size(100.dp) // Google uses slightly smaller avatars
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                    alpha = avatarAlpha
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 0.dp // Google uses less shadows
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Medium, // Google uses medium weight
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text animations - Google style (faster, simpler)
        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(animationSpec = tween(200)) +
                    slideInVertically(
                        initialOffsetY = { -10 }, // Less dramatic slide
                        animationSpec = tween(250, easing = EaseOutQuad)
                    )
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium, // Google uses medium weight
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        AnimatedVisibility(
            visible = isLoaded,
            enter = fadeIn(animationSpec = tween(250, delayMillis = 50)) +
                    expandVertically(
                        animationSpec = tween(250, delayMillis = 50, easing = EaseOutQuad)
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
                    dampingRatio = 0.8f, // Less bouncy
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        // Section header - Google style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp), // Smaller icon container
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp) // Smaller icon
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium, // Google uses medium weight
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Section content - Google style cards (less rounded, less elevation)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp), // Google uses less rounded corners
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 1.dp // Google uses less elevation
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
    // Google-style staggered animation (faster, less dramatic)
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50) // Shorter delay for faster appearance
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(200)) +
                slideInHorizontally(
                    initialOffsetX = { -10 }, // Less dramatic slide
                    animationSpec = tween(200, easing = EaseOutQuad)
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
                // Label - Google style
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.35f)
                )

                // Value - Google style
                Text(
                    text = formatProfileValue(label, value),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal, // Google uses normal weight
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(0.65f)
                        .padding(start = 8.dp)
                )
            }

            // Add divider if not the last element (thinner divider for Google style)
            if (label != "Section" && label != "Admission" && label != "Mode" &&
                label != "Gender" && label != "Category" && label != "Specialization") {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 0.dp),
                    thickness = (1.5).dp, // Google uses thicker divider0.5dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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