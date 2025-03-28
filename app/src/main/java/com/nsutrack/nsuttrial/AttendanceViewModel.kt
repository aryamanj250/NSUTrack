package com.nsutrack.nsuttrial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nsutrack.data.model.AttendanceRecord
import com.yourname.nsutrack.data.model.LoginRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.google.gson.annotations.SerializedName
import com.nsutrack.nsuttrial.ui.theme.generateConsistentColor
import java.util.Calendar

class AttendanceViewModel : ViewModel() {
    // Tag for logging
    private val TAG = "AttendanceViewModel"

    // Initialize API Service
    private val apiService = RetrofitClient.apiService

    // State Flows
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId

    private val _isLoginInProgress = MutableStateFlow(false)
    val isLoginInProgress: StateFlow<Boolean> = _isLoginInProgress

    private val _isSessionInitialized = MutableStateFlow(false)
    val isSessionInitialized: StateFlow<Boolean> = _isSessionInitialized

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _subjectData = MutableStateFlow<List<SubjectData>>(emptyList())
    val subjectData: StateFlow<List<SubjectData>> = _subjectData

    private val _isAttendanceDataLoaded = MutableStateFlow(false)
    val isAttendanceDataLoaded: StateFlow<Boolean> = _isAttendanceDataLoaded

    // Profile data fields
    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading

    private val _profileData = MutableStateFlow<ProfileData?>(null)
    val profileData: StateFlow<ProfileData?> = _profileData

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError

    // Timetable data fields
    private val _isTimetableLoading = MutableStateFlow(false)
    val isTimetableLoading: StateFlow<Boolean> = _isTimetableLoading

    private val _timetableData = MutableStateFlow<TimetableData?>(null)
    val timetableData: StateFlow<TimetableData?> = _timetableData

    private val _timetableError = MutableStateFlow<String?>(null)
    val timetableError: StateFlow<String?> = _timetableError

    // Theme preference state
    private val _useDynamicColors = MutableStateFlow(true)
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors

    // Stored credentials for auto-refresh
    private val _storedUsername = MutableStateFlow<String?>(null)
    private val _storedPassword = MutableStateFlow<String?>(null)
    private var sharedPreferences: SharedPreferences? = null

    // Keep track of active jobs
    private var activeLoginJob: Job? = null
    private var activeAttendanceJob: Job? = null
    private var activeProfileJob: Job? = null
    private var activeTimetableJob: Job? = null

    // Initialize session when ViewModel is created
    init {
        Log.d(TAG, "ViewModel initialized")
        initializeSession()
    }

    // Initialize shared preferences for credential storage
    fun initializeSharedPreferences(context: Context) {
        sharedPreferences = context.getSharedPreferences("nsu_credentials", Context.MODE_PRIVATE)
        // Load stored credentials if available
        val username = sharedPreferences?.getString("username", null)
        val password = sharedPreferences?.getString("password", null)
        _storedUsername.value = username
        _storedPassword.value = password

        Log.d(TAG, "SharedPreferences initialized, credentials ${if (username != null) "found" else "not found"}")
    }

    // Set dynamic color preference
    fun setDynamicColorPreference(useDynamic: Boolean) {
        _useDynamicColors.value = useDynamic
    }

    // Step 1: Initialize session
    fun initializeSession() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""
            _isSessionInitialized.value = false

