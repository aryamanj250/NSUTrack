package com.nsutrack.nsuttrial

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
// Use fastGroupBy if available/needed
import com.nsutrack.nsuttrial.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log // Import Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import com.nsutrack.nsuttrial.SubjectData
import com.yourname.nsutrack.data.model.AttendanceRecord
import kotlin.math.ceil


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendanceDetailSheetContent(
    subject: SubjectData,
    hapticFeedback: HapticFeedback.HapticHandler,
    modifier: Modifier = Modifier // Apply modifier from ModalBottomSheet
) {
    val records = subject.records
    val currentCalendar = remember { Calendar.getInstance() } // Use current time for context

    // Filter and Sort records ONCE
    val FilteredRecords = remember(records) {
        records
            .mapNotNull { record -> parseRecordDate(record.date)?.let { Pair(record, it) } } // Parse date first
            .filter { (record, _) -> record.status.matches(Regex("[01+-]+|GH|H|CS|TL|MS|CR|MB")) } // Filter valid
            .sortedByDescending { (_, calendar) -> calendar.timeInMillis } // Sort by parsed date
            .map { (record, _) -> record } // Keep only the record
    }

    // Group records by "MMM yyyy"
    val groupedRecords = remember(FilteredRecords) {
       FilteredRecords.groupBy { record ->
            val cal = parseRecordDate(record.date) ?: currentCalendar // Fallback if parsing fails again
            SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time)
        }.toList()
        // No need to sort groups again as records were pre-sorted
    }

    // State for expanded months
    val expandedMonths = remember { mutableStateOf(setOf(groupedRecords.firstOrNull()?.first ?: "")) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            // Add bottom padding to ensure content scrolls above the navigation bar AND potential safe areas
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp) // Reduce top padding slightly
    ) {
        // --- Header Section ---
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // M3 BottomSheet Drag Handle visual
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 16.dp) // Adjust spacing
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )

                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                AttendanceHeaderStats(subject)
                Spacer(modifier = Modifier.height(20.dp))
                AttendanceAdviceCard(subject)
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // --- Month Sections ---
        groupedRecords.forEach { (monthYear, monthRecords) ->
            val monthKey = monthYear
            val isExpanded = expandedMonths.value.contains(monthKey)

            item {
                MonthHeader(
                    monthYear = monthYear,
                    recordCount = monthRecords.size,
                    isExpanded = isExpanded,
                    onToggle = {
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                        expandedMonths.value = if (isExpanded) {
                            expandedMonths.value - monthKey
                        } else {
                            expandedMonths.value + monthKey
                        }
                    },
                    modifier = Modifier.animateItemPlacement(tween(200)) // Animate header position
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Animated Visibility for Records in the Month
            items(
                items = if (isExpanded) monthRecords else emptyList(),
                key = { record -> record.date + record.status + UUID.randomUUID() }
            ) { record ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(150, delayMillis = 50)) +
                            expandVertically(animationSpec = tween(150, delayMillis = 50)),
                    exit = fadeOut(animationSpec = tween(100)) +
                            shrinkVertically(animationSpec = tween(100))
                ) {
                    Modifier
                        .padding(bottom = 8.dp)
                    RecordRow(
                        record = record,
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(150))
                    )
                }
            }
        }
    }
}

// Helper to parse record date string ("MMM-dd") into a Calendar object
private fun parseRecordDate(dateStr: String): Calendar? {
    return try {
        val format = SimpleDateFormat("MMM-dd", Locale.getDefault())
        format.isLenient = false
        val parsedDate = format.parse(dateStr)
        if (parsedDate != null) {
            val recordCal = Calendar.getInstance()
            recordCal.time = parsedDate
            val currentCal = Calendar.getInstance()
            val currentYear = currentCal.get(Calendar.YEAR)
            recordCal.set(Calendar.YEAR, currentYear) // Set current year

            // Adjust year if record month is later than current month
            if (recordCal.get(Calendar.MONTH) > currentCal.get(Calendar.MONTH)) {
                recordCal.set(Calendar.YEAR, currentYear - 1)
            }
            recordCal
        } else {
            Log.w("DateParse", "SimpleDateFormat returned null for: $dateStr")
            null
        }
    } catch (e: Exception) {
        Log.e("DateParse", "Could not parse date: $dateStr. Error: ${e.message}")
        null
    }
}

