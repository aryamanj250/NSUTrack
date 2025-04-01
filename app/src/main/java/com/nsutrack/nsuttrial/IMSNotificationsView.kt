package com.nsutrack.nsuttrial

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class for IMS notifications
 */
data class IMSNotification(
    val date: String,
    val title: String,
    val link: String,
    val publishedBy: String,
    val department: String
)

/**
 * ViewModel for managing IMS notifications
 */
class IMSNotificationsViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _notifications = MutableStateFlow<List<IMSNotification>>(emptyList())
    val notifications: StateFlow<List<IMSNotification>> = _notifications.asStateFlow()

    private val _departments = MutableStateFlow<List<String>>(emptyList())
    val departments: StateFlow<List<String>> = _departments.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedDepartment = MutableStateFlow<String?>(null)
    val selectedDepartment: StateFlow<String?> = _selectedDepartment.asStateFlow()

    private val _filteredNotifications = MutableStateFlow<List<IMSNotification>>(emptyList())
    val filteredNotifications: StateFlow<List<IMSNotification>> = _filteredNotifications.asStateFlow()

    fun fetchNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = withContext(Dispatchers.IO) {
                    scrapeNotifications()
                }

                _notifications.value = result.first
                _departments.value = result.second

                // Set default department and filter
                if (_departments.value.isNotEmpty() && _selectedDepartment.value == null) {
                    _selectedDepartment.value = "All"
                    updateFilteredNotifications()
                }

            } catch (e: Exception) {
                _errorMessage.value = "Failed to load notifications: ${e.localizedMessage}"
                Log.e("IMSNotificationsVM", "Error fetching notifications", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSelectedDepartment(department: String) {
        viewModelScope.launch {
            _selectedDepartment.value = department
            updateFilteredNotifications()
        }
    }

    private fun updateFilteredNotifications() {
        val department = _selectedDepartment.value
        val allNotifications = _notifications.value

        _filteredNotifications.value = if (department == "All") {
            allNotifications
        } else {
            allNotifications.filter { it.department == department }
        }
    }

    /**
     * Scrape notifications from the IMS website
     * Returns a pair of (list of notifications, list of departments)
     */
    private suspend fun scrapeNotifications(): Pair<List<IMSNotification>, List<String>> = withContext(Dispatchers.IO) {
        val baseUrl = "https://www.imsnsit.org/imsnsit/"
        val notifications = mutableListOf<IMSNotification>()
        val departments = mutableSetOf<String>()

        try {
            // Create a session with custom user agent and headers
            val connection = Jsoup.connect("${baseUrl}notifications.php")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.119 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .followRedirects(true)
                .cookies(mutableMapOf()) // This will store cookies between requests

            val document = connection.get()

            // Extract departments from the select dropdown
            val departmentOptions = document.selectXpath("/html/body/form/table/tbody/tr[2]/td/div/select/option")
            departmentOptions.forEach { option ->
                departments.add(option.text())
            }

            // Add "All" option for showing all departments
            departments.add("All")

            // Extract notifications
            val tbody = document.selectXpath("/html/body/form/table/tbody").first()
            if (tbody != null) {
                // Get all rows
                val rows = tbody.select("tr")

                // Skip header rows and process notification rows
                // Starting from the 4th row (index 3), then every other row
                var i = 3
                while (i < rows.size - 4) { // Stopping at 4th from bottom
                    val row = rows[i]
                    val columns = row.select("td")

                    if (columns.size >= 2) {
                        // Extract date from first column
                        val dateColumn = columns[0]
                        val dateText = dateColumn.select("font").text().trim()

                        // Extract notification details from second column
                        val detailsColumn = columns[1]
                        val anchor = detailsColumn.select("a").first()
                        val detailFont = detailsColumn.select("font").first()

                        if (anchor != null && detailFont != null) {
                            val title = anchor.text().trim()
                            var link = anchor.attr("href").trim()

                            // Make link absolute if it's relative
                            if (!link.startsWith("http")) {
                                link = baseUrl + link
                            }

                            // Extract published by and department info
                            val detailText = detailFont.text().trim()
                            val publishedBy = extractPublishedBy(detailText)
                            val department = extractDepartment(detailText)

                            notifications.add(
                                IMSNotification(
                                    date = dateText,
                                    title = title,
                                    link = link,
                                    publishedBy = publishedBy,
                                    department = department
                                )
                            )
                        }
                    }

                    i += 2 // Skip every other row
                }
            }

        } catch (e: IOException) {
            Log.e("IMSNotificationsVM", "Error scraping notifications", e)
            throw e
        }

        // Sort notifications by date (most recent first)
        val sortedNotifications = notifications.sortedByDescending {
            parseNotificationDate(it.date)
        }

        // Return notifications and departments
        Pair(sortedNotifications, departments.toList().sorted())
    }

    /**
     * Extract the published by information from the detail text
     */
    private fun extractPublishedBy(detailText: String): String {
        val publishedByPattern = "Published By:\\s*(.+?)(?:\\s*Department:.*|$)".toRegex()
        val matchResult = publishedByPattern.find(detailText)
        return matchResult?.groupValues?.get(1)?.trim() ?: "Unknown"
    }

    /**
     * Extract the department information from the detail text
     */
    private fun extractDepartment(detailText: String): String {
        val departmentPattern = "Department:\\s*(.+)$".toRegex()
        val matchResult = departmentPattern.find(detailText)
        return matchResult?.groupValues?.get(1)?.trim() ?: "General"
    }

    /**
     * Parse date string to Date object for sorting
     */
    private fun parseNotificationDate(dateStr: String): Date {
        return try {
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
            dateFormat.parse(dateStr) ?: Date(0)
        } catch (e: Exception) {
            Date(0) // Default to epoch if parsing fails
        }
    }
}

/**
 * Composable for displaying IMS notifications
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesView(viewModel: IMSNotificationsViewModel = viewModel()) {
    val isLoading by viewModel.isLoading.collectAsState()
    val departments by viewModel.departments.collectAsState()
    val selectedDepartment by viewModel.selectedDepartment.collectAsState()
    val filteredNotifications by viewModel.filteredNotifications.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val uriHandler = LocalUriHandler.current
    val hapticFeedback = HapticFeedback.getHapticFeedback()

    // Fetch notifications when the screen is first displayed
    LaunchedEffect(key1 = Unit) {
        viewModel.fetchNotifications()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "IMS Notifications",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Department filter dropdown
        if (departments.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = selectedDepartment ?: "All",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Department") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    departments.forEach { department ->
                        DropdownMenuItem(
                            text = { Text(department) },
                            onClick = {
                                viewModel.setSelectedDepartment(department)
                                expanded = false
                                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                            }
                        )
                    }
                }
            }
        }

        // Error message
        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )

                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                        viewModel.fetchNotifications()
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.End)
                ) {
                    Text("Retry")
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Notifications list
        if (filteredNotifications.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredNotifications) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                            uriHandler.openUri(notification.link)
                        }
                    )
                }
            }
        } else if (!isLoading && errorMessage == null) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedDepartment == "All")
                        "No notifications available"
                    else
                        "No notifications for $selectedDepartment department",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card for displaying a single notification
 */
@Composable
fun NotificationCard(notification: IMSNotification, onClick: () -> Unit) {
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    var isPressed by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(),
        label = "Card Scale"
    )

    // This LaunchedEffect will observe the isPressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
            onClick()
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                isPressed = true
                // Don't call LaunchedEffect here, it's moved outside
            }
            .padding(vertical = 2.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        // Rest of your card content remains the same
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Date and department chip
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = notification.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                SuggestionChip(
                    onClick = { /* No action needed */ },
                    label = { Text(notification.department) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Published by
            Text(
                text = "Published by: ${notification.publishedBy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}