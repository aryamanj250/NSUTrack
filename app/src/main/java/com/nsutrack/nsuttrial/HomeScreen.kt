package com.nsutrack.nsuttrial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.nsutrack.nsuttrial.ui.theme.getAttendanceAdvice
import com.nsutrack.nsuttrial.ui.theme.getAttendanceStatusColor
import com.nsutrack.nsuttrial.ui.theme.getReadableTextColor
import java.util.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: AttendanceViewModel
) {
    var isLoaded by remember { mutableStateOf(false) }
    var showingAccountSheet by remember { mutableStateOf(false) }

    // Collect state flows
    val isLoading by viewModel.isLoading.collectAsState()
    val subjectData by viewModel.subjectData.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isAttendanceDataLoaded by viewModel.isAttendanceDataLoaded.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Collect profile and timetable data
    val profileData by viewModel.profileData.collectAsState()
    val isProfileLoading by viewModel.isProfileLoading.collectAsState()
    val timetableData by viewModel.timetableData.collectAsState()
    val isTimetableLoading by viewModel.isTimetableLoading.collectAsState()

    // Animation delay
    LaunchedEffect(key1 = true) {
        delay(100)
        isLoaded = true
    }

    // Check session on initialization and handle null session
    LaunchedEffect(key1 = Unit) {
        Log.d("SessionDebug", "HomeScreen loaded with session ID: ${sessionId ?: "null"}")

        // Handle null session ID
        if (sessionId == null) {
            Log.d("SessionDebug", "No session ID found, initializing session")
            viewModel.initializeSession()
            // Wait a moment to see if we can get a session
            delay(1000)

            // If still no session after delay, navigate back to login
            if (sessionId == null) {
                Log.d("SessionDebug", "Failed to get session, returning to login")
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
    }

    // Fetch data if we have session but no data
    LaunchedEffect(key1 = sessionId, key2 = subjectData.size) {
        if (sessionId != null && subjectData.isEmpty() && !isLoading) {
            Log.d("SessionDebug", "We have session but no data, refreshing data")
            viewModel.refreshData()
        }
    }

    // Fetch profile data if not already loaded
    LaunchedEffect(key1 = sessionId, key2 = profileData) {
        if (profileData == null && !isProfileLoading && sessionId != null) {
            Log.d("SessionDebug", "Fetching profile data with session ID: $sessionId")
            viewModel.fetchProfileData()
        }
    }

    // Fetch timetable data if not already loaded
    LaunchedEffect(key1 = sessionId, key2 = timetableData) {
        if (timetableData == null && !isTimetableLoading && sessionId != null) {
            Log.d("SessionDebug", "Fetching timetable data with session ID: $sessionId")
            viewModel.fetchTimetableData()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showingAccountSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    )


    { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInHorizontally(animationSpec = tween(500)) { it / 5 }
            ) {
                // Wrap the content in SwipeRefresh
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isLoading),
                    onRefresh = { viewModel.refreshData() }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = rememberLazyListState()
                    ) {
                        item {
                            // Schedule Section
                            HomeScheduleSection(viewModel = viewModel)

                            // Error message display
                            if (errorMessage.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = errorMessage,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }

                            // Attendance Section
                            Spacer(modifier = Modifier.height(8.dp))
                            AttendanceSection(viewModel = viewModel)
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
            onDismiss = { showingAccountSheet = false }
        )
    }
}

@Composable
fun HomeScheduleSection(viewModel: AttendanceViewModel) {
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

    // Process schedule data without using remember with Composable functions
    val todaySchedule = if (timetableData == null) {
        // Return sample schedule if no timetable data
        getSampleSchedule(currentTime)
    } else {
        val todayClassSchedules = timetableData!!.schedule[today]
        // Process each class schedule
        todayClassSchedules?.mapNotNull { classSchedule ->
            // Parse class times safely
            val times = parseClassTimes(classSchedule.startTime, classSchedule.endTime, currentTime)
            if (times == null) {
                Log.e("ScheduleSection", "Could not parse times for ${classSchedule.subject}")
                null
            } else {
                val (startTime, endTime) = times

                // Get color for the subject (this is a Composable call)
                val color = viewModel.colorForSubject(classSchedule.subject)

                // Create schedule object
                Schedule(
                    subject = classSchedule.subjectName ?: classSchedule.subject,
                    startTime = startTime,
                    endTime = endTime,
                    color = color,
                    room = classSchedule.room,
                    group = classSchedule.group
                )
            }
        }?.sortedBy { it.startTime }
            ?: // Return sample schedule if no classes today
            getSampleSchedule(currentTime)
    }

    // Check if we need to scroll to current/upcoming class
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
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
            Text(
                text = timetableError ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else if (todaySchedule.isEmpty()) {
            Text(
                text = stringResource(R.string.no_classes_today),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .height(88.dp)
                    .fillMaxWidth()
            ) {
                // Schedule cards in horizontal scrollable list
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()

                ) {
                    items(
                        items = todaySchedule,
                        key = { it.id }
                    ) { schedule ->
                        HomeScheduleCard(
                            schedule = schedule,
                            width = calculateWidth(schedule)
                        )
                    }
                }

                // Red line indicator for current time
                val redLinePosition = calculateRedLinePosition(currentTime)
                    @Composable
                    fun RedLineIndicator(position: Float) {
                        val errorColor = MaterialTheme.colorScheme.error

                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .offset(x = redLinePosition.dp)
                    ) {
                        drawLine(
                            color = errorColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2f
                        )
                    }
                }
                if (redLinePosition > 0) {
                    RedLineIndicator(position = redLinePosition)
                }
            }
        }
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
    // Need to return fixed Color values that don't depend on MaterialTheme
    return listOf(
        Schedule(
            subject = "Discrete Structures",
            startTime = Schedule.createTimeForToday(9, 0, baseDate),
            endTime = Schedule.createTimeForToday(10, 0, baseDate),
            color = Color.Gray
        ),
        Schedule(
            subject = "Computer Programming",
            startTime = Schedule.createTimeForToday(10, 0, baseDate),
            endTime = Schedule.createTimeForToday(11, 0, baseDate),
            color = Color.Green
        ),
        Schedule(
            subject = "Mathematics-II",
            startTime = Schedule.createTimeForToday(11, 0, baseDate),
            endTime = Schedule.createTimeForToday(12, 0, baseDate),
            color = Color(0xFF9C27B0) // Purple
        ),
        Schedule(
            subject = "Network Analysis and Synthesis",
            startTime = Schedule.createTimeForToday(12, 0, baseDate),
            endTime = Schedule.createTimeForToday(13, 0, baseDate),
            color = Color.Blue
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
    startOfDay.set(Calendar.HOUR_OF_DAY, 9)
    startOfDay.set(Calendar.MINUTE, 0)
    startOfDay.set(Calendar.SECOND, 0)
    startOfDay.set(Calendar.MILLISECOND, 0)

    // Calculate elapsed time since start of day in hours
    val elapsedMillis = currentTime.time - startOfDay.time.time
    val elapsedHours = elapsedMillis / (1000 * 60 * 60f)

    return if (elapsedHours > 0) {
        elapsedHours * hourWidth + 16f // 16dp padding offset
    } else {
        -100f // Hide line if before academic day start
    }
}

// Then use it inside a Composable
@Composable
fun TimeIndicator(currentTime: Date) {
    val position = calculateRedLinePosition(currentTime)
    val errorColor = MaterialTheme.colorScheme.error // Get color in Composable context

    if (position > 0) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset(x = position.dp)
        ) {
            drawLine(
                color = errorColor,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 2f
            )
        }
    }
}

// Process timetable data to get today's schedule
@Composable
private fun getTodaySchedule(
    timetableData: TimetableData?,
    viewModel: AttendanceViewModel,
    currentTime: Date
): List<Schedule> {
    if (timetableData == null) {
        return getSampleSchedule(currentTime)
    }

    // Get current day name (Mon, Tue, etc.)
    val calendar = Calendar.getInstance()
    calendar.time = currentTime
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)

    // Map weekday number to day string
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val today = days[weekday - 1]

    Log.d("ScheduleSection", "Getting schedule for $today")

    // Get today's schedule from timetable data
    val todayClassSchedules = timetableData.schedule[today] ?: return getSampleSchedule(currentTime)

    // Process the class schedules
    return todayClassSchedules.mapNotNull { classSchedule ->
        // Use the helper function instead of try-catch
        val times = parseTimesSafely(
            classSchedule.startTime,
            classSchedule.endTime,
            currentTime,
            classSchedule.subject
        ) ?: return@mapNotNull null

        val (startTime, endTime) = times

        // Use the colorForSubject function that now uses Material theme
        val color = viewModel.colorForSubject(classSchedule.subject)

        // Use subject name if available, otherwise code
        val subjectName = classSchedule.subjectName ?: classSchedule.subject

        Schedule(
            subject = subjectName,
            startTime = startTime,
            endTime = endTime,
            color = color,
            room = classSchedule.room,
            group = classSchedule.group
        )
    }.sortedBy { it.startTime } // Sort by start time
}

