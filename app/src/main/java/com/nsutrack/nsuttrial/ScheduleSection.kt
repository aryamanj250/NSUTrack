package com.nsutrack.nsuttrial

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nsutrack.nsuttrial.ui.theme.getReadableTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@Composable
fun ScheduleSection(viewModel: AttendanceViewModel) {
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

    // Calculate current day and get schedule
    val calendar = Calendar.getInstance().apply { time = currentTime }
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val today = days[weekday - 1]

    // Process today's schedule
    val todaySchedule = processScheduleData(timetableData, viewModel, today, currentTime)

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
                fontWeight = FontWeight.Bold
            )

            if (isTimetableLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
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
                color = Color.Gray,
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
                        val cardWidth = calculateWidth(schedule)
                        ScheduleCard(
                            schedule = schedule,
                            width = cardWidth
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

    // Fetch timetable data if not already loaded
    LaunchedEffect(Unit) {
        if (timetableData == null && !isTimetableLoading) {
            viewModel.fetchTimetableData()
        }
    }
}

@Composable
private fun processScheduleData(
    timetableData: TimetableData?,
    viewModel: AttendanceViewModel,
    today: String,
    currentTime: Date
): List<Schedule> {
    // Return empty list if no data
    if (timetableData == null) {
        return getSampleSchedule(currentTime)
    }

    // Get today's class schedules
    val todayClassSchedules = timetableData.schedule[today]

    // Return sample schedule if no classes today
    if (todayClassSchedules == null) {
        Log.d("ScheduleSection", "No schedules found for $today")
        return getSampleSchedule(currentTime)
    }

    Log.d("ScheduleSection", "Found ${todayClassSchedules.size} classes for $today")

    // Process each class schedule
    return todayClassSchedules.mapNotNull { classSchedule ->
        processClassSchedule(classSchedule, viewModel, currentTime)
    }.sortedBy { it.startTime }
}

@Composable
private fun processClassSchedule(
    classSchedule: TimetableData.ClassSchedule,
    viewModel: AttendanceViewModel,
    currentTime: Date
): Schedule? {
    // Parse class times
    val timePair = parseClassTimesSafely(
        classSchedule.startTime,
        classSchedule.endTime,
        currentTime
    ) ?: return null

    val (startTime, endTime) = timePair

    // Get color for subject
    val color = viewModel.colorForSubject(classSchedule.subject)

    // Use subject name if available, otherwise code
    val subjectName = classSchedule.subjectName ?: classSchedule.subject

    return Schedule(
        subject = subjectName,
        startTime = startTime,
        endTime = endTime,
        color = color,
        room = classSchedule.room,
        group = classSchedule.group
    )
}

// Helper function to safely parse class times
private fun parseClassTimesSafely(
    startTimeStr: String,
    endTimeStr: String,
    baseDate: Date
): Pair<Date, Date>? {
    if (!startTimeStr.contains(":") || !endTimeStr.contains(":")) {
        return null
    }

    return try {
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

        Pair(startTime, endTime)
    } catch (e: Exception) {
        Log.e("ScheduleSection", "Error parsing times: $startTimeStr-$endTimeStr: ${e.message}")
        null
    }
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
    val calendar = Calendar.getInstance()

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

// Provide sample schedule data when no real data is available
private fun getSampleSchedule(baseDate: Date): List<Schedule> {
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

@Composable
fun ScheduleCard(schedule: Schedule, width: Float) {
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
fun RedLineIndicator(position: Float) {
    Row(
        modifier = Modifier
            .offset(x = position.dp)
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight(0.6f)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
        )
    }
}