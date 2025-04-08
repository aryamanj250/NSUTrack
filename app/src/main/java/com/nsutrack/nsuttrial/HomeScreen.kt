package com.nsutrack.nsuttrial

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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import com.nsutrack.nsuttrial.ui.theme.getAttendanceAdvice
import com.nsutrack.nsuttrial.ui.theme.getReadableTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

private val scheduleColorPalette = listOf(
    Color(0xFF82C584),
    Color(0xFF64B5F6),
    Color(0xFFFFB74D),
    Color(0xFFBA68C8),
    Color(0xFFE57373),
    Color(0xFF4DB6AC),
    Color(0xFFFFF176),
    Color(0xFF90A4AE),
    Color(0xFFF06292)  // Pink 300
    // You can refine this further. Maybe swap one out for a Brown-ish tone if desired.
)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class, ExperimentalMaterialApi::class // OptIn for PullRefresh
)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: AttendanceViewModel
) {
    // --- State Declarations ---
    var isContentVisible by remember { mutableStateOf(false) }
    var showingAccountSheet by remember { mutableStateOf(false) }
    val hapticFeedback = HapticFeedback.getHapticFeedback() // Ensure HapticFeedback object exists
    val coroutineScope = rememberCoroutineScope()

    // --- ViewModel State Collection ---
    val isLoading by viewModel.isLoading.collectAsState() // Used for pull-refresh state
    val subjectData by viewModel.subjectData.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profileData by viewModel.profileData.collectAsState()
    // Specific loading states can be kept if needed for finer-grained UI updates
    val isProfileLoading by viewModel.isProfileLoading.collectAsState()
    val isTimetableLoading by viewModel.isTimetableLoading.collectAsState()
    val timetableData by viewModel.timetableData.collectAsState()

    // --- Pull-to-Refresh State ---
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading, // Link to the main loading state
        onRefresh = {
            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
            Log.d("HomeScreen", "Pull-to-refresh initiated by user.")
            viewModel.performPullToRefresh() // Call the new ViewModel function
        }
    )

    // --- Animation States ---
    val contentAlpha by animateFloatAsState(
        targetValue = if (isContentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 50),
        label = "ContentAlpha"
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (isContentVisible) 0f else 20f, // Reduced offset
        animationSpec = tween(durationMillis = 400, delayMillis = 50, easing = EaseOutCubic),
        label = "ContentOffsetY"
    )

    // --- LaunchedEffects ---
    LaunchedEffect(key1 = Unit) {
        delay(100) // Slight delay before fade-in
        isContentVisible = true
    }

    // Effect to navigate to login if session is null and no credentials stored
    LaunchedEffect(key1 = sessionId, key2 = viewModel.hasStoredCredentials()) {
        if (sessionId == null && !viewModel.hasStoredCredentials() && !isLoading) {
            Log.d("HomeScreen", "No session or credentials, navigating to login.")
            // Prevent navigation if a login or refresh is already in progress
            if (!viewModel.isLoginInProgress.value) {
                navController.navigate("login") { popUpTo("home") { inclusive = true } }
            }
        }
        // Trigger initial load if session is present but data is missing
        else if (sessionId != null && subjectData.isEmpty() && !isLoading && viewModel.hasStoredCredentials()) {
            Log.d("HomeScreen", "Session valid, has credentials, triggering initial load via refresh.")
            viewModel.performPullToRefresh()
        }
    }

    // --- UI Structure ---
    Scaffold(
        topBar = {
            EnhancedTopAppBar( // Use your EnhancedTopAppBar
                title = stringResource(R.string.home),
                profileData = profileData,
                onProfileClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                    showingAccountSheet = true
                },
                hapticFeedback = hapticFeedback
            )
        }
    ) { scaffoldPadding ->

        // Box container for PullRefresh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding()) // Apply top padding ONLY
                .pullRefresh(pullRefreshState) // Apply pullRefresh modifier HERE
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // No top padding here; handled by Box and Scaffold
                    .padding(bottom = scaffoldPadding.calculateBottomPadding()) // Apply bottom nav padding if bar is shown
                    .background(MaterialTheme.colorScheme.background)
                    .graphicsLayer { // Apply entry animation
                        alpha = contentAlpha
                        translationY = contentOffsetY
                    },
                state = rememberLazyListState(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 8.dp, // Padding below the TopAppBar/PullIndicator area
                    bottom = 16.dp // Extra padding at the very bottom
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp) // Space between items
            ) {
                // --- Schedule Section ---
                item {
                    // Ensure HomeScheduleSection composable exists and is used here
                    HomeScheduleSection(viewModel = viewModel, hapticFeedback = hapticFeedback)
                }

                // --- Error Message Display ---
                if (errorMessage.isNotEmpty()) {
                    item {
                        // Keep your error card implementation, make sure it animates placement
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement() // Animate placement
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // --- Attendance Header ---
                item {
                    Text(
                        text = stringResource(R.string.attendance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 8.dp) // Adjusted padding
                            .padding(horizontal = 16.dp)
                            .animateItemPlacement() // Animate placement
                    )
                }

                // --- Attendance List Content (States) ---
                when {
                    // Loading State (Subtle placeholder while pull-refresh is active)
                    isLoading && subjectData.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp) // Smaller placeholder height
                                    .padding(horizontal = 16.dp)
                                    .animateItemPlacement(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Optional: Show a very subtle text or nothing,
                                // relying on the PullRefreshIndicator
                                Text(
                                    stringResource(R.string.loading_attendance), // "Loading attendance..."
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    // Empty State (When not loading and no data/error)
                    subjectData.isEmpty() && !isLoading && errorMessage.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 16.dp)
                                    .animateItemPlacement(), // Animate empty state
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.no_attendance_data),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button( // Explicit refresh button
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                                            viewModel.performPullToRefresh() // Trigger refresh
                                        },
                                        shape = RoundedCornerShape(24.dp) // Modern shape
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.refresh),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.refresh))
                                    }
                                }
                            }
                        }
                    }
                    // Data Available State
                    subjectData.isNotEmpty() -> {
                        // Wrap subjects in a single Card item
                        item(key = "attendance-list-card") { // Use a stable key
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItemPlacement(tween(300)) // Animate card placement
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp), // Consistent rounding
                                colors = CardDefaults.cardColors(
                                    // Use a slightly transparent surface container for depth
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Subtle elevation
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    subjectData.forEachIndexed { index, subject ->
                                        // Ensure SubjectRowContent composable exists and is used here
                                        SubjectRowContent(
                                            subject = subject,
                                            viewModel = viewModel, // Pass if needed by detail view
                                            hapticFeedback = hapticFeedback,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        // Add divider, except after the last item
                                        if (index < subjectData.size - 1) {
                                            HorizontalDivider( // Use M3 Divider
                                                modifier = Modifier.padding(horizontal = 16.dp), // Indent divider
                                                thickness = 0.5.dp, // Thin divider
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // --- End Attendance List Content ---

            } // End LazyColumn

            // PullRefreshIndicator - Positioned at the top center within the Box
            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface, // Match background
                contentColor = MaterialTheme.colorScheme.primary // Indicator color
            )

        } // End Box for PullRefresh
    } // End Scaffold

    // --- Account Bottom Sheet Display (Keep existing logic) ---
    if (showingAccountSheet) {
        // Ensure AccountView composable exists and is used here
        AccountView(
            viewModel = viewModel,
            onDismiss = { showingAccountSheet = false },
            onLogout = {
                showingAccountSheet = false // Dismiss sheet first
                coroutineScope.launch {
                    delay(250) // Allow sheet dismiss animation
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
        )
    }
} // End HomeScreen Composable

// The rest of your HomeScheduleSection and related functions remain the same
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class) // Make sure necessary OptIns are present at file/function level
@Composable
fun HomeScheduleSection(
    viewModel: AttendanceViewModel,
    hapticFeedback: HapticFeedback.HapticHandler // Assuming you still pass this
) {
    val timetableData by viewModel.timetableData.collectAsState()
    val isTimetableLoading by viewModel.isTimetableLoading.collectAsState()
    val timetableError by viewModel.timetableError.collectAsState()

    // Force refresh of current time on recomposition (use a state for updates)
    var timeState by remember { mutableStateOf(Date()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Update time every 30 seconds (keep your timer logic)
    LaunchedEffect(Unit) {
        while (true) {
            timeState = Date() // Use fresh date
            delay(30000) // 30 second updates
        }
    }

    // Calculate today's day string
    val calendar = Calendar.getInstance()
    calendar.time = timeState // Use the state for consistency
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val today = days[weekday - 1]

    // Process schedule data state
    var todaySchedule by remember { mutableStateOf<List<Schedule>>(emptyList()) }

    // Process schedule data whenever timetableData, today or timeState changes
    LaunchedEffect(timetableData, today, timeState) {
        withContext(Dispatchers.Default) {
            try {
                val data = timetableData
                var processedSchedules: List<Schedule> = emptyList() // Default to empty

                if (data != null) {
                    val availableDays = data.schedule.keys.joinToString(", ")
                    Log.d("ScheduleSection", "Available schedule days: $availableDays")
                    val todayClassSchedules = data.schedule[today]

                    if (todayClassSchedules != null && todayClassSchedules.isNotEmpty()) {
                        Log.d("ScheduleSection", "Found ${todayClassSchedules.size} classes for $today")
                        val schedules = todayClassSchedules.mapNotNull { classSchedule ->
                            try {
                                val timePair = parseClassTimes( // Ensure parseClassTimes is accessible
                                    classSchedule.startTime,
                                    classSchedule.endTime,
                                    timeState // Pass the current time state
                                ) ?: return@mapNotNull null

                                val (startTime, endTime) = timePair
                                // Note: We no longer use generateConsistentColor here.
                                // Color will be assigned later from the palette.
                                val placeholderColor = Color.Gray // Temporary

                                // Extract group numbers (your logic here)
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
                                    color = placeholderColor, // Use placeholder
                                    room = classSchedule.room,
                                    group = groupInfo,
                                    groups = groupNumbers // Pass extracted groups
                                )
                            } catch (e: Exception) {
                                Log.e("ScheduleSection", "Error processing class: ${e.message}")
                                null
                            }
                        }
                        // Merge schedules that should be combined (ensure mergeSchedules is accessible)
                        var mergedSchedules = mergeSchedules(schedules).sortedBy { it.startTime }

                        // *** START: New Color Assignment Logic ***
                        val finalMergedSchedules = mutableListOf<Schedule>()
                        var colorIndex = 0
                        for (schedule in mergedSchedules) {
                            // Only assign palette colors to non-break schedules
                            val assignedColor = scheduleColorPalette[colorIndex % scheduleColorPalette.size]
                            finalMergedSchedules.add(schedule.copy(color = assignedColor)) // Update color
                            colorIndex++ // Increment index for the next non-break class
                        }
                        mergedSchedules = finalMergedSchedules // Update the list with assigned colors
                        // *** END: New Color Assignment Logic ***

                        // Add breaks between classes (ensure insertBreaksBetweenClasses is accessible)
                        // This happens *after* colors are assigned to classes. Breaks get their default color.
                        processedSchedules = insertBreaksBetweenClasses(mergedSchedules)

                        Log.d("ScheduleSection", "Final schedule count with breaks: ${processedSchedules.size}")

                    } else {
                        Log.d("ScheduleSection", "No schedules found for $today")
                        // processedSchedules remains emptyList()
                    }
                } else {
                    Log.d("ScheduleSection", "Timetable data is null")
                    // processedSchedules remains emptyList()
                }
                // Update the state with the processed schedule (or empty list)
                todaySchedule = processedSchedules
            } catch (e: Exception) {
                Log.e("ScheduleSection", "Error processing schedule data: ${e.message}")
                e.printStackTrace()
                // Ensure schedule is empty on error
                todaySchedule = emptyList()
            }
        }
    }

    // Improved auto-scrolling logic with delay (Keep your logic)
    LaunchedEffect(todaySchedule, timeState) {
        if (todaySchedule.isNotEmpty()) {
            delay(300) // Allow composition time
            val freshCurrentTime = timeState // Use the updated state time
            // Find target class logic remains the same
            val currentClass = todaySchedule.firstOrNull { !it.isBreak && it.isCurrentTime(freshCurrentTime) } // Prioritize non-breaks for scroll
            val nearestUpcoming = if (currentClass == null) {
                todaySchedule.filter { !it.isBreak && it.startTime.after(freshCurrentTime) } // Focus scroll on actual classes
                    .minByOrNull { it.startTime.time - freshCurrentTime.time }
            } else null

            val targetClass = currentClass ?: nearestUpcoming
            if (targetClass != null) {
                val index = todaySchedule.indexOf(targetClass)
                if (index >= 0) {
                    Log.d("AutoScroll", "Scrolling to index $index for target: ${targetClass.subject}")
                    try { listState.animateScrollToItem(index) } catch (e: Exception) { Log.e("AutoScroll", "Scroll error", e) }
                } else {
                    Log.d("AutoScroll", "Target class ${targetClass.subject} not found in final list")
                }
            } else {
                Log.d("AutoScroll", "No current or upcoming class found to scroll to.")
            }
        }
    }

    // --- UI (Remains the same structure as your provided code) ---
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Section header with icon and loading indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.schedule), // Use updated string if needed
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

        // Conditional Content Area with Crossfade
        Crossfade(
            targetState = Triple(isTimetableLoading, timetableError, todaySchedule.isEmpty()),
            label = "ScheduleContentFade"
        ) { (loading, error, isEmpty) ->
            when {
                // Show Error Card first if an error exists
                error != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = error ?: stringResource(R.string.error_loading_schedule),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Show Loading Placeholder while loading (and no error)
                loading -> {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp) // Match the 'No classes' card height
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(15.dp)) // Clip shape for consistency
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        // Optional: Add a Shimmer effect here later
                    }
                }

                // Show "No Classes Today" Card if empty, not loading, and no error
                isEmpty -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                            .padding(horizontal = 16.dp)
                            .animateContentSize(), // Animate size changes smoothly
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat look
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center // Center content vertically and horizontally
                        ) {
                            // Column to stack the two text lines
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally, // Center text within the column
                                verticalArrangement = Arrangement.Center // Center vertically too
                            ) {
                                Text(
                                    text = stringResource(R.string.no_classes_today), // "No classes scheduled today! 🎉"
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp)) // Small space between lines
                                Text(
                                    text = stringResource(R.string.your_day_is_clear), // "Your day is clear"
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), // Slightly less prominent
                                    style = MaterialTheme.typography.bodyMedium, // Slightly smaller style
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Show the Schedule LazyRow if not loading, no error, and schedule is not empty
                else -> {
                    Box(
                        modifier = Modifier
                            .height(110.dp) // Your defined height for the schedule row
                            .fillMaxWidth()
                    ) {
                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = todaySchedule,
                                key = { it.id } // Use stable keys
                            ) { schedule ->
                                val isCurrentClass = schedule.isCurrentTime(timeState) // Check against state time

                                // Apply scaling animation to current class (ensure animation states are defined)
                                val scale by animateFloatAsState(
                                    targetValue = if (isCurrentClass && !schedule.isBreak) 1.05f else 1f, // Only scale non-breaks
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "Class card scale"
                                )

                                // Call your existing card composable (ensure it's accessible)
                                // Pass the schedule with the *assigned* palette color
                                HomeScheduleCard(
                                    schedule = schedule,
                                    width = calculateWidth(schedule), // Ensure calculateWidth is accessible
                                    isCurrentClass = isCurrentClass,
                                    animationScale = scale
                                )
                            }
                        }

                        // Red line indicator (ensure RedLineIndicator & calculateRedLinePosition are accessible)
                        // Calculate position based on updated time state
                        val redLinePosition = calculateRedLinePosition(timeState)
                        if (redLinePosition > 0) {
                            RedLineIndicator(position = redLinePosition)
                        }
                    }
                }
            } // End When
        } // End Crossfade
    } // End Column

    // Force fetch timetable data when this component is first displayed (keep your logic)
    LaunchedEffect(Unit) {
        if (timetableData == null && !isTimetableLoading) {
            // Consider using the forceRefresh parameter if needed based on your logic
            viewModel.fetchTimetableData(forceRefresh = false) // Or true if needed on initial composition
        }
    }
}
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
    // Determine text color based on the background (palette color or break color)
    val textColor = if (schedule.isBreak)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        getReadableTextColor(schedule.color) // Use the assigned palette color

    // For dashed border - get the outline color from the theme within the composable
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    // Add subtle pulsing animation for current class (non-breaks only)
    val pulseAnimation = rememberInfiniteTransition(label = "PulseAnimation")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = if (isCurrentClass && !schedule.isBreak) 0.95f else 1f, // Pulse only for current non-break
        targetValue = if (isCurrentClass && !schedule.isBreak) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse Alpha"
    )

    // Add elevation for current class (only for non-break cards)
    val elevation = if (isCurrentClass && !schedule.isBreak) 6.dp else 2.dp

    if (schedule.isBreak) {
        // --- Break card styling (remains unchanged) ---
        Box(
            modifier = Modifier
                .width((width * animationScale).dp) // Apply scale even to breaks for consistency? Or keep 1f? Let's scale.
                .height((75 * animationScale).dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            DashedBorder(
                color = outlineColor,
                shape = RoundedCornerShape(12.dp),
                strokeWidth = 1.dp,
                dashWidth = 8.dp,
                dashGap = 4.dp,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = schedule.subject, // "Break"
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = schedule.timeRange,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
                val durationMinutes = schedule.duration / 60
                if (durationMinutes > 0) {
                    Text(
                        text = "$durationMinutes min",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    } else {
        // --- Regular class card using assigned palette color ---
        val cardColor = schedule.color // Use the color assigned from the palette
        val adjustedColor = cardColor.copy(alpha = 0.85f) // Slightly increase alpha compared to before

        Box(
            modifier = Modifier
                .width((width * animationScale).dp)
                .height((75 * animationScale).dp)
                .graphicsLayer {
                    alpha = pulseAlpha // Apply pulsing alpha
                }
                .shadow(
                    elevation = elevation, // Animated elevation
                    shape = RoundedCornerShape(12.dp),
                    spotColor = adjustedColor.copy(alpha = 0.4f) // Slightly softer shadow
                )
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp), // Slightly smaller radius than shadow
                colors = CardDefaults.cardColors(
                    containerColor = adjustedColor // Use the adjusted palette color
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat look inside the Box shadow
            ) {
                // Subtle gradient overlay for depth (kept from original)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
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
                            color = textColor, // Ensure readable text color
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        // Time range
                        Text(
                            text = schedule.timeRange,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.9f),
                            maxLines = 1
                        )

                        // Room and group info (kept from original)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (schedule.room != null) {
                                val roomText = if (schedule.room.contains(", ")) {
                                    val rooms = schedule.room.split(", ")
                                    if (rooms.size > 2) "${rooms[0]}, ${rooms[1]}..." else schedule.room
                                } else schedule.room
                                Text(
                                    text = roomText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.9f),
                                    maxLines = 1
                                )
                            }
                            if (schedule.room != null && schedule.getFormattedGroups() != null) {
                                Text("•", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.7f))
                            }
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

            // Current class indicator animation (kept from original)
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
}
// Create a separate composable for the dashed border
@Composable
fun DashedBorder(
    color: Color,
    shape: RoundedCornerShape,
    strokeWidth: Dp = 1.dp,
    dashWidth: Dp = 8.dp,
    dashGap: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val dashWidthPx = with(density) { dashWidth.toPx() }
    val dashGapPx = with(density) { dashGap.toPx() }

    Canvas(modifier = modifier) {
        // Create a path effect for dashed lines
        val pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashWidthPx, dashGapPx),
            phase = 0f
        )

        // Draw a rounded rectangle directly instead of using outline.path
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = pathEffect
            ),
            cornerRadius = CornerRadius(12f, 12f)
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class) // Necessary for Card defaults
@Composable
fun SubjectRowContent(
    subject: SubjectData,
    viewModel: AttendanceViewModel, // Keep viewModel if needed for DetailedAttendanceView or future actions
    hapticFeedback: HapticFeedback.HapticHandler,
    modifier: Modifier = Modifier
) {
    var showDetailedView by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    // Scale animation for interactive feedback on the row
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f, // Slightly less pronounced scale than card
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SubjectRow Scale"
    )

    // Calculate advice text based on attendance data
    val (adviceText, _) = getAttendanceAdvice(
        subject.overallPresent,
        subject.overallClasses
    )

    // Determine color for the percentage text based on attendance
    val attendanceColor = remember(subject.attendancePercentage) { // Memoize color calculation
        when {
            subject.attendancePercentage >= 85.0f -> Color(0xFF4CAF50) // Green
            subject.attendancePercentage >= 75.0f -> Color(0xFF8BC34A) // Light Green
            subject.attendancePercentage >= 65.0f -> Color(0xFFFFC107) // Yellow/Amber
            subject.attendancePercentage >= 60.0f -> Color(0xFFFF9800) // Orange
            else -> Color(0xFFE57373) // Soft Red
        }
    }

    // Row containing the textual and icon content
    Row(
        modifier = modifier // Apply external modifier
            .scale(rowScale) // Apply press animation scale
            .clickable {
                isPressed = true
                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                coroutineScope.launch {
                    delay(150) // Duration for press animation visibility
                    isPressed = false
                    showDetailedView = true // Trigger detail view after animation
                }
            }
            // Padding inside the Row, defining the content area
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Subject Name and Advice
        Column(
            modifier = Modifier.weight(1f), // Occupy available space
            verticalArrangement = Arrangement.spacedBy(4.dp) // Space between texts
        ) {
            Text(
                text = subject.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, // Use onSurface for content inside the card
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = adviceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // Use onSurfaceVariant
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right Row: Attendance Percentage and Arrow Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.padding(start = 12.dp) // Space from left content
        ) {
            Text(
                text = "${subject.attendancePercentage.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = attendanceColor, // Dynamic color
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            Spacer(Modifier.width(8.dp)) // Space before icon

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.details), // Accessibility
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp) // Consistent icon size
            )
        }
    } // End Row

    // Conditional display of the Detailed Attendance View (BottomSheet or Dialog)
    if (showDetailedView) {
        DetailedAttendanceView(
            subject = subject,
            onDismiss = { showDetailedView = false }
            // Pass viewModel if DetailedAttendanceView needs it
            // viewModel = viewModel
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
// Calculate width for schedule card based on duration
private fun calculateWidth(schedule: Schedule): Float {
    val baseWidth = 160f // Base width for a 1-hour class
    val minWidth = if (schedule.isBreak) 100f else 160f  // Minimum width - smaller for breaks

    val durationInHours = schedule.duration / 3600f

    // For breaks, use a more compressed scale
    val scaleFactor = if (schedule.isBreak) 0.7f else 1.0f

    return max(minWidth, baseWidth * durationInHours * scaleFactor)
}
// Define this function at file level in HomeScreen.kt (outside of any composable function)
private fun insertBreaksBetweenClasses(schedules: List<Schedule>): List<Schedule> {
    if (schedules.isEmpty() || schedules.size == 1) return schedules

    val sortedSchedules = schedules.sortedBy { it.startTime }
    val result = mutableListOf<Schedule>()

    for (i in 0 until sortedSchedules.size - 1) {
        val currentClass = sortedSchedules[i]
        val nextClass = sortedSchedules[i + 1]

        // Add the current class
        result.add(currentClass)

        // Check if there's a gap between current class and next class
        val gapInMinutes = (nextClass.startTime.time - currentClass.endTime.time) / (1000 * 60)

        // Only add a break if there's a significant gap (>= 10 minutes)
        if (gapInMinutes >= 10) {
            result.add(
                Schedule(
                    subject = "Break",
                    startTime = currentClass.endTime,
                    endTime = nextClass.startTime,
                    color = Color(0xFFE0E0E0), // Light grey
                    isBreak = true // This requires updating the Schedule data class
                )
            )
        }
    }

    // Add the last class
    result.add(sortedSchedules.last())

    return result
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
    profileData: ProfileData? = null,
    onProfileClick: (() -> Unit)? = null,
    hapticFeedback: HapticFeedback.HapticHandler? = null
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
            .height(60.dp)
    ) {
        // Blurred background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
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
            if (profileData != null) {
                ProfileButton(
                    profileData = profileData,
                    onClick = {
                        hapticFeedback?.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                        onProfileClick?.invoke()
                    }
                )
            }
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

