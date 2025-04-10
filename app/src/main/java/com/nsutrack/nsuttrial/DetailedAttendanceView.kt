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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.yourname.nsutrack.data.model.AttendanceRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import android.util.Log
import androidx.compose.animation.core.EaseOutQuint

/**
 * Bottom sheet implementation for attendance details with fast, smooth animations
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

    // Get window size for precise calculations
    val view = LocalView.current
    val density = LocalDensity.current
    val SmoothDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

// Optional: A complementary curve for the exit animation (emphasized accelerate)
    val SmoothAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Get current date to filter past records
    val currentDate = remember { Date() }
    val dateFormat = remember { SimpleDateFormat("MMM-dd", Locale.getDefault()) }
    val currentDateString = remember { dateFormat.format(currentDate) }

    // Parse month and day for comparison
    val currentMonth = currentDateString.split("-")[0]
    val currentDay = currentDateString.split("-")[1].toIntOrNull() ?: 0

    // Filter records - include ALL attendance types with meaningful status codes
    val filteredRecords = records.filter { record ->
        try {
            // Any record with a valid date format should be included
            val dateParts = record.date.split("-")
            if (dateParts.size != 2) return@filter false

            // Try to parse the day as a number
            val day = dateParts[1].toIntOrNull() ?: return@filter false

            // Accept any status that's not completely empty
            return@filter record.status.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }
    // Map months to their numerical order for proper sorting
    val monthOrder = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
    )

    // Calculate current month/year
    val calendar = Calendar.getInstance()
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonthNum = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based

    // Group and sort records by month
    val groupedRecords = filteredRecords
        .groupBy {
            it.date.split("-").firstOrNull() ?: ""
        }
        .toList()
        .sortedByDescending { (monthStr, _) ->
            // Month in numerical form
            val monthNum = monthOrder[monthStr] ?: 0

            // Determine if this month is from current year or previous year
            // For academic years that span calendar years (e.g., Aug-Jul)
            val yearValue = if (monthNum > currentMonthNum) currentYear - 1 else currentYear

            // Combine for sorting (yyyyMM format)
            yearValue * 100 + monthNum
        }

    // Log grouped records for debugging
    LaunchedEffect(Unit) {
        val monthsFound = groupedRecords.map { it.first }.joinToString(", ")
        Log.d("AttendanceView", "Found months: $monthsFound")
        Log.d("AttendanceView", "Current month: $currentMonth ($currentMonthNum)")

        // Log all record details
        filteredRecords.forEach {
            Log.d("AttendanceView", "Record: ${it.date} - ${it.status}")
        }
    }

    // Default to expanding current month
    val expandedMonths = remember {
        mutableStateOf(
            if (groupedRecords.isNotEmpty()) {
                if (groupedRecords.any { it.first == currentMonth }) {
                    setOf(currentMonth)
                } else {
                    setOf(groupedRecords.first().first)
                }
            } else {
                emptySet()
            }
        )
    }

    // Define sheet states - use Int to avoid type issues
    val EXPANDED = 0

    val HIDDEN = 2

    // Animation state for entry/exit
    val visible = remember { MutableTransitionState(false) }

    // Sheet state and drag state
    var sheetState by remember { mutableStateOf(EXPANDED) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var velocityTracker by remember { mutableStateOf(0f) }


    // Animation curves
    val standardEasing = FastOutSlowInEasing
    //val emphasizedEasing = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1.0f)

    LaunchedEffect(Unit) {
        delay(50)
        visible.targetState = true
    }

    // Handle back button
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
        coroutineScope.launch {
            visible.targetState = false
            delay(200)
            onDismiss()
        }
    }

    // Full screen dialog
    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                sheetState = HIDDEN // Trigger state change first
                visible.targetState = false
                delay(200) // Match exit animation
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
            // Scrim with animation
            val scrimAlpha by animateFloatAsState(
                // *** Simplified: Only depends on EXPANDED or not ***
                targetValue = if (sheetState == EXPANDED) 0.65f else 0f,
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
                        // *** Click outside dismisses directly ***
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                        coroutineScope.launch {
                            sheetState = HIDDEN
                            delay(150)
                            visible.targetState = false
                            delay(150)
                            onDismiss()
                        }
                    }
            )

            // Main sheet with animation
            AnimatedVisibility(
                visible = visible.targetState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    // *** Use tween with the custom easing curve ***
                    animationSpec = tween(
                        durationMillis = 400, // Slightly longer duration for smoother deceleration
                        easing = SmoothDecelerateEasing // Apply the custom curve
                    )
                ) + fadeIn(
                    // Keep fade-in relatively short
                    animationSpec = tween(150)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    // *** Use the complementary custom easing for exit ***
                    animationSpec = tween(
                        durationMillis = 300, // Exit can be slightly faster
                        easing = SmoothAccelerateEasing // Apply custom accelerate curve
                        // Alternative: FastOutLinearInEasing might still be fine
                    )
                ) + fadeOut(
                    // Faster fade-out on exit
                    tween(100)
                )
            )  {
                // Get the exact screen height to calculate precise sheet height
                var screenHeight by remember { mutableIntStateOf(0) }

                // Sheet height with precise calculation based on screen size
                val sheetHeightFraction by animateFloatAsState(
                    // *** MODIFIED VALUES ***
                    // Expanded state covers 75%
                    // Half-Expanded (initial resting state) covers 60%
                    targetValue = if (sheetState == EXPANDED) 0.85f else 0f,
                    animationSpec = spring( // Keep spring for nice feel
                        dampingRatio = 0.7f,
                        stiffness = 300f,
                        visibilityThreshold = 0.001f
                    ),
                    label = "SheetHeight"
                )
                val sheetHeightPx = if (screenHeight > 0) {
                    (screenHeight * sheetHeightFraction).roundToInt()
                } else {
                    0
                }

                // *** MOVE AnimateDpAsState HERE (outside graphicsLayer) ***
                val cornerRadius by animateDpAsState(
                    targetValue = if (sheetState == EXPANDED) 16.dp else 24.dp, // Adjust as desired
                    animationSpec = tween(200),
                    label = "SheetCornerRadius"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (sheetHeightPx.coerceAtMost((screenHeight * 0.85f).toInt())).toDp() })
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .onSizeChanged {
                            if (screenHeight == 0) {
                                screenHeight = view.height
                            }
                        }
                        .graphicsLayer {
                            // *** Use fixed or animated corner radius ***
                            this.shadowElevation = 8f // Consistent shadow for expanded state
                            this.shape = RoundedCornerShape(
                                topStart = cornerRadius,
                                topEnd = cornerRadius
                            )
                            clip = true
                        },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Draggable header
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
                                                // *** DEFINE Thresholds HERE ***
                                                val velocityDismissThreshold = 350f // Threshold for downward velocity to dismiss
                                                val offsetDismissThreshold = 80f   // Pixels dragged down to trigger dismiss

                                                when {
                                                    // Condition to dismiss downwards using the defined thresholds
                                                    currentVelocity > velocityDismissThreshold || dragOffset > offsetDismissThreshold -> {
                                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                        sheetState = HIDDEN
                                                        delay(150)
                                                        visible.targetState = false
                                                        delay(150)
                                                        onDismiss()
                                                    }

                                                    // Snap back if not dismissed
                                                    else -> {
                                                        val springSpec = spring<Float>(
                                                            dampingRatio = 0.7f,
                                                            stiffness = 500f
                                                        )
                                                        animate(
                                                            initialValue = dragOffset,
                                                            targetValue = 0f, // Snap back to original position
                                                            animationSpec = springSpec
                                                        ) { value, _ ->
                                                            dragOffset = value
                                                        }
                                                    }
                                                }
                                                velocityTracker = 0f // Reset velocity tracker
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            coroutineScope.launch {
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
                                        onVerticalDrag = { change, dragAmount -> change.consume()
                                            val resistanceFactor = if (dragAmount < 0) 0.3f else 1.0f

                                            velocityTracker = 0.75f * velocityTracker + 0.25f * dragAmount * 16f // Keep velocity tracking

                                            val newOffset = dragOffset + dragAmount * resistanceFactor
                                            dragOffset = newOffset

                                            // *** Clamp dragOffset: Prevent dragging significantly UP ***
                                            // Allow slight upward bounce (-30f), but allow larger downward drag (e.g., screenHeight * 0.3f)
                                            val maxDownwardDrag = if (screenHeight > 0) screenHeight * 0.3f else 200f
                                            dragOffset = dragOffset.coerceIn(-30f, maxDownwardDrag)
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
                                // Handle
                                Box(
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val handleWidth by animateFloatAsState(
                                        targetValue = if (isDragging) 48f else 36f,
                                        animationSpec = tween(100),
                                        label = "HandleWidth"
                                    )

                                    val handleOpacity by animateFloatAsState(
                                        targetValue = if (isDragging) 0.8f else 0.4f,
                                        animationSpec = tween(120),
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

                                // Title
                                val titleScale by animateFloatAsState(
                                    targetValue = if (sheetState == EXPANDED) 1f else 0.98f,
                                    animationSpec = tween(150),
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

                        // Divider
                        val dividerAlpha by animateFloatAsState(
                            targetValue = if (isDragging) 0.7f else 0.25f,
                            animationSpec = tween(100),
                            label = "DividerAlpha"
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (sheetState == EXPANDED) 0.dp else 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = dividerAlpha)
                        )

                        // Content area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
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
                                            // Critical: Extra bottom padding to ensure content fills past bottom nav
                                            bottom = 8.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(16.dp) // Spacing between items
                                    ) {
                                        item {
                                            AttendanceHeader(subject)
                                        }

                                        items(
                                            items = groupedRecords,
                                            key = { it.first }
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

                                        // Extra spacer at bottom to ensure content scrolls past navigation bar
                                        item {
                                            Spacer(modifier = Modifier.height(4.dp))
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

                    // Arrow animation
                    val rotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = tween(200),
                        label = "ArrowRotation"
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Records with animation - simple list of all records
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Single list with all records, sorted by day (most recent days first)
                records.forEach { record ->
                    RecordRow(record)
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
                // Removed the duplicated Text component here
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

        // Attendance advice card
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

        // Card colors
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
@Composable
fun RecordRow(record: AttendanceRecord) {
    // Format the date
    val day = record.date.split("-").getOrNull(1)?.toIntOrNull() ?: 0
    val month = record.date.split("-").getOrNull(0) ?: ""
    val dateString = "$day $month"

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
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

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
                text = "Mid Semester",
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

        "MB" -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE1BEE7), // Light Purple
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Mass Bunk",
                color = Color(0xFF7B1FA2), // Dark Purple
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