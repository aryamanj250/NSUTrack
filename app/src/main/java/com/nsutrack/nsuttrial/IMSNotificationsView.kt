package com.nsutrack.nsuttrial

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast

// Data class remains the same
data class IMSNotification(
    val date: String,
    val title: String,
    val link: String,
    val detailText: String
)

// ViewModel Updated: Added proper title case formatting for departments
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

    private val baseURL = "https://www.imsnsit.org/imsnsit/"

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

                if (_departments.value.isNotEmpty() && _selectedDepartment.value == null) {
                    _selectedDepartment.value = "All" // Default to "All"
                }
                // Initial filtering after fetch
                updateFilteredNotifications()

            } catch (e: Exception) {
                _errorMessage.value = "Failed to load notifications: ${e.localizedMessage}"
                Log.e("IMSNotificationsVM", "Error fetching notifications", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSelectedDepartment(department: String) {
        _selectedDepartment.value = department
        updateFilteredNotifications()
    }

    private fun updateFilteredNotifications() {
        val department = _selectedDepartment.value
        val allNotifications = _notifications.value

        _filteredNotifications.value = if (department == null || department == "All") {
            allNotifications
        } else {
            allNotifications.filter { notification ->
                notification.detailText.contains(department, ignoreCase = true)
            }
        }
    }

    private suspend fun scrapeNotifications(): Pair<List<IMSNotification>, List<String>> = withContext(Dispatchers.IO) {
        val notifications = mutableListOf<IMSNotification>()
        val departments = mutableSetOf<String>()

        // List of words that should stay lowercase
        val lowercaseExceptions = listOf("and", "for", "of", "to")

        try {
            val document = Jsoup.connect("${baseURL}notifications.php")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.119 Safari/537.36")
                .timeout(30000)
                .get()

            val tbody = document.selectXpath("/html/body/form/table/tbody").first()

            if (tbody != null) {
                val departmentOptions = tbody.selectXpath("tr[2]/td/div/select/option")
                departments.add("All") // Add "All" explicitly first

                for (option in departmentOptions) {
                    val rawDeptName = option.text().trim()
                    if (rawDeptName.isNotEmpty() && rawDeptName != "Select ...") {
                        // Process department name with proper capitalization
                        val capitalizedDeptName = rawDeptName
                            .lowercase()
                            .split(" ")
                            .map { word ->
                                if (lowercaseExceptions.contains(word.lowercase())) {
                                    word.lowercase()
                                } else {
                                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                }
                            }
                            .joinToString(" ")

                        departments.add(capitalizedDeptName)
                    }
                }

                val rows = tbody.select("tr")
                var i = 3
                while (i < rows.size - 4) {
                    try {
                        val row = rows[i]
                        val columns = row.select("td")
                        if (columns.size >= 2) {
                            val dateText = columns[0].select("font").first()?.text()?.trim() ?: ""
                            val detailsColumn = columns[1]
                            val anchor = detailsColumn.select("a").first()
                            if (anchor != null) {
                                val title = anchor.text().trim()
                                var link = anchor.attr("href").trim()
                                if (!link.startsWith("http")) {
                                    link = baseURL + link
                                }
                                val detailText = detailsColumn.selectXpath("./font").first()?.text()?.trim() ?: ""
                                if (title.isNotEmpty()) {
                                    notifications.add(
                                        IMSNotification(
                                            date = dateText,
                                            title = title,
                                            link = link,
                                            detailText = detailText
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("IMSNotificationsVM", "Error processing row at index $i: ${e.message}")
                    }
                    i += 2
                }
            }
        } catch (e: IOException) {
            Log.e("IMSNotificationsVM", "Error scraping notifications", e)
            throw e
        }

        val sortedNotifications = notifications.sortedByDescending {
            parseNotificationDate(it.date)
        }

        // Return sorted notifications and sorted departments (with "All" likely first)
        Pair(sortedNotifications, departments.toList().sortedWith(compareBy { it != "All" })) // Keep "All" first
    }

    private fun parseNotificationDate(dateStr: String): Date {
        return try {
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
            dateFormat.parse(dateStr) ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }

    /**
     * Open a link using the WebViewActivity with proper handling for all link types.
     */
    fun openLink(link: String, context: Context, title: String = "Notice Details") {
        try {
            Log.d("IMSNotificationsVM", "Opening link: $link")

            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(WebViewActivity.EXTRA_URL, link)
                putExtra(WebViewActivity.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to open link: ${e.localizedMessage}"
            Log.e("IMSNotificationsVM", "Error creating intent for WebViewActivity", e)
            Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show()
        }
    }
}

// Composable updated to fix the padding issues
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IMSNoticesView(viewModel: IMSNotificationsViewModel = viewModel()) {
    val isLoading by viewModel.isLoading.collectAsState()
    val departments by viewModel.departments.collectAsState()
    val selectedDepartment by viewModel.selectedDepartment.collectAsState()
    val filteredNotifications by viewModel.filteredNotifications.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        viewModel.fetchNotifications()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 8.dp, bottom = 0.dp) // Removed bottom padding
    ) {
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
                    label = { Text("Filter by Department") }, // More descriptive label
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
                            }
                        )
                    }
                }
            }
        }

        // Error message card
        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Error loading notices:", // Prefix error message
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.fetchNotifications() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp), // Add vertical padding
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Notifications list
        if (!isLoading && errorMessage == null) { // Only show list/empty state when not loading and no error
            if (filteredNotifications.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 0.dp), // Zero bottom padding
                    modifier = Modifier.fillMaxSize() // Use remaining space
                ) {
                    items(filteredNotifications, key = { it.link }) { notification -> // Add a key for better performance
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Pass notification title to WebViewActivity
                                    viewModel.openLink(notification.link, context, notification.title)
                                }
                                .padding(vertical = 2.dp) // Small vertical padding between cards
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = notification.date,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray, // Consider MaterialTheme.colorScheme.outline
                                    modifier = Modifier.padding(bottom = 6.dp) // Adjusted padding
                                )
                                Text(
                                    text = notification.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = notification.detailText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state (when not loading and no notifications match filter)
                Box(
                    modifier = Modifier
                        .fillMaxSize(), // Take remaining space
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedDepartment == "All" || selectedDepartment == null)
                            "No notifications found."
                        else
                            "No notifications found for ${selectedDepartment}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}