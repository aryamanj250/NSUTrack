package com.nsutrack.nsuttrial

import android.R.attr.label
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourname.nsutrack.data.model.AttendanceRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bottom sheet implementation for attendance details with fast, smooth animations
 * Optimized for 90% max height and faster transitions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAttendanceView(
    subject: SubjectData,
    onDismiss: () -> Unit
) {
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val records = subject.records
    val coroutineScope = rememberCoroutineScope()

    // Get current date to filter past records
    val currentDate = remember { Date() }
    val dateFormat = remember { SimpleDateFormat("MMM-dd", Locale.getDefault()) }
    val currentDateString = remember { dateFormat.format(currentDate) }

    // Parse month and day for comparison
    val currentMonth = currentDateString.split("-")[0]
    val currentDay = currentDateString.split("-")[1].toIntOrNull() ?: 0

    // Filter records to show only past dates with actual classes
    val filteredRecords = records.filter { record ->
        // First filter dates
        val dateParts = record.date.split("-")
        if (dateParts.size < 2) return@filter false // Invalid date format

        val month = dateParts[0]
        val day = dateParts[1].toIntOrNull() ?: 0

        // Check date is in past
        val isPastDate = if (month != currentMonth) {
            true // Different month than current is fine
        } else {
            day <= currentDay // For current month, only show up to current day
        }

        // Check status - only include status that indicates class actually happened
        val isActualClass = when (record.status) {
            "0", "1", // Absent or Present
            "0+0", "0+1", "1+0", "1+1" -> true // Combined statuses for multiple periods
            else -> false // GH, H, CS, TL, MS, CR, etc. are not actual classes
        }

        return@filter isPastDate && isActualClass
    }

    // Group attendance records by month
    val groupedRecords = filteredRecords.groupBy {
        it.date.split("-").firstOrNull() ?: ""
    }.toList()

    // Sort groups by month (assuming format is "MMM-DD")
    val sortedGroups = groupedRecords.sortedByDescending { it.first }

    // Keep track of expanded month sections
    val expandedMonths = remember { mutableStateOf(setOf(sortedGroups.firstOrNull()?.first ?: "")) }

    // Bottom sheet state using constants instead of enum
    val EXPANDED = 0
    val HALF_EXPANDED = 1
    val HIDDEN = 2

    // Animation state for smooth entry/exit
    val visible = remember { MutableTransitionState(false) }

    // Sheet state controller
    var sheetState by remember { mutableStateOf(HALF_EXPANDED) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var velocityTracker by remember { mutableStateOf(0f) }

    // Standard Material animation curves with faster timing
    val standardEasing = FastOutSlowInEasing
    val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    LaunchedEffect(Unit) {
        delay(50)  // Short delay to ensure UI is ready
        visible.targetState = true
    }

    // Handle back button press with animation
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
        coroutineScope.launch {
            visible.targetState = false
            delay(200)  // Reduced delay time for faster dismissal
            onDismiss()
        }
    }

    // Full screen dialog with optimized animations
    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                visible.targetState = false
                delay(200) // Faster dismissal animation
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim with faster fade animation
            val scrimAlpha by animateFloatAsState(
                targetValue = when (sheetState) {
                    EXPANDED -> 0.65f
                    HALF_EXPANDED -> 0.5f
                    else -> 0f
                },
                animationSpec = tween(
                    durationMillis = 200, // Faster animation
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

            // Main sheet with optimized, faster animation
            AnimatedVisibility(
                visible = visible.targetState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 250, // Faster entry animation
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
                        durationMillis = 180, // Faster exit animation
                        easing = standardEasing
                    )
                ) + fadeOut(
                    targetAlpha = 0f,
                    animationSpec = tween(
                        durationMillis = 150 // Faster fade out
                    )
                )
            ) {
                // Sheet height animation with physics-based spring - MAX 90% HEIGHT
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

                // Elevation animation
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

                // Corner radius with faster animation
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

                // Main sheet surface
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
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Draggable header with faster interactions
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

                                                // Determine target state based on velocity and position
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

                                                    // Position-based decisions
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

                                                    // Return to current state
                                                    else -> {
                                                        // Fast animation to return to neutral position
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

                                            // Resistance calculation
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

                                            // Track velocity
                                            velocityTracker = 0.75f * velocityTracker + 0.25f * dragAmount * 16f

                                            // Apply resistance
                                            val effectiveResistance = baseResistance * stateResistance * progressiveFactor
                                            dragOffset += dragAmount * effectiveResistance

                                            // Constraints
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
                                // Drag handle with faster animation
                                Box(
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Handle properties with faster animation
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

                                // Title with faster animation
                                val titleScale by animateFloatAsState(
                                    targetValue = if (sheetState == EXPANDED) 1f else 0.98f,
                                    animationSpec = tween(150), // Faster animation
                                    label = "TitleScale"
                                )

                                Text(
                                    text = subject.name,
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

                        // Divider with faster animation
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

                        // Content area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (sheetState == EXPANDED) 16.dp else 16.dp)
                        ) {
                            when {
                                filteredRecords.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No attendance records available",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }

                                else -> {
                                    // Attendance data with optimized LazyColumn
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 40.dp // Extra bottom padding to prevent empty space
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        item {
                                            AttendanceHeader(subject)
                                        }

                                        items(
                                            items = sortedGroups,
                                            key = { it.first } // Use month as stable key
                                        ) { (month, monthRecords) ->
                                            MonthSection(
                                                month = month,
                                                records = monthRecords.sortedByDescending {
                                                    it.date.split("-").getOrNull(1)?.toIntOrNull() ?: 0
                                                },
                                                isExpanded = expandedMonths.value.contains(month),
                                                onToggle = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedback.FeedbackType.LIGHT
                                                    )
                                                    expandedMonths.value =
                                                        if (expandedMonths.value.contains(month)) {
                                                            expandedMonths.value - month
                                                        } else {
                                                            expandedMonths.value + month
                                                        }
                                                }
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
}

@Composable
fun AttendanceHeader(subject: SubjectData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${subject.overallClasses}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${subject.overallPresent}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4CAF50)
                )
                Text(
                    text = "Present",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${subject.overallAbsent}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE57373)
                )
                Text(
                    text = "Absent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Attendance advice card with contextual colors
        val targetPercentage = 75.0
        val currentPercentage = subject.attendancePercentage

        val adviceText = if (currentPercentage >= targetPercentage) {
            val classesCanSkip =
                ((subject.overallPresent * 100 / targetPercentage) - subject.overallClasses).toInt()
            "You can skip next $classesCanSkip classes"
        } else {
            val classesNeeded =
                Math.ceil(((targetPercentage * subject.overallClasses - 100 * subject.overallPresent) / (100 - targetPercentage)))
                    .toInt()
            "You need to attend next $classesNeeded classes"
        }

        // Card colors based on status
        val bgColor = if (currentPercentage >= targetPercentage) {
            Color(0xFFE8F5E9) // Light Green
        } else {
            Color(0xFFFFEBEE) // Light Red
        }

        val textColor = if (currentPercentage >= targetPercentage) {
            Color(0xFF2E7D32) // Dark Green
        } else {
            Color(0xFFD32F2F) // Dark Red
        }

        val borderColor = if (currentPercentage >= targetPercentage) {
            Color(0xFF81C784) // Green
        } else {
            Color(0xFFEF5350) // Red
        }

        // Advice card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = bgColor,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = borderColor
            )
        ) {
            Text(
                text = adviceText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
                color = textColor,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// MonthSection component
@Composable
fun MonthSection(
    month: String,
    records: List<AttendanceRecord>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Month header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = month,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${records.size} CLASSES",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Rotation animation for arrow
                    val rotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = tween(
                            durationMillis = 200, // Faster rotation
                            easing = FastOutSlowInEasing
                        ),
                        label = "ArrowRotation"
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Records with faster expand/collapse animation
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(
                    durationMillis = 200, // Faster expand
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = LinearOutSlowInEasing
                )
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(
                    durationMillis = 150, // Faster collapse
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(120) // Faster fade out
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort records by day in descending order (latest first)
                val sortedRecords = records.sortedByDescending {
                    it.date.split("-").getOrNull(1)?.toIntOrNull() ?: 0
                }

                // Group records by type for organized display
                val normalRecords = sortedRecords.filter {
                    it.status == "0" || it.status == "1" ||
                            it.status == "0+0" || it.status == "1+1" ||
                            it.status == "0+1" || it.status == "1+0"
                }

                val holidayRecords = sortedRecords.filter {
                    it.status == "GH" || it.status == "H" || it.status == "CS" || it.status == "TL"
                }

                val specialRecords = sortedRecords.filter {
                    it.status == "MS" || it.status == "CR" ||
                            (!normalRecords.contains(it) && !holidayRecords.contains(it))
                }

                // Display normal classes first (present/absent)
                if (normalRecords.isNotEmpty()) {
                    normalRecords.forEach { record ->
                        RecordRow(record)
                    }
                }

                // Then display special cases like mid-sem exams
                if (specialRecords.isNotEmpty()) {
                    Text(
                        text = "Special Sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    specialRecords.forEach { record ->
                        RecordRow(record)
                    }
                }

// Finally display holidays
// Finally display holidays
                if (holidayRecords.isNotEmpty()) {
                    Text(
                        text = "Holidays & Cancellations",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    holidayRecords.forEach { record ->
                        RecordRow(record)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordRow(record: AttendanceRecord) {
    // Format the day better
    val day = record.date.split("-").getOrNull(1)?.toIntOrNull() ?: 0

    // Get month for context
    val month = record.date.split("-").getOrNull(0) ?: ""

    // Format date properly
    val monthNum = when(month) {
        "Jan" -> 1
        "Feb" -> 2
        "Mar" -> 3
        "Apr" -> 4
        "May" -> 5
        "Jun" -> 6
        "Jul" -> 7
        "Aug" -> 8
        "Sep" -> 9
        "Oct" -> 10
        "Nov" -> 11
        "Dec" -> 12
        else -> 1
    }

    // Create date string in proper format
    val dateString = "$day $month"

    // Card with subtle elevation
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show day with better formatting
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Format the status with styling
            AttendanceStatusView(status = record.status)
        }
    }
}

@Composable
fun AttendanceStatusView(status: String) {
    when (status) {
        // Single status codes
        "0" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFEBEE), // Light Red
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Absent",
                color = Color(0xFFD32F2F), // Dark Red
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        "1" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE8F5E9), // Light Green
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Present",
                color = Color(0xFF2E7D32), // Dark Green
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Combined status codes
        "0+1", "1+0" -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE), // Light Red
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "A",
                    color = Color(0xFFD32F2F), // Dark Red
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9), // Light Green
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "P",
                    color = Color(0xFF2E7D32), // Dark Green
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        "0+0" -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE), // Light Red
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "A",
                    color = Color(0xFFD32F2F), // Dark Red
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFEBEE), // Light Red
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "A",
                    color = Color(0xFFD32F2F), // Dark Red
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        "1+1" -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9), // Light Green
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "P",
                    color = Color(0xFF2E7D32), // Dark Green
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9), // Light Green
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "P",
                    color = Color(0xFF2E7D32), // Dark Green
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Special cases
        "GH", "H" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Holiday",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        "CS" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Suspended",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        "TL" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Teacher Leave",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        "MS" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF8E1), // Light Amber
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Mid Sem",
                color = Color(0xFFF57F17), // Dark Amber
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        "CR" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE3F2FD), // Light Blue
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Rescheduled",
                color = Color(0xFF1565C0), // Dark Blue
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Default case for any other status
        else -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = status,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}