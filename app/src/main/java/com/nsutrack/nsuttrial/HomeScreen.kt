package com.nsutrack.nsuttrial

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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.IntOffset
import com.nsutrack.nsuttrial.ui.theme.getAttendanceAdvice
import com.nsutrack.nsuttrial.ui.theme.getAttendanceStatusColor
import com.nsutrack.nsuttrial.ui.theme.getReadableTextColor
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
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                            showingAccountSheet = true
                        }) {
                        // If we have profile data, show the first letter of the name as the avatar
                        if (profileData != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profileData?.studentName?.take(1)?.uppercase() ?: "A",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
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
                // Wrap the content in SwipeRefresh
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isLoading),
                    onRefresh = {
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                        coroutineScope.launch {
                            viewModel.refreshData()
                        }
                    }
                ) {
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

@Composable
fun HomeScheduleSection(
    viewModel: AttendanceViewModel,
    hapticFeedback: HapticFeedback.HapticHandler
) {
    val timetableData by viewModel.timetableData.collectAsState()
    val isTimetableLoading by viewModel.isTimetableLoading.collectAsState()
    val timetableError by viewModel.timetableError.collectAsState()

    // State for current time and timer
    var currentTime by remember { mutableStateOf(Date()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Update time every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(60000) // Update every minute
        }
    }

    // Calculate today's day string
    val calendar = Calendar.getInstance().apply { time = currentTime }
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val today = days[weekday - 1]

    // Process schedule data
    val todaySchedule = if (timetableData == null) {
        getSampleSchedule(currentTime)
    } else {
        val todayClassSchedules = timetableData!!.schedule[today]
        todayClassSchedules?.mapNotNull { classSchedule ->
            val times = parseClassTimes(classSchedule.startTime, classSchedule.endTime, currentTime)
            if (times == null) {
                null
            } else {
                val (startTime, endTime) = times
                val color = viewModel.colorForSubject(classSchedule.subject)
                Schedule(
                    subject = classSchedule.subjectName ?: classSchedule.subject,
                    startTime = startTime,
                    endTime = endTime,
                    color = color,
                    room = classSchedule.room,
                    group = classSchedule.group
                )
            }
        }?.sortedBy { it.startTime } ?: getSampleSchedule(currentTime)
    }

    // Scroll to current/upcoming class
    LaunchedEffect(todaySchedule, currentTime) {
        if (todaySchedule.isNotEmpty()) {
            val currentClass = todaySchedule.firstOrNull { it.isCurrentTime(currentTime) }
            val nearestUpcoming = if (currentClass == null) {
                todaySchedule.filter { it.startTime.after(currentTime) }
                    .minByOrNull { it.startTime.time - currentTime.time }
            } else null

            val targetClass = currentClass ?: nearestUpcoming

            if (targetClass != null) {
                val index = todaySchedule.indexOf(targetClass)
                if (index >= 0) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(index)
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
                    .height(96.dp),
                shape = RoundedCornerShape(16.dp),
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
                    .height(110.dp)
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
                        val isCurrentClass = schedule.isCurrentTime(currentTime)

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
                val redLinePosition = calculateRedLinePosition(currentTime)
                if (redLinePosition > 0) {
                    RedLineIndicator(position = redLinePosition)
                }
            }
        }
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

    // Add elevation for current class
    val elevation = if (isCurrentClass) 6.dp else 3.dp

    Box(
        modifier = Modifier
            .width((width * animationScale).dp)
            .height((90 * animationScale).dp)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(16.dp),
                spotColor = schedule.color.copy(alpha = 0.5f)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = schedule.color.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
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
                if (schedule.room != null || schedule.group != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        if (schedule.room != null && schedule.group != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }

                        // Group info
                        if (schedule.group != null) {
                            Text(
                                text = schedule.group,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.9f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Add a small indicator for current class
        if (isCurrentClass) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
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

    val (adviceText, adviceColor) = getAttendanceAdvice(
        subject.overallPresent,
        subject.overallClasses
    )

    val attendanceColor = viewModel.getAttendanceStatusColor(subject.attendancePercentage)

    // Animation for the attendance percentage
    val animatedPercentage = remember { Animatable(0f) }

    LaunchedEffect(subject.attendancePercentage) {
        animatedPercentage.animateTo(
            targetValue = subject.attendancePercentage,
            animationSpec = tween(1000, easing = FastOutSlowInEasing)
        )
    }

    Card(  // Changed from ElevatedCard to Card for a flatter look
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                showDetailedView = true
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        // Removed elevation property to minimize shadow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = adviceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = adviceColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress indicator
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .fillMaxWidth(0.75f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedPercentage.value / 100f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(attendanceColor)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Percentage circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(attendanceColor.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = "${subject.attendancePercentage.toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = attendanceColor
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Show detailed view as a dialog
    if (showDetailedView) {
        DetailedAttendanceView(
            subject = subject,
            onDismiss = {
                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                showDetailedView = false
            }
        )
    }
}
// Helper function to parse class times
private fun parseClassTimes(startTimeStr: String, endTimeStr: String, baseDate: Date): Pair<Date, Date>? {
    try {
        if (!startTimeStr.contains(":") || !endTimeStr.contains(":")) {
            return null
        }

        val startComponents = startTimeStr.split(":")
        val endComponents = endTimeStr.split(":")

        if (startComponents.size != 2 || endComponents.size != 2) {
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

        val startTime = Schedule.createTimeForToday(startHour, startMinute, baseDate)
        val endTime = Schedule.createTimeForToday(endHour, endMinute, baseDate)

        return Pair(startTime, endTime)
    } catch (e: Exception) {
        Log.e("ScheduleSection", "Error parsing times: $startTimeStr-$endTimeStr: ${e.message}")
        return null
    }
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
    val hourWidth = 160f // Width for a 1-hour class
    val durationInHours = schedule.duration / 3600f
    return max(hourWidth, durationInHours * hourWidth) // Ensure minimum width
}
// Calculate position for the current time red line
private fun calculateRedLinePosition(currentTime: Date): Float {
    val hourWidth = 160f // Same scale as card width

    // Define start of day (9:00 AM for academic schedule)
    val startOfDay = Calendar.getInstance()
    startOfDay.time = currentTime
    startOfDay.set(Calendar.HOUR_OF_DAY, 9)
    startOfDay.set(Calendar.MINUTE, 0)
    startOfDay.set(Calendar.SECOND, 0)
    startOfDay.set(Calendar.MILLISECOND, 0)

    // Calculate elapsed time since start of day in hours
    val elapsedMillis = currentTime.time - startOfDay.timeInMillis
    val elapsedHours = elapsedMillis / (1000 * 60 * 60f)

    return if (elapsedHours > 0) {
        elapsedHours * hourWidth + 16f // 16dp padding offset
    } else {
        -100f // Hide line if before academic day start
    }
}