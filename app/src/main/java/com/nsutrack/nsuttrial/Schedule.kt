package com.nsutrack.nsuttrial

import androidx.compose.ui.graphics.Color
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val startTime: Date,
    val endTime: Date,
    val color: Color,
    val room: String? = null,
    val group: String? = null,
    val groups: List<String> = emptyList() // New field for multiple groups
) {
    // Get duration in seconds
    val duration: Long
        get() = (endTime.time - startTime.time) / 1000

    // Format time range (e.g., "9 AM - 10 AM")
    val timeRange: String
        get() {
            val formatter = SimpleDateFormat("h a", Locale.getDefault())

            val startPeriod = if (Calendar.getInstance().apply {
                    time = startTime
                }.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"

            val endPeriod = if (Calendar.getInstance().apply {
                    time = endTime
                }.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"

            return if (startPeriod == endPeriod) {
                val simpleFormatter = SimpleDateFormat("h", Locale.getDefault())
                "${simpleFormatter.format(startTime)} - ${simpleFormatter.format(endTime)} $startPeriod"
            } else {
                "${formatter.format(startTime)} - ${formatter.format(endTime)}"
            }
        }

    // Check if a given time is within this schedule's time range
    fun isCurrentTime(currentTime: Date): Boolean {
        return currentTime.time >= startTime.time && currentTime.time <= endTime.time
    }

    // Calculate how much of the schedule period has elapsed (0.0 to 1.0)
    fun elapsedTimeRatio(currentTime: Date): Double {
        if (!isCurrentTime(currentTime)) return 0.0
        val elapsed = currentTime.time - startTime.time
        return elapsed.toDouble() / (endTime.time - startTime.time)
    }

    // Get formatted group text
    fun getFormattedGroups(): String? {
        return when {
            // If we have multiple groups
            groups.isNotEmpty() -> "Group ${groups.joinToString(", ")}"
            // If we have only a single group
            group != null -> if (group.startsWith("Group", ignoreCase = true)) group else "Group $group"
            // No group info
            else -> null
        }
    }

    // Check if this schedule should be merged with another
    fun shouldMergeWith(other: Schedule): Boolean {
        // Check if this is the same subject with adjacent time slots
        return subject == other.subject &&
                // Either end time of this matches start time of other, or vice versa
                ((endTime.time == other.startTime.time) || (startTime.time == other.endTime.time)) &&
                // Rooms are the same, or at least one is null
                (room == other.room || room == null || other.room == null)
    }

    // Merge this schedule with another
    fun mergeWith(other: Schedule): Schedule {
        // Determine the earliest start time and latest end time
        val newStartTime = if (startTime.before(other.startTime)) startTime else other.startTime
        val newEndTime = if (endTime.after(other.endTime)) endTime else other.endTime

        // Merge group information
        val newGroups = (this.groups + other.groups).distinct().sorted()
        val singleGroups = listOfNotNull(this.group, other.group)
            .filter { !newGroups.contains(it) }
            .distinct()

        // Combine all groups
        val finalGroups = (newGroups + singleGroups).distinct().sorted()

        return Schedule(
            id = id, // Keep the ID of the first schedule
            subject = subject,
            startTime = newStartTime,
            endTime = newEndTime,
            color = color,
            room = room ?: other.room, // Take the non-null room if available
            groups = finalGroups
        )
    }

    companion object {
        // Utility function to create a Date with specified hour and minute
        fun createTimeForToday(hour: Int, minute: Int, baseDate: Date = Date()): Date {
            val calendar = Calendar.getInstance()
            calendar.time = baseDate
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
    }
}

// Timetable data classes to parse JSON response
data class TimetableData(
    val schedule: Map<String, List<ClassSchedule>>
) {
    data class ClassSchedule(
        @SerializedName("start_time")
        val startTime: String,

        @SerializedName("end_time")
        val endTime: String,

        val subject: String,
        val group: String? = null,
        val room: String? = null,

        @SerializedName("subject_name")
        val subjectName: String? = null
    )
}