// --- Sub-Composables for the Sheet ---

@Composable
fun AttendanceHeaderStats(subject: SubjectData) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("Total", "${subject.overallClasses}", MaterialTheme.colorScheme.onSurfaceVariant)
        StatItem("Present", "${subject.overallPresent}", GoogleGreen)
        StatItem("Absent", "${subject.overallAbsent}", GoogleRed)
    }
}




@Composable
fun MonthHeader(
    monthYear: String, recordCount: Int, isExpanded: Boolean,
    onToggle: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthYear, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$recordCount ${if (recordCount == 1) "CLASS" else "CLASSES"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, tween(200))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.padding(start = 12.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RecordRow(record: AttendanceRecord, modifier: Modifier = Modifier) {
    val cal = parseRecordDate(record.date)
    val dateString = cal?.let { SimpleDateFormat("dd MMM", Locale.getDefault()).format(it.time) } ?: record.date // Fallback to original

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateString, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface
            )
            AttendanceStatusChip(status = record.status)
        }
    }
}
@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
fun AttendanceAdviceCard(subject: SubjectData) {
    // Define target percentage for attendance
    val targetPercentage = 75.0
    val currentPercentage = subject.attendancePercentage

    // Calculate advice text based on current attendance
    val adviceText = if (currentPercentage >= targetPercentage) {
        val classesCanSkip = ((subject.overallPresent * 100 / targetPercentage) - subject.overallClasses).toInt()
        "You can skip next $classesCanSkip classes"
    } else {
        val classesNeeded =
            ceil(((targetPercentage * subject.overallClasses - 100 * subject.overallPresent) / (100 - targetPercentage)))
                .toInt()
        "You need to attend next $classesNeeded classes"
    }

    // Set colors based on attendance status
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

    // Create the advice card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = bgColor)
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

@Composable
fun AttendanceStatusChip(status: String) {
    when (status) {
        // Present
        "1" -> StatusChip(
            text = "Present",
            backgroundColor = Color(0xFFE8F5E9), // Light Green
            textColor = Color(0xFF2E7D32) // Dark Green
        )

        // Absent
        "0" -> StatusChip(
            text = "Absent",
            backgroundColor = Color(0xFFFFEBEE), // Light Red
            textColor = Color(0xFFD32F2F) // Dark Red
        )

        // Combined status codes
        "0+1", "1+0" -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiniStatusChip(
                text = "A",
                backgroundColor = Color(0xFFFFEBEE),
                textColor = Color(0xFFD32F2F)
            )

            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            MiniStatusChip(
                text = "P",
                backgroundColor = Color(0xFFE8F5E9),
                textColor = Color(0xFF2E7D32)
            )
        }

        "0+0" -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiniStatusChip(
                text = "A",
                backgroundColor = Color(0xFFFFEBEE),
                textColor = Color(0xFFD32F2F)
            )

            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            MiniStatusChip(
                text = "A",
                backgroundColor = Color(0xFFFFEBEE),
                textColor = Color(0xFFD32F2F)
            )
        }

        "1+1" -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiniStatusChip(
                text = "P",
                backgroundColor = Color(0xFFE8F5E9),
                textColor = Color(0xFF2E7D32)
            )

            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            MiniStatusChip(
                text = "P",
                backgroundColor = Color(0xFFE8F5E9),
                textColor = Color(0xFF2E7D32)
            )
        }

        // Special cases
        "GH", "H" -> StatusChip(
            text = "Holiday",
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        "CS" -> StatusChip(
            text = "Suspended",
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        "TL" -> StatusChip(
            text = "Teacher Leave",
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        "MS" -> StatusChip(
            text = "Mid Semester",
            backgroundColor = Color(0xFFFFF8E1), // Light Amber
            textColor = Color(0xFFF57F17) // Dark Amber
        )

        "CR" -> StatusChip(
            text = "Rescheduled",
            backgroundColor = Color(0xFFE3F2FD), // Light Blue
            textColor = Color(0xFF1565C0) // Dark Blue
        )

        "MB" -> StatusChip(
            text = "Mass Bunk",
            backgroundColor = Color(0xFFE1BEE7), // Light Purple
            textColor = Color(0xFF7B1FA2) // Dark Purple
        )

        // Default case for any other status
        else -> StatusChip(
            text = status,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            textColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MiniStatusChip(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}