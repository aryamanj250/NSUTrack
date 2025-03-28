package com.nsutrack.nsuttrial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourname.nsutrack.data.model.AttendanceRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Custom easing curves for smoother animations
private val EaseInQuart = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

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

    // Filter records to show only:
    // 1. Past dates
    // 2. Classes that actually occurred (Present/Absent) - exclude holidays, mass bunks, etc.
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

    // Animation state for smooth entry/exit
    val visible = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        delay(50)  // Short delay to ensure UI is ready
        visible.targetState = true
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

    // Full screen dialog with animations
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
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
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
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400, easing = EaseOutQuart)
                ) + fadeIn(animationSpec = tween(350)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300, easing = EaseInQuart)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                    // Keep the Scaffold within the Surface
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = subject.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                        coroutineScope.launch {
                                            visible.targetState = false
                                            delay(300)
                                            onDismiss()
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                actions = {
                                    IconButton(onClick = { /* do nothing */ }, enabled = false) {
                                        Box(modifier = Modifier.size(24.dp))
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        // Rest of your content remains the same
                        // Just pass paddingValues to content
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            // Your existing content here
                            when {
                                filteredRecords.isEmpty() -> {
                                    // Empty state
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
                                    // Attendance data
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 24.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        item {
                                            AttendanceHeader(subject)
                                        }

                                        items(sortedGroups) { (month, monthRecords) ->
                                            MonthSection(
                                                month = month,
                                                records = monthRecords.sortedByDescending {
                                                    it.date.split("-").getOrNull(1)?.toIntOrNull()
                                                        ?: 0
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
            // Remove the circle with percentage here - it's now gone!

            // Keep the stats row
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

            // Advice card with styled border and colors
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(16.dp)
                    ),
                color = bgColor,
                shape = RoundedCornerShape(16.dp)
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
    fun MonthSection(
        month: String,
        records: List<AttendanceRecord>,
        isExpanded: Boolean,
        onToggle: () -> Unit
    ) {
        // Count classes by type for the header display
        val presentCount =
            records.count { it.status == "1" || it.status == "1+1" || it.status == "0+1" || it.status == "1+0" }
        val absentCount = records.count { it.status == "0" || it.status == "0+0" }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Month header with ripple effect and class count
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

                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            label = "Arrow Rotation"
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

            // Records
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
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

            // Format the status
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