// Parse class start and end times from strings
// Non-Composable helper function to parse times safely
private fun parseTimesSafely(
    startTimeStr: String,
    endTimeStr: String,
    currentTime: Date,
    subjectCode: String
): Pair<Date, Date>? {
    try {
        // Parse time strings (format: "HH:MM")
        if (!startTimeStr.contains(":") || !endTimeStr.contains(":")) {
            Log.e("ScheduleSection", "Could not parse times for $subjectCode")
            return null
        }
        val startComponents = startTimeStr.split(":")
        val endComponents = endTimeStr.split(":")

        // Rest of your parsing logic
        var startHour = startComponents[0].toInt()
        val startMinute = startComponents[1].toInt()
        var endHour = endComponents[0].toInt()
        val endMinute = endComponents[1].toInt()


        // ... (rest of your parsing code)

        val startTime = Schedule.createTimeForToday(startHour, startMinute, currentTime)
        val endTime = Schedule.createTimeForToday(endHour, endMinute, currentTime)

        return Pair(startTime, endTime)
    } catch (e: Exception) {
        Log.e("ScheduleSection", "Error parsing times for $subjectCode: ${e.message}")
        return null
    }
}
// Provide sample schedule data when no real data is available

@Composable
fun HomeScheduleCard(schedule: Schedule, width: Float) {
    val textColor = getReadableTextColor(schedule.color)

    Box(
        modifier = Modifier
            .width(width.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(schedule.color.copy(alpha = 0.9f))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
}

@Composable
fun AttendanceSection(viewModel: AttendanceViewModel, modifier: Modifier = Modifier) {
    val subjectData by viewModel.subjectData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.attendance),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp)
        )

        if (isLoading && subjectData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (subjectData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.no_attendance_data),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        onClick = { viewModel.refreshData() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Refresh")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subjectData.forEach { subject ->
                    AttendanceCard(subject = subject, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AttendanceCard(subject: SubjectData, viewModel: AttendanceViewModel) {
    var showDetailedView by remember { mutableStateOf(false) }

    val (adviceText, adviceColor) = getAttendanceAdvice(
        subject.overallPresent,
        subject.overallClasses
    )

    val attendanceColor = viewModel.getAttendanceStatusColor(subject.attendancePercentage)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailedView = true },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
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

                Text(
                    text = adviceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = adviceColor
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val percentage = subject.attendancePercentage

                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = attendanceColor
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    // Show detailed view as a dialog
    if (showDetailedView) {
        DetailedAttendanceView(
            subject = subject,
            onDismiss = { showDetailedView = false }
        )
    }
}