            try {
                Log.d(TAG, "Initializing session...")
                // Get session ID from server
                val sessionResponse = apiService.getSessionId()
                _sessionId.value = sessionResponse.session_id
                _isSessionInitialized.value = true
                Log.d(TAG, "Session initialized with ID: ${sessionResponse.session_id}")

            } catch (e: Exception) {
                Log.e(TAG, "Session initialization failed: ${e.message}")
                _errorMessage.value = "Failed to initialize session: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Step 4: Submit credentials
    fun login(username: String, password: String) {
        // Store credentials for future refreshes
        _storedUsername.value = username
        _storedPassword.value = password

        // Save to SharedPreferences
        sharedPreferences?.edit {
            putString("username", username)
            putString("password", password)
            apply()
        }

        Log.d(TAG, "Stored credentials for future refreshes")

        // Cancel previous login attempt if any
        activeLoginJob?.cancel()

        activeLoginJob = viewModelScope.launch {
            val currentSessionId = _sessionId.value
            if (currentSessionId == null) {
                Log.e(TAG, "Login failed: No session ID available")
                _errorMessage.value = "Session not initialized. Please try again."
                return@launch
            }

            Log.d(TAG, "Starting login with session ID: $currentSessionId")
            _isLoginInProgress.value = true
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                // Submit credentials to server
                val loginResponse = apiService.login(
                    LoginRequest(
                        session_id = currentSessionId,
                        uid = username,
                        pwd = password
                    )
                )

                if (!loginResponse.isSuccessful) {
                    throw IOException("Login failed with code: ${loginResponse.code()}")
                }

                Log.d(TAG, "Credentials submitted successfully, checking for errors...")

                // Check for credential errors
                var retryCount = 0
                var errorFound = false

                while (retryCount < 3) {
                    val currentId = _sessionId.value ?: break
                    val errorResponse = apiService.checkLoginErrors(currentId)

                    if (errorResponse.has("error")) {
                        // Handle invalid credentials
                        val errorMessage = errorResponse.get("error").asString
                        Log.e(TAG, "Login error: $errorMessage")

                        if (errorResponse.has("new_session_id")) {
                            val newSessionId = errorResponse.get("new_session_id").asString
                            Log.d(TAG, "Received new session ID: $newSessionId")
                            _sessionId.value = newSessionId
                            _isSessionInitialized.value = true
                        }

                        _errorMessage.value = errorMessage
                        errorFound = true
                        break
                    }

                    // If no errors found, we can proceed to fetch attendance
                    if (errorResponse.has("status") && errorResponse.get("status").asString == "no_errors") {
                        Log.d(TAG, "No login errors found, proceeding to fetch attendance data")
                        break
                    }

                    delay(300) // Brief delay before checking again
                    retryCount++
                }

                // If no errors, fetch attendance data
                if (!errorFound) {
                    Log.d(TAG, "Login successful, session ID before fetching data: ${_sessionId.value}")
                    _isLoggedIn.value = true
                    fetchAttendanceData()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login error: ${e.message}")
                _errorMessage.value = "Login error: ${e.message}"
                _isLoginInProgress.value = false
                _isLoading.value = false
            }
        }
    }

    // Fetch attendance data after successful login
    private suspend fun fetchAttendanceData() {
        if (activeAttendanceJob != null) {
            activeAttendanceJob?.cancelAndJoin()
        }

        activeAttendanceJob = viewModelScope.launch {
            try {
                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    Log.e(TAG, "Cannot fetch attendance: Session ID not available")
                    throw IOException("Session ID not available")
                }

                Log.d(TAG, "Fetching attendance data with session ID: $currentSessionId")

                // Get attendance data using SSE
                val attendanceResponse = apiService.getAttendanceData(currentSessionId)

                if (attendanceResponse.isSuccessful && attendanceResponse.body() != null) {
                    // Process the response
                    val responseBody = attendanceResponse.body()?.string() ?: ""
                    Log.d(TAG, "Received attendance data response of length: ${responseBody.length}")

                    // SSE responses are formatted as "data: {json}\n\n"
                    val dataPrefix = "data: "
                    if (responseBody.contains(dataPrefix)) {
                        val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                        Log.d(TAG, "Extracted JSON data of length: ${jsonData.length}")

                        // Parse the attendance data
                        parseAttendanceData(jsonData)

                        _isAttendanceDataLoaded.value = true
                        Log.d(TAG, "Attendance data loaded successfully")

                        // Also fetch profile data after successful attendance data
                        fetchProfileData()

                        // Also fetch timetable data
                        fetchTimetableData()
                    } else {
                        throw IOException("Invalid SSE format in response")
                    }
                } else {
                    Log.e(TAG, "Failed to get attendance data: ${attendanceResponse.code()}")
                    throw IOException("Failed to get attendance data: ${attendanceResponse.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching attendance data: ${e.message}")
                _errorMessage.value = "Error fetching attendance data: ${e.message}"
            } finally {
                _isLoginInProgress.value = false
                _isLoading.value = false
                activeAttendanceJob = null
            }
        }
    }

    // Parse attendance data
    private fun parseAttendanceData(jsonData: String) {
        try {
            Log.d(TAG, "Parsing attendance data")

            // First, check what kind of data we're getting
            val rawJsonString = jsonData.trim()

            // Log the first part of the JSON to debug
            val previewLength = minOf(100, rawJsonString.length)
            Log.d(TAG, "JSON data preview: ${rawJsonString.substring(0, previewLength)}...")

            val gson = Gson()

            if (rawJsonString.startsWith("{")) {
                // We received an object, not the expected array
                Log.d(TAG, "Received JSON object instead of array, trying AttendanceResponse format")

                val response = gson.fromJson(rawJsonString, AttendanceResponse::class.java)

                // Convert the API response to our internal model
                val subjects = response.subjects.map { apiSubject ->
                    // Convert ApiRecord to AttendanceRecord
                    val records = apiSubject.records.map { apiRecord ->
                        AttendanceRecord(
                            date = apiRecord.date,
                            status = apiRecord.status
                        )
                    }

                    // Calculate attendance percentage
                    val percentage = if (apiSubject.total_classes > 0) {
                        (apiSubject.present.toFloat() / apiSubject.total_classes) * 100
                    } else {
                        0f
                    }

                    SubjectData(
                        code = apiSubject.code,
                        name = apiSubject.name,
                        attendancePercentage = percentage,
                        overallClasses = apiSubject.total_classes,
                        overallPresent = apiSubject.present,
                        overallAbsent = apiSubject.absent,
                        records = records
                    )
                }

                Log.d(TAG, "Successfully parsed ${subjects.size} subjects from AttendanceResponse")
                _subjectData.value = subjects

            } else if (rawJsonString.startsWith("[")) {
                // Original parsing logic for array format
                Log.d(TAG, "Received JSON array, using original parsing logic")

                val typeToken = object : TypeToken<List<List<String>>>() {}.type
                val rawAttendanceData: List<List<String>> = gson.fromJson(jsonData, typeToken)

                // Variables to store important rows
                var subjectCodesRow: List<String>? = null
                var overallClassesRow: List<String>? = null
                var overallPresentRow: List<String>? = null
                var overallAbsentRow: List<String>? = null
                var subjectMappingRow: String = ""
                val dateWiseRecords = mutableListOf<Pair<String, List<String>>>()

                // First pass: identify important rows
                for (row in rawAttendanceData) {
                    if (row.isEmpty()) continue

                    when (row[0]) {
                        "Days" -> subjectCodesRow = row
                        "Overall Class" -> overallClassesRow = row
                        "Overall  Present" -> overallPresentRow = row
                        "Overall Absent" -> overallAbsentRow = row
                    }

                    // Save date-wise records (rows starting with dates)
                    if (row[0].contains("-") && !row[0].contains("FCCS")) {
                        val date = row[0]
                        val records = row.subList(1, row.size)
                        dateWiseRecords.add(Pair(date, records))
                    }
                }

                // Get subject mapping from last row
                if (rawAttendanceData.isNotEmpty() && rawAttendanceData.last().isNotEmpty()) {
                    subjectMappingRow = rawAttendanceData.last()[0]
                }

                // Extract subject codes
                val subjectCodes = subjectCodesRow?.drop(1) ?: emptyList()
                Log.d(TAG, "Found ${subjectCodes.size} subject codes")

                // Create subject map
                val subjectMap = mutableMapOf<String, String>()

                for (code in subjectCodes) {
                    val codePos = subjectMappingRow.indexOf("$code-")
                    if (codePos >= 0) {
                        val startPos = codePos + code.length + 1
                        var endPos = subjectMappingRow.length
                        for (nextCode in subjectCodes) {
                            if (nextCode != code) {
                                val nextCodePos = subjectMappingRow.indexOf(nextCode, startPos)
                                if (nextCodePos > 0 && nextCodePos < endPos) {
                                    endPos = nextCodePos
                                }
                            }
                        }

                        if (startPos < endPos) {
                            val name = subjectMappingRow.substring(startPos, endPos).trim()
                            subjectMap[code] = name
                        }
                    }
                }

                // Process data for each subject
                val subjects = mutableListOf<SubjectData>()

                for (i in subjectCodes.indices) {
                    val code = subjectCodes[i]
                    val overallClasses = overallClassesRow?.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    val overallPresent = overallPresentRow?.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    val overallAbsent = overallAbsentRow?.getOrNull(i + 1)?.toIntOrNull() ?: 0

                    // Get subject name
                    val name = subjectMap[code] ?: code

                    // Process attendance records
                    val records = mutableListOf<AttendanceRecord>()

                    for ((date, statuses) in dateWiseRecords) {
                        if (i < statuses.size) {
                            records.add(
                                AttendanceRecord(
                                    date = date,
                                    status = statuses[i]
                                )
                            )
                        }
                    }

                    // Calculate attendance percentage
                    val percentage = if (overallClasses > 0) {
                        (overallPresent.toFloat() / overallClasses) * 100
                    } else {
                        0f
                    }

                    subjects.add(
                        SubjectData(
                            code = code,
                            name = name,
                            attendancePercentage = percentage,
                            overallClasses = overallClasses,
                            overallPresent = overallPresent,
                            overallAbsent = overallAbsent,
                            records = records
                        )
                    )
                }

                Log.d(TAG, "Successfully parsed ${subjects.size} subjects")
                _subjectData.value = subjects
            } else {
                // Neither an object nor an array
                Log.e(TAG, "Unrecognized JSON format: neither an object nor an array")
                _errorMessage.value = "Unrecognized data format from server"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing attendance data: ${e.message}")
            // Log a snippet of the JSON to help with debugging
            try {
                val jsonSnippet = if (jsonData.length > 200) jsonData.substring(0, 200) + "..." else jsonData
                Log.e(TAG, "JSON snippet: $jsonSnippet")
            } catch (snippetError: Exception) {
                Log.e(TAG, "Could not log JSON snippet: ${snippetError.message}")
            }
            _errorMessage.value = "Error processing attendance data: ${e.message}"
            _subjectData.value = emptyList()
        }
    }

    // Fetch profile data
    fun fetchProfileData() {
        if (activeProfileJob != null) {
            activeProfileJob?.cancel()
        }

        activeProfileJob = viewModelScope.launch {
            _isProfileLoading.value = true
            _profileError.value = null

            try {
                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    Log.e(TAG, "Cannot fetch profile: Session ID not available")
                    throw IOException("Session ID not available")
                }

                Log.d(TAG, "Fetching profile data with session ID: $currentSessionId")

                val profileResponse = apiService.getProfileData(currentSessionId)

                if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    val responseBody = profileResponse.body()?.string() ?: ""
                    Log.d(TAG, "Received profile data response of length: ${responseBody.length}")

                    // Parse SSE response
                    val dataPrefix = "data: "
                    if (responseBody.contains(dataPrefix)) {
                        val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                        Log.d(TAG, "Extracted profile JSON data of length: ${jsonData.length}")

                        // Parse profile data
                        try {
                            val gson = Gson()

                            // Check if it's an error response
                            try {
                                val errorObj = gson.fromJson(jsonData, JsonObject::class.java)
                                if (errorObj.has("error")) {
                                    val errorMessage = errorObj.get("error").asString
                                    Log.e(TAG, "Profile error from server: $errorMessage")
                                    _profileError.value = errorMessage
                                    return@launch
                                }
                            } catch (e: Exception) {
                                // Not an error object, continue with normal parsing
                            }

                            // Parse the profile data
                            val profileData = gson.fromJson(jsonData, ProfileData::class.java)
                            Log.d(TAG, "Successfully parsed profile data for: ${profileData.studentName}")
                            _profileData.value = profileData
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing profile JSON: ${e.message}")
                            _profileError.value = "Failed to parse profile data: ${e.message}"
                        }
                    } else {
                        throw IOException("Invalid SSE format in profile response")
                    }
                } else {
                    Log.e(TAG, "Failed to get profile data: ${profileResponse.code()}")
                    throw IOException("Failed to get profile data: ${profileResponse.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching profile data: ${e.message}")
                _profileError.value = "Error fetching profile data: ${e.message}"
            } finally {
                _isProfileLoading.value = false
                activeProfileJob = null
            }
        }
    }

    // Fetch timetable data
    // Add this to AttendanceViewModel.kt to refresh timetable data more aggressively
    // Add these to your AttendanceViewModel.kt to enhance timetable management

    // Replace your existing fetchTimetableData method with this enhanced version
    fun fetchTimetableData(forceRefresh: Boolean = false) {
        if (activeTimetableJob != null) {
            activeTimetableJob?.cancel()
        }

        // Skip if already loaded and not forcing refresh
        if (!forceRefresh && _timetableData.value != null && !(_timetableData.value?.isStale() ?: true)) {
            Log.d(TAG, "Using cached timetable data (not stale)")
            return
        }

        activeTimetableJob = viewModelScope.launch {
            _isTimetableLoading.value = true
            _timetableError.value = null

            Log.d(TAG, "Starting timetable data fetch (force: $forceRefresh)")

            try {
                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    Log.e(TAG, "Cannot fetch timetable: Session ID not available")
                    throw IOException("Session ID not available")
                }

                Log.d(TAG, "Fetching timetable data with session ID: $currentSessionId")

                val timetableResponse = apiService.getTimetableData(currentSessionId)

                if (timetableResponse.isSuccessful && timetableResponse.body() != null) {
                    val responseBody = timetableResponse.body()?.string() ?: ""
                    Log.d(TAG, "Received timetable data response of length: ${responseBody.length}")

                    // Parse SSE response
                    val dataPrefix = "data: "
                    if (responseBody.contains(dataPrefix)) {
                        val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                        Log.d(TAG, "Extracted timetable JSON data of length: ${jsonData.length}")

                        // Parse timetable data
                        try {
                            val gson = Gson()

                            // Check if it's an error response
                            try {
                                val errorObj = gson.fromJson(jsonData, JsonObject::class.java)
                                if (errorObj.has("error")) {
                                    val errorMessage = errorObj.get("error").asString
                                    Log.e(TAG, "Timetable error from server: $errorMessage")
                                    _timetableError.value = errorMessage
                                    return@launch
                                }
                            } catch (e: Exception) {
                                // Not an error object, continue with normal parsing
                            }

                            // Parse the timetable data, ensuring we set the current timestamp
                            val baseData = gson.fromJson(jsonData, TimetableData::class.java)

                            // Create a new instance with current timestamp
                            val freshData = TimetableData(
                                schedule = baseData.schedule,
                                fetchTimestamp = System.currentTimeMillis()
                            )

                            // Log days available in schedule
                            val days = freshData.schedule.keys.joinToString(", ")
                            val currentDay = getCurrentDayAbbreviation()

                            Log.d(TAG, "Successfully parsed timetable with days: $days")
                            Log.d(TAG, "Current day is: $currentDay")

                            if (currentDay in freshData.schedule.keys) {
                                val classes = freshData.schedule[currentDay]?.size ?: 0
                                Log.d(TAG, "Found $classes classes for today ($currentDay)")
                            } else {
                                Log.w(TAG, "No schedule data for today ($currentDay)")
                            }

                            _timetableData.value = freshData
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing timetable JSON: ${e.message}")
                            e.printStackTrace()
                            _timetableError.value = "Failed to parse timetable data: ${e.message}"
                        }
                    } else {
                        throw IOException("Invalid SSE format in timetable response")
                    }
                } else {
                    Log.e(TAG, "Failed to get timetable data: ${timetableResponse.code()}")
                    throw IOException("Failed to get timetable data: ${timetableResponse.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching timetable data: ${e.message}")
                e.printStackTrace()
                _timetableError.value = "Error fetching timetable data: ${e.message}"
            } finally {
                _isTimetableLoading.value = false
                activeTimetableJob = null
            }
        }
    }

    // Helper method to get current day abbreviation
    private fun getCurrentDayAbbreviation(): String {
        val calendar = Calendar.getInstance()
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return days[dayOfWeek - 1]
    }

    // Add this method to force refresh all data including timetable
    fun forceRefreshAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                // First, get a new session
                initializeSession()

                // Wait for session initialization
                delay(500)

                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    throw IOException("Failed to initialize session")
                }

