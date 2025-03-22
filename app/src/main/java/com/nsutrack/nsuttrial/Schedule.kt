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
    val group: String? = null
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