package com.nsutrack.nsuttrial.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Returns a color that is readable on top of the background color
 */
@Composable
fun getReadableTextColor(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
}

/**
 * Returns a status color based on the attendance percentage
 */
@Composable
fun getAttendanceStatusColor(percentage: Float): Color {
    val colorScheme = MaterialTheme.colorScheme
    return when {
        percentage >= 75.0f -> colorScheme.tertiary // Good attendance
        percentage >= 65.0f -> colorScheme.secondary // Warning attendance
        else -> colorScheme.error // Critical attendance
    }
}

/**
 * Generates a consistent color for a subject based on its code
 */
fun generateConsistentColor(subjectCode: String, colorScheme: ColorScheme): Color {
    // Create a stable hash from the subject code
    var hash = 0
    for (char in subjectCode) {
        hash = (hash * 31) + char.code
    }

    // Use the hash to select from a palette derived from the theme
    val colors = listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        Color(0xFF66BB6A), // Green
        Color(0xFFFFB74D), // Orange
        Color(0xFF9575CD), // Purple
        Color(0xFF4DD0E1)  // Cyan
    )

    return colors[Math.abs(hash) % colors.size]
}

/**
 * Creates a darkened version of a color for surfaces
 */
fun Color.darken(factor: Float = 0.2f): Color {
    return Color(
        red = max(0f, this.red - factor),
        green = max(0f, this.green - factor),
        blue = max(0f, this.blue - factor),
        alpha = this.alpha
    )
}

/**
 * Creates a lightened version of a color for highlights
 */
fun Color.lighten(factor: Float = 0.2f): Color {
    return Color(
        red = min(1f, this.red + factor),
        green = min(1f, this.green + factor),
        blue = min(1f, this.blue + factor),
        alpha = this.alpha
    )
}

/**
 * Get color for status indicator (attendance, present/absent marks)
 */
@Composable
fun getStatusColor(status: String): Color {
    val colorScheme = MaterialTheme.colorScheme

    return when (status) {
        "0" -> colorScheme.error                     // Absent
        "1" -> colorScheme.tertiary                  // Present
        "0+1", "1+0" -> colorScheme.secondary        // Partially present
        "0+0" -> colorScheme.error.darken(0.1f)      // Double absent
        "1+1" -> colorScheme.tertiary.lighten(0.1f)  // Double present
        "GH", "H" -> Color.Gray                      // Holiday
        "CS" -> Color.Gray                           // Suspended
        "TL" -> Color.Gray                           // Teacher Leave
        "MS" -> colorScheme.secondary                // Mid Sem
        "CR" -> Color(0xFFFF9800)                    // Rescheduled
        else -> colorScheme.primary                  // Default
    }
}

/**
 * Helper for attendance advice text and color
 */
@Composable
fun getAttendanceAdvice(overallPresent: Int, overallClasses: Int): Pair<String, Color> {
    val targetPercentage = 75.0
    val currentPercentage = if (overallClasses > 0) {
        (overallPresent.toDouble() / overallClasses) * 100
    } else {
        0.0
    }

    val text = if (currentPercentage >= targetPercentage) {
        val classesCanSkip = Math.floor((overallPresent * 100 / targetPercentage) - overallClasses).toInt()
        "You can skip next $classesCanSkip classes"
    } else {
        val classesNeeded = Math.ceil(((targetPercentage * overallClasses - 100 * overallPresent) / (100 - targetPercentage))).toInt()
        "You need to attend next $classesNeeded classes"
    }

    val color = if (currentPercentage >= targetPercentage) {
        MaterialTheme.colorScheme.tertiary
    } else if (currentPercentage >= 65.0) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    return Pair(text, color)
}