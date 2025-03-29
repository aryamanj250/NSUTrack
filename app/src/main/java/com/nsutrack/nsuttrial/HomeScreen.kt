package com.nsutrack.nsuttrial

import PullStretchEffect
import android.icu.text.SimpleDateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.nsutrack.nsuttrial.ui.theme.getAttendanceAdvice
import com.nsutrack.nsuttrial.ui.theme.getAttendanceStatusColor
import com.nsutrack.nsuttrial.ui.theme.getReadableTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: AttendanceViewModel
) {
    var isLoaded by remember { mutableStateOf(false) }
    var showingAccountSheet by remember { mutableStateOf(false) }
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val coroutineScope = rememberCoroutineScope()

    // Collect state flows
    val isLoading by viewModel.isLoading.collectAsState()
    val subjectData by viewModel.subjectData.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isAttendanceDataLoaded by viewModel.isAttendanceDataLoaded.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profileData by viewModel.profileData.collectAsState()
    val isProfileLoading by viewModel.isProfileLoading.collectAsState()
    val timetableData by viewModel.timetableData.collectAsState()
    val isTimetableLoading by viewModel.isTimetableLoading.collectAsState()

    // Animation delay
    LaunchedEffect(key1 = true) {
        delay(100)
        isLoaded = true
    }

    // Check session and fetch data
    LaunchedEffect(key1 = Unit) {
        if (sessionId == null) {
            viewModel.initializeSession()
            delay(1000)
            if (sessionId == null) {
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
    }

    // Fetch data if needed
    LaunchedEffect(key1 = sessionId, key2 = subjectData.size) {
        if (sessionId != null && subjectData.isEmpty() && !isLoading) {
            viewModel.refreshData()
        }
    }

    // Fetch profile and timetable if needed
    LaunchedEffect(key1 = sessionId, key2 = profileData) {
        if (profileData == null && !isProfileLoading && sessionId != null) {
            viewModel.fetchProfileData()
        }
    }

    LaunchedEffect(key1 = sessionId, key2 = timetableData) {
        if (timetableData == null && !isTimetableLoading && sessionId != null) {
            viewModel.fetchTimetableData()
        }
    }

    Scaffold(
        topBar = {
            EnhancedTopAppBar(
                title = stringResource(R.string.home),
                profileData = profileData,
                onProfileClick = {
                    showingAccountSheet = true
                },
                hapticFeedback = hapticFeedback
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(animationSpec = tween(500)) { it / 5 }
            ) {

                PullStretchEffect {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            // Schedule Section
                            HomeScheduleSection(viewModel = viewModel, hapticFeedback = hapticFeedback)
                        }

                        // Error message display
                        if (errorMessage.isNotEmpty()) {
                            item {
                                Modifier
                                    .fillMaxWidth()
                                ElevatedCard(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            visibilityThreshold = IntOffset.VisibilityThreshold
                                        )
                                    ),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = errorMessage,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        item {
                            // Section Header for Attendance
                            Text(
                                text = stringResource(R.string.attendance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Attendance Section
                        when {
                            isLoading && subjectData.isEmpty() -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp),
                                            strokeWidth = 4.dp
                                        )
                                    }
                                }
                            }
                            subjectData.isEmpty() -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stringResource(R.string.no_attendance_data),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyLarge
                                            )

                                            if (errorMessage.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = errorMessage,
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                                    // Keep manual refresh button for empty state
                                                    coroutineScope.launch {
                                                        viewModel.refreshData()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Refresh",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Refresh",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                items(
                                    items = subjectData,
                                    key = { it.code }
                                ) { subject ->
                                    AttendanceCard(
                                        subject = subject,
                                        viewModel = viewModel,
                                        hapticFeedback = hapticFeedback,
                                        modifier = Modifier.animateItemPlacement(
                                            animationSpec = tween(300)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation back to login if no session
        LaunchedEffect(key1 = sessionId) {
            if (sessionId == null && !isLoading && isLoaded) {
                delay(2000) // Give a moment to show the no session message
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
    }

    // Show Account View when requested
    if (showingAccountSheet) {
        AccountView(
            viewModel = viewModel,
            onDismiss = {
                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                showingAccountSheet = false
            }
        )
    }
}

// The rest of the file remains the same...
@Composable
fun HomeScheduleSection(
    viewModel: AttendanceViewModel,
    hapticFeedback: HapticFeedback.HapticHandler
) {
    val timetableData by viewModel.timetableData.collectAsState()
    val isTimetableLoading by viewModel.isTimetableLoading.collectAsState()
    val timetableError by viewModel.timetableError.collectAsState()

    // Force refresh of current time on recomposition
    val currentTime by remember { mutableStateOf(Date()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Force update time immediately upon composition
    var timeState by remember { mutableStateOf(Date()) }

    // Update time every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            timeState = Date() // Use fresh date
            delay(30000) // 30 second updates
        }
    }

    // Calculate today's day string
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val today = days[weekday - 1]

    // Process schedule data
    var todaySchedule by remember { mutableStateOf<List<Schedule>>(emptyList()) }

    // Process schedule data whenever timetableData, today or timeState changes
    LaunchedEffect(timetableData, today, timeState) {
        withContext(Dispatchers.Default) {
            try {
                val data = timetableData
                if (data != null) {
                    // Log available days
                    val availableDays = data.schedule.keys.joinToString(", ")
                    Log.d("ScheduleSection", "Available schedule days: $availableDays")

                    // Get today's class schedules
                    val todayClassSchedules = data.schedule[today]

                    if (todayClassSchedules != null && todayClassSchedules.isNotEmpty()) {
                        Log.d("ScheduleSection", "Found ${todayClassSchedules.size} classes for $today")

                        // Process each class schedule
                        val schedules = todayClassSchedules.mapNotNull { classSchedule ->
                            try {
                                // Parse class times
                                val timePair = parseClassTimes(
                                    classSchedule.startTime,
                                    classSchedule.endTime,
                                    Date()
                                ) ?: return@mapNotNull null

                                val (startTime, endTime) = timePair

                                // Generate a consistent color based on subject code
                                val color = generateConsistentColor(classSchedule.subject)

                                // Extract group numbers if they exist
                                val groupInfo = classSchedule.group?.trim()
                                val groupNumbers = groupInfo?.let {
                                    if (it.contains("Group", ignoreCase = true)) {
                                        it.replace("Group", "", ignoreCase = true)
                                            .trim().split(",").map { it.trim() }
                                    } else {
                                        listOf(it)
                                    }
                                } ?: emptyList()

                                Schedule(
                                    subject = classSchedule.subjectName ?: classSchedule.subject,
                                    startTime = startTime,
                                    endTime = endTime,
                                    color = color,
                                    room = classSchedule.room,
                                    group = groupInfo,
                                    groups = groupNumbers
                                )
                            } catch (e: Exception) {
                                Log.e("ScheduleSection", "Error processing class: ${e.message}")
                                null
                            }
                        }

                        // Merge schedules that should be combined
                        todaySchedule = mergeSchedules(schedules).sortedBy { it.startTime }
                        Log.d("ScheduleSection", "Final schedule count: ${todaySchedule.size}")
                        return@withContext
                    } else {
                        Log.d("ScheduleSection", "No schedules found for $today")
                    }
                } else {
                    Log.d("ScheduleSection", "Timetable data is null")
                }

                // If we get here, we couldn't get the real schedule, so use the sample
                todaySchedule = getSampleSchedule(Date())
            } catch (e: Exception) {
                Log.e("ScheduleSection", "Error processing schedule data: ${e.message}")
                e.printStackTrace()
                todaySchedule = getSampleSchedule(Date())
            }
        }
    }

    // Improved auto-scrolling logic with delay
    LaunchedEffect(todaySchedule, timeState) {
        if (todaySchedule.isNotEmpty()) {
            // Make sure the LazyRow has composed
            delay(300)

            // Find current class (class happening right now)
            val freshCurrentTime = Date() // Always use fresh time
            val currentClass = todaySchedule.firstOrNull { it.isCurrentTime(freshCurrentTime) }

            // If no current class, find the next upcoming class
            val nearestUpcoming = if (currentClass == null) {
                todaySchedule.filter { it.startTime.after(freshCurrentTime) }
                    .minByOrNull { it.startTime.time - freshCurrentTime.time }
            } else null

            // Use current class if available, otherwise use next upcoming class
            val targetClass = currentClass ?: nearestUpcoming

            if (targetClass != null) {
                val index = todaySchedule.indexOf(targetClass)
                if (index >= 0) {
                    Log.d("AutoScroll", "Scrolling to ${if (currentClass != null) "current" else "upcoming"} class: ${targetClass.subject} at index $index")
                    try {
                        listState.animateScrollToItem(index)
                    } catch (e: Exception) {
                        Log.e("AutoScroll", "Error scrolling: ${e.message}")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Section header with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.schedule),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (isTimetableLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (timetableError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = timetableError ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (todaySchedule.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_classes_today),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .height(90.dp)
                    .fillMaxWidth()
            ) {
                // Schedule cards in horizontal scrollable list
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = todaySchedule,
                        key = { it.id }
                    ) { schedule ->
                        val isCurrentClass = schedule.isCurrentTime(Date()) // Fresh time check

                        // Apply scaling animation to current class
                        val scale by animateFloatAsState(
                            targetValue = if (isCurrentClass) 1.05f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "Class card scale"
                        )

                        HomeScheduleCard(
                            schedule = schedule,
                            width = calculateWidth(schedule),
                            isCurrentClass = isCurrentClass,
                            animationScale = scale
                        )
                    }
                }

                // Red line indicator for current time
                val redLinePosition = calculateRedLinePosition(Date()) // Fresh time
                if (redLinePosition > 0) {
                    RedLineIndicator(position = redLinePosition)
                }
            }
        }
    }

    // Force fetch timetable data when this component is first displayed
    LaunchedEffect(Unit) {
        if (!isTimetableLoading) {
            viewModel.fetchTimetableData(forceRefresh = true)
        }
    }
}
// Helper function to parse class times - updated to be more robust
private fun parseClassTimes(startTimeStr: String, endTimeStr: String, baseDate: Date): Pair<Date, Date>? {
    try {
        if (!startTimeStr.contains(":") || !endTimeStr.contains(":")) {
            Log.w("TimeParser", "Invalid time format: $startTimeStr-$endTimeStr")
            return null
        }

        val startComponents = startTimeStr.split(":")
        val endComponents = endTimeStr.split(":")

        if (startComponents.size != 2 || endComponents.size != 2) {
            Log.w("TimeParser", "Invalid time components: $startComponents / $endComponents")
            return null
        }

        var startHour = startComponents[0].toInt()
        val startMinute = startComponents[1].toInt()
        var endHour = endComponents[0].toInt()
        val endMinute = endComponents[1].toInt()

        // Convert times like "01:00" to 13:00 for afternoon classes
        if (startHour < 9 && startHour != 12) {
            startHour += 12
        }
        if (endHour < 9 && endHour != 12) {
            endHour += 12
        }

        // Use fresh baseDate for today's date
        val today = Calendar.getInstance().apply {
            time = baseDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val startTime = Schedule.createTimeForToday(startHour, startMinute, today)
        val endTime = Schedule.createTimeForToday(endHour, endMinute, today)

        // Debug log the times
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        Log.d("TimeParser", "Parsed $startTimeStr-$endTimeStr to ${dateFormat.format(startTime)}-${dateFormat.format(endTime)}")

        return Pair(startTime, endTime)
    } catch (e: Exception) {
        Log.e("TimeParser", "Error parsing times: $startTimeStr-$endTimeStr: ${e.message}")
        e.printStackTrace()
        return null
    }
}
@Composable
fun HomeScheduleCard(
    schedule: Schedule,
    width: Float,
    isCurrentClass: Boolean,
    animationScale: Float
) {
    val textColor = getReadableTextColor(schedule.color)

    // Add subtle pulsing animation for current class
    val pulseAnimation = rememberInfiniteTransition(label = "PulseAnimation")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = if (isCurrentClass) 0.92f else 1f,
        targetValue = if (isCurrentClass) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse Alpha"
    )

    // Add elevation for current class
    val elevation = if (isCurrentClass) 6.dp else 3.dp

    Box(
        modifier = Modifier
            .width((width * animationScale).dp)
            .height((75 * animationScale).dp)
            .graphicsLayer {
                alpha = pulseAlpha
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(12.dp),
                spotColor = schedule.color.copy(alpha = 0.7f)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = schedule.color.copy(alpha = 0.95f)
            )
        ) {
            // Add subtle gradient overlay for depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Subject name
                    Text(
                        text = schedule.subject,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor.copy(alpha = 0.95f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    // Time range
                    Text(
                        text = schedule.timeRange,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.95f),
                        maxLines = 1
                    )

                    // Room and group info
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Room info
                        if (schedule.room != null) {
                            val roomText = if (schedule.room.contains(", ")) {
                                val rooms = schedule.room.split(", ")
                                if (rooms.size > 2) {
                                    "${rooms[0]}, ${rooms[1]}..."
                                } else {
                                    schedule.room
                                }
                            } else {
                                schedule.room
                            }

                            Text(
                                text = roomText,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.9f),
                                maxLines = 1
                            )
                        }

                        // Separator
                        if (schedule.room != null && schedule.getFormattedGroups() != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }

                        // Group info using the method
                        schedule.getFormattedGroups()?.let { groups ->
                            Text(
                                text = groups,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.9f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Add a small indicator for current class with animation
        if (isCurrentClass) {
            val indicatorScale by pulseAnimation.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Indicator Scale"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(indicatorScale)
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
@Composable
fun AttendanceCard(
    subject: SubjectData,
    viewModel: AttendanceViewModel,
    hapticFeedback: HapticFeedback.HapticHandler,
    modifier: Modifier = Modifier
) {
    var showDetailedView by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()  // Add this line


    var isPressed by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "Card Scale"
    )

    val cardElevation by animateFloatAsState(
        targetValue = if (isPressed) 1f else 3f,
        label = "Card Elevation"
    )

    val (adviceText, _) = getAttendanceAdvice(
        subject.overallPresent,
        subject.overallClasses
    )

    // Get attendance color based on percentage
    val attendanceColor = when {
        subject.attendancePercentage >= 85.0f -> Color(0xFF4CAF50) // Green
        subject.attendancePercentage >= 75.0f -> Color(0xFF8BC34A) // Light Green
        subject.attendancePercentage >= 65.0f -> Color(0xFFFFC107) // Yellow/Amber
        subject.attendancePercentage >= 60.0f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFE57373) // Soft Red
    }

    Column(modifier = modifier) {
        // Main content with enhanced animation
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(cardScale)
                .shadow(
                    elevation = cardElevation.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = attendanceColor.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    // Trigger pressed state briefly
                    isPressed = true
                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)

                    // Reset after a short delay
                    coroutineScope.launch {
                        delay(100)
                        isPressed = false
                        delay(50)
                        showDetailedView = true
                    }
                },
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left section with subject info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = adviceText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Right section with percentage and arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Percentage with colored text
                        Text(
                            text = "${subject.attendancePercentage.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = attendanceColor
                        )

                        // Arrow icon with animation
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isPressed) 10f else 0f,
                            label = "Arrow Rotation"
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .rotate(arrowRotation)
                        )
                    }
                }

                // Divider at the bottom of each card
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
            }
        }
    }

    // Show detailed view when requested
    if (showDetailedView) {
        DetailedAttendanceView(
            subject = subject,
            onDismiss = {
                showDetailedView = false
            }
        )
    }
}
// Helper function to generate consistent colors for subjects
private fun generateConsistentColor(subjectCode: String): Color {
    // Simple hash function to get consistent colors
    val hash = subjectCode.hashCode()
    val r = ((hash and 0xFF0000) shr 16) / 255f
    val g = ((hash and 0x00FF00) shr 8) / 255f
    val b = (hash and 0x0000FF) / 255f

    // Ensure good color saturation
    return Color(
        red = 0.3f + 0.6f * r,
        green = 0.3f + 0.6f * g,
        blue = 0.3f + 0.6f * b,
        alpha = 1.0f
    )
}

// Helper function to parse class times

// Helper function to merge related schedules
private fun mergeSchedules(schedules: List<Schedule>): List<Schedule> {
    if (schedules.isEmpty()) return emptyList()

    val result = mutableListOf<Schedule>()
    val processedIds = mutableSetOf<String>()

    // First pass - merge time-adjacent classes for the same subject
    for (i in schedules.indices) {
        // Skip if already processed
        if (processedIds.contains(schedules[i].id)) continue

        var current = schedules[i]
        processedIds.add(current.id)

        // Look for adjacent schedules to merge
        var madeChanges = true
        while (madeChanges) {
            madeChanges = false

            for (j in schedules.indices) {
                if (processedIds.contains(schedules[j].id)) continue

                if (current.shouldMergeWith(schedules[j])) {
                    current = current.mergeWith(schedules[j])
                    processedIds.add(schedules[j].id)
                    madeChanges = true
                    break
                }
            }
        }

        result.add(current)
    }

    // Second pass - merge classes with the same time but different groups
    val finalResult = mutableListOf<Schedule>()
    val processedResultIds = mutableSetOf<String>()

    for (i in result.indices) {
        if (processedResultIds.contains(result[i].id)) continue

        var current = result[i]
        processedResultIds.add(current.id)

        for (j in result.indices) {
            if (processedResultIds.contains(result[j].id)) continue

            // If same subject, same time window, but different groups
            if (current.subject == result[j].subject &&
                current.startTime.time == result[j].startTime.time &&
                current.endTime.time == result[j].endTime.time) {

                current = current.mergeWith(result[j])
                processedResultIds.add(result[j].id)
            }
        }

        finalResult.add(current)
    }

    return finalResult
}

// Sample schedule data when no real data is available
private fun getSampleSchedule(baseDate: Date): List<Schedule> {
    return listOf(
        Schedule(
            subject = "Discrete Structures",
            startTime = Schedule.createTimeForToday(9, 0, baseDate),
            endTime = Schedule.createTimeForToday(10, 0, baseDate),
            color = Color(0xFF78909C) // Blue Gray
        ),
        Schedule(
            subject = "Computer Programming",
            startTime = Schedule.createTimeForToday(10, 0, baseDate),
            endTime = Schedule.createTimeForToday(11, 0, baseDate),
            color = Color(0xFF66BB6A) // Green
        ),
        Schedule(
            subject = "Mathematics-II",
            startTime = Schedule.createTimeForToday(11, 0, baseDate),
            endTime = Schedule.createTimeForToday(12, 0, baseDate),
            color = Color(0xFF9575CD) // Purple
        ),
        Schedule(
            subject = "Network Analysis and Synthesis",
            startTime = Schedule.createTimeForToday(12, 0, baseDate),
            endTime = Schedule.createTimeForToday(13, 0, baseDate),
            color = Color(0xFF4FC3F7) // Light Blue
        )
    )
}

// Calculate width for schedule card based on duration
private fun calculateWidth(schedule: Schedule): Float {
    val baseWidth = 160f // Base width for a 1-hour class
    val minWidth = 160f  // Minimum width for any class

    val durationInHours = schedule.duration / 3600f

    return max(minWidth, baseWidth * durationInHours)
}


private fun calculateRedLinePosition(currentTime: Date): Float {
    val hourWidth = 160f

    val startOfDay = Calendar.getInstance()
    startOfDay.time = currentTime
    startOfDay.set(Calendar.HOUR_OF_DAY, 9)
    startOfDay.set(Calendar.MINUTE, 0)
    startOfDay.set(Calendar.SECOND, 0)
    startOfDay.set(Calendar.MILLISECOND, 0)

    val elapsedMillis = currentTime.time - startOfDay.timeInMillis
    val elapsedHours = elapsedMillis / (1000 * 60 * 60f)

    return if (elapsedHours > 0) {
        elapsedHours * hourWidth + 16f // 16dp padding offset
    } else {
        -100f
    }
}
@Composable
fun EnhancedTopAppBar(
    title: String,
    profileData: ProfileData?,
    onProfileClick: () -> Unit,
    hapticFeedback: HapticFeedback.HapticHandler
) {

    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        isLoaded = true
    }

    val titleOffset by animateFloatAsState(
        targetValue = if (isLoaded) 0f else -50f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Title Animation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        // Blurred background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 3.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title with animation
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = titleOffset.dp)
            )

            // Profile button with animation
            ProfileButton(
                profileData = profileData,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                    onProfileClick()
                }
            )
        }
    }
}

@Composable
fun ProfileButton(
    profileData: ProfileData?,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Profile Button Scale"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier.scale(scale)
    ) {
        if (profileData != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profileData.studentName?.take(1)?.uppercase() ?: "A",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

