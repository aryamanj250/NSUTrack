package com.nsutrack.nsuttrial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourname.nsutrack.data.model.AttendanceRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAttendanceView(
    subject: SubjectData,
    onDismiss: () -> Unit
) {
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val records = subject.records

    // Group attendance records by month
    val groupedRecords = records.groupBy {
        it.date.split("-").firstOrNull() ?: ""
    }.toList()

    // Sort groups by month (assuming format is "MMM-DD")
    val sortedGroups = groupedRecords.sortedByDescending { it.first }

    // Keep track of expanded month sections
    val expandedMonths = remember { mutableStateOf(setOf(sortedGroups.firstOrNull()?.first ?: "")) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with subject info
                AttendanceHeader(subject)

                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Month-wise sections
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(sortedGroups) { (month, monthRecords) ->
                        MonthSection(
                            month = month,
                            records = monthRecords.sortedByDescending {
                                it.date.split("-").getOrNull(1)?.toIntOrNull() ?: 0
                            },
                            isExpanded = expandedMonths.value.contains(month),
                            onToggle = {
                                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                expandedMonths.value = if (expandedMonths.value.contains(month)) {
                                    expandedMonths.value - month
                                } else {
                                    expandedMonths.value + month
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }

            // Floating close button
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                        onDismiss()
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
fun AttendanceHeader(subject: SubjectData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = subject.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${subject.overallPresent} out of ${subject.overallClasses} classes attended",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Attendance advice card
        val targetPercentage = 75.0
        val currentPercentage = subject.attendancePercentage

        val adviceText = if (currentPercentage >= targetPercentage) {
            val classesCanSkip = ((subject.overallPresent * 100 / targetPercentage) - subject.overallClasses).toInt()
            "You can skip next $classesCanSkip classes"
        } else {
            val classesNeeded = Math.ceil(((targetPercentage * subject.overallClasses - 100 * subject.overallPresent) / (100 - targetPercentage))).toInt()
            "You need to attend next $classesNeeded classes"
        }

        val bgColor = if (currentPercentage >= targetPercentage) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else if (currentPercentage >= 65.0) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }

        val textColor = if (currentPercentage >= targetPercentage) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else if (currentPercentage >= 65.0) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = bgColor,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Text(
                text = adviceText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontWeight = FontWeight.Medium
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
    Column(modifier = Modifier.fillMaxWidth()) {
        // Month header with ripple effect
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
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
                            .padding(start = 8.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Records
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)
            ) {
                records.forEach { record ->
                    RecordRow(record)
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecordRow(record: AttendanceRecord) {
    val day = record.date.split("-").getOrNull(1) ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Format the status
        AttendanceStatusView(status = record.status)
    }
}

@Composable
fun AttendanceStatusView(status: String) {
    when (status) {
        // Single status codes
        "0" -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        "1" -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "P",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Combined status codes
        "0+1", "1+0" -> RowWithSpacing {
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "P",
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
        "0+0" -> RowWithSpacing {
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }
        "1+1" -> RowWithSpacing {
            Text(
                text = "P",
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "P",
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Special cases
        "GH", "H" -> Text(
            text = "Holiday",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        "CS" -> Text(
            text = "Suspended",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        "TL" -> Text(
            text = "Teacher Leave",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        "MS" -> Text(
            text = "Mid Sem",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )
        "CR" -> Text(
            text = "Rescheduled",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )

        // Default case for any other status
        else -> Text(
            text = status,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RowWithSpacing(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}