                val username = _storedUsername.value
                val password = _storedPassword.value

                if (username != null && password != null) {
                    val loginResponse = apiService.login(
                        LoginRequest(
                            session_id = currentSessionId,
                            uid = username,
                            pwd = password
                        )
                    )

                    if (!loginResponse.isSuccessful) {
                        throw IOException("Login failed with code: ${loginResponse.code()}")
                    }

                    var retryCount = 0
                    while (retryCount < 3) {
                        val sessionId = _sessionId.value ?: break
                        val errorResponse = apiService.checkLoginErrors(sessionId)

                        if (errorResponse.has("error")) {
                            val errorMessage = errorResponse.get("error").asString
                            throw IOException(errorMessage)
                        }

                        if (errorResponse.has("status") && errorResponse.get("status").asString == "no_errors") {
                            break
                        }

                        delay(300)
                        retryCount++
                    }

                    // Reset values to trigger fresh fetches
                    _timetableData.value = null
                    _profileData.value = null
                    _subjectData.value = emptyList()

                    // Mark as logged in and fetch fresh data
                    _isLoggedIn.value = true

                    // Fetch attendance first (this is required by your original logic)
                    fetchAttendanceData()

                    // Then fetch timetable and profile with force refresh
                    fetchTimetableData(forceRefresh = true)
                    fetchProfileData()
                } else {
                    throw IOException("No stored credentials")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Force refresh error: ${e.message}")
                _errorMessage.value = "Error refreshing data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    // Improved Pull-to-refresh functionality with credential resubmission
    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            Log.d(TAG, "Starting refresh data flow")

            try {
                // First, get a new session regardless of current state
                initializeSession()

                // Wait a short time for session initialization
                delay(500)

                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    Log.e(TAG, "Failed to initialize session for refresh")
                    _errorMessage.value = "Failed to initialize connection. Please try again."
                    _isLoading.value = false
                    return@launch
                }

                // Check if we have stored credentials
                val username = _storedUsername.value
                val password = _storedPassword.value

                if (username != null && password != null) {
                    Log.d(TAG, "Using stored credentials to refresh data")
                    // Submit credentials with new session
                    val loginResponse = apiService.login(
                        LoginRequest(
                            session_id = currentSessionId,
                            uid = username,
                            pwd = password
                        )
                    )

                    if (!loginResponse.isSuccessful) {
                        throw IOException("Login failed with code: ${loginResponse.code()}")
                    }

                    Log.d(TAG, "Credentials resubmitted, checking for errors...")

                    // Check for credential errors
                    var retryCount = 0
                    var errorFound = false

                    while (retryCount < 3) {
                        val currentId = _sessionId.value ?: break
                        val errorResponse = apiService.checkLoginErrors(currentId)

                        if (errorResponse.has("error")) {
                            // Handle invalid credentials
                            val errorMessage = errorResponse.get("error").asString
                            Log.e(TAG, "Login error during refresh: $errorMessage")

                            if (errorResponse.has("new_session_id")) {
                                val newSessionId = errorResponse.get("new_session_id").asString
                                Log.d(TAG, "Received new session ID: $newSessionId")
                                _sessionId.value = newSessionId
                                _isSessionInitialized.value = true
                            }

                            _errorMessage.value = errorMessage
                            errorFound = true
                            break
                        }

                        // If no errors found, proceed to fetch attendance
                        if (errorResponse.has("status") && errorResponse.get("status").asString == "no_errors") {
                            Log.d(TAG, "No login errors found, proceeding to fetch attendance data")
                            break
                        }

                        delay(300) // Brief delay before checking again
                        retryCount++
                    }

                    // If no errors, fetch attendance data
                    if (!errorFound) {
                        Log.d(TAG, "Login successful, fetching fresh data with session ID: ${_sessionId.value}")
                        _isLoggedIn.value = true
                        fetchAttendanceData()
                    }
                } else {
                    // Try to see if we can fetch data with the existing session
                    Log.d(TAG, "No stored credentials found, attempting with session only")
                    _errorMessage.value = "Please log in to refresh data"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during refresh: ${e.message}")
                _errorMessage.value = "Error refreshing data: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Helper function to assign consistent colors to subjects using Material 3 theme
    @Composable
    fun colorForSubject(subjectCode: String): Color {
        return generateConsistentColor(subjectCode, MaterialTheme.colorScheme)
    }

    // Get color for attendance percentage based on Material 3 theme
    @Composable
    fun getAttendanceStatusColor(percentage: Float): Color {
        val colorScheme = MaterialTheme.colorScheme
        return when {
            percentage >= 75.0f -> colorScheme.tertiary // Good attendance
            percentage >= 65.0f -> colorScheme.secondary // Warning attendance
            else -> colorScheme.error // Critical attendance
        }
    }

    // Cancel all pending requests
    fun cancelRequests() {
        Log.d(TAG, "Cancelling all pending requests")
        activeLoginJob?.cancel()
        activeAttendanceJob?.cancel()
        activeProfileJob?.cancel()
        activeTimetableJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel being cleared")
        cancelRequests()
    }
}

// Subject data model for UI
data class SubjectData(
    val code: String,
    val name: String,
    val attendancePercentage: Float,
    val overallClasses: Int,
    val overallPresent: Int,
    val overallAbsent: Int,
    val records: List<AttendanceRecord>
)

// Profile data model with proper field mapping
data class ProfileData(
    @SerializedName("Student ID")
    val studentID: String = "",

    @SerializedName("Student Name")
    val studentName: String = "",

    @SerializedName("DOB")
    val dob: String = "",

    @SerializedName("Gender")
    val gender: String = "",

    @SerializedName("Category")
    val category: String = "",

    @SerializedName("Admission")
    val admission: String = "",

    @SerializedName("Branch Name")
    val branchName: String = "",

    @SerializedName("Degree")
    val degree: String = "",

    @SerializedName("FT/PT")
    val ftpt: String = "",

    @SerializedName("Specialization")
    val specialization: String = "",

    @SerializedName("Section")
    val section: String = ""
)


