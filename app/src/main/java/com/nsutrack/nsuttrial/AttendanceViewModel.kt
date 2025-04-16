package com.nsutrack.nsuttrial

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.nsutrack.nsuttrial.ui.theme.generateConsistentColor // Assuming this exists
import com.yourname.nsutrack.data.model.AttendanceRecord // Assuming this exists
import com.yourname.nsutrack.data.model.LoginRequest // Assuming this exists
import kotlinx.coroutines.Job
import kotlinx.coroutines.async // Added import
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope // Added import
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody // Keep if apiService returns this directly
import retrofit2.HttpException // Keep for error handling
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.io.IOException
import java.util.Calendar
import kotlin.math.ceil // Keep if used in calculation helpers
import kotlin.math.min // Added for JSON preview

// Assume RetrofitClient and ApiService are correctly defined elsewhere
// object RetrofitClient { val apiService: ApiService = ... }
// interface ApiService { ... }

class AttendanceViewModel : ViewModel() {
    private val TAG = "AttendanceViewModel"
    private val apiService = RetrofitClient.apiService // Ensure this is initialized

    // State Flows for UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow() // Main loading state for pull-refresh

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _isLoginInProgress = MutableStateFlow(false)
    val isLoginInProgress: StateFlow<Boolean> = _isLoginInProgress.asStateFlow()

    private val _isSessionInitialized = MutableStateFlow(false)
    val isSessionInitialized: StateFlow<Boolean> = _isSessionInitialized.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _subjectData = MutableStateFlow<List<SubjectData>>(emptyList())
    val subjectData: StateFlow<List<SubjectData>> = _subjectData.asStateFlow()

    private val _isAttendanceDataLoaded = MutableStateFlow(false)
    val isAttendanceDataLoaded: StateFlow<Boolean> = _isAttendanceDataLoaded.asStateFlow()

    // Profile specific states
    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()
    private val _profileData = MutableStateFlow<ProfileData?>(null)
    val profileData: StateFlow<ProfileData?> = _profileData.asStateFlow()
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    // Timetable specific states
    private val _isTimetableLoading = MutableStateFlow(false)
    val isTimetableLoading: StateFlow<Boolean> = _isTimetableLoading.asStateFlow()
    private val _timetableData = MutableStateFlow<TimetableData?>(null)
    val timetableData: StateFlow<TimetableData?> = _timetableData.asStateFlow()
    private val _timetableError = MutableStateFlow<String?>(null)
    val timetableError: StateFlow<String?> = _timetableError.asStateFlow()

    // Theme preference state (if used)
    private val _useDynamicColors = MutableStateFlow(true)
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors.asStateFlow()

    // Logout completion signal (if needed)
    private val _logoutCompleted = MutableStateFlow(false)
    val logoutCompleted: StateFlow<Boolean> = _logoutCompleted.asStateFlow()

    // Stored credentials
    private val _storedUsername = MutableStateFlow<String?>(null)
    private val _storedPassword = MutableStateFlow<String?>(null)
    private var sharedPreferences: SharedPreferences? = null

    // Active job tracking
    private var activeLoginJob: Job? = null
    private var activeAttendanceJob: Job? = null
    private var activeProfileJob: Job? = null
    private var activeTimetableJob: Job? = null

    init {
        Log.d(TAG, "ViewModel initialized")
        // Avoid auto-initializing session here; let SharedPreferences init or refresh handle it.
    }

    fun initializeSharedPreferences(context: Context) {
        if (sharedPreferences == null) { // Initialize only once
            sharedPreferences = context.getSharedPreferences("nsu_credentials", Context.MODE_PRIVATE)
            val username = sharedPreferences?.getString("username", null)
            val password = sharedPreferences?.getString("password", null)
            _storedUsername.value = username
            _storedPassword.value = password
            Log.d(TAG, "SharedPreferences initialized, credentials ${if (username != null) "found" else "not found"}")

            // If credentials exist, try to initialize session silently on startup
            if (hasStoredCredentials() && _sessionId.value == null) {
                Log.d(TAG, "Credentials found on init, initializing session.")
                initializeSession()
            }
        }
    }

    // --- START: NEW Pull-to-Refresh Function ---
    fun performPullToRefresh() {
        viewModelScope.launch {
            Log.i(TAG, "Pull-to-Refresh triggered.")
            _isLoading.value = true // Show pull-refresh indicator immediately
            _errorMessage.value = "" // Clear previous errors
            _profileError.value = null // Clear specific errors
            _timetableError.value = null // Clear specific errors

            try {
                // 1. Cancel any ongoing network requests
                Log.d(TAG, "[Refresh] Cancelling ongoing requests...")
                cancelRequests()
                delay(100) // Short delay to ensure cancellation propagates

                // 2. Initialize a new session
                Log.d(TAG, "[Refresh] Initializing new session...")
                try {
                    val sessionResponse = apiService.getSessionId()
                    _sessionId.value = sessionResponse.session_id
                    _isSessionInitialized.value = true
                    Log.d(TAG, "[Refresh] New session initialized with ID: ${sessionResponse.session_id}")
                } catch (sessionError: Exception) {
                    Log.e(TAG, "[Refresh] Session initialization failed: ${sessionError.message}")
                    _errorMessage.value = "Connection error during refresh. Please try again."
                    _isLoading.value = false // Hide indicator on failure
                    return@launch // Stop refresh process
                }

                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    Log.e(TAG, "[Refresh] Failed: No session ID available after initialization.")
                    _errorMessage.value = "Failed to establish session for refresh."
                    _isLoading.value = false // Hide indicator
                    return@launch // Stop refresh process
                }

                // 3. Retrieve saved credentials
                Log.d(TAG, "[Refresh] Retrieving stored credentials...")
                val credentials = getStoredCredentials()
                if (credentials == null) {
                    Log.w(TAG, "[Refresh] No stored credentials found. Cannot re-authenticate.")
                    _errorMessage.value = "Please log in to refresh data."
                    _isLoggedIn.value = false
                    _isLoading.value = false // Hide indicator
                    clearAllData() // Clear potentially stale data
                    return@launch // Stop refresh process
                }
                val (username, password) = credentials

                // 4. Submit credentials to the backend
                Log.d(TAG, "[Refresh] Submitting credentials with session ID: $currentSessionId...")
                try {
                    val loginResponse = apiService.login(
                        LoginRequest(
                            session_id = currentSessionId,
                            uid = username,
                            pwd = password
                        )
                    )
                    if (!loginResponse.isSuccessful) {
                        throw IOException("Re-authentication failed (HTTP ${loginResponse.code()})")
                    }
                    Log.d(TAG, "[Refresh] Credentials submitted successfully.")

                    // Check for application-level login errors
                    var retryCount = 0
                    var loginErrorFound = false
                    
                    Log.d(TAG, "test log: [Refresh] Starting error check loop")
                    while (retryCount < 3 && !loginErrorFound) {
                        Log.d(TAG, "test log: [Refresh] Error check iteration ${retryCount + 1}/3")
                        delay(300)
                        val checkSessionId = _sessionId.value
                        if (checkSessionId == null) {
                            Log.e(TAG, "test log: [Refresh] Session ID became null during error check")
                            break // Use current ID
                        }
                        
                        Log.d(TAG, "test log: [Refresh] Checking login errors with session ID: $checkSessionId")
                        val errorResponse = apiService.checkLoginErrors(checkSessionId)
                        Log.d(TAG, "test log: [Refresh] Error response JSON: $errorResponse")
                        
                        if (errorResponse.has("error")) {
                            val serverErrorMessage = errorResponse.get("error").asString
                            Log.e(TAG, "test log: [Refresh] Login error found: $serverErrorMessage")
                            _errorMessage.value = serverErrorMessage
                            loginErrorFound = true
                            if (errorResponse.has("new_session_id")) {
                                val newSessionId = errorResponse.get("new_session_id").asString
                                Log.d(TAG, "test log: [Refresh] Received new session ID during error check: $newSessionId")
                                _sessionId.value = newSessionId
                            }
                        } else if (errorResponse.has("status") && errorResponse.get("status").asString == "no_errors") {
                            Log.d(TAG, "test log: [Refresh] No login errors found, login confirmed successful")
                            _isLoggedIn.value = true
                            break
                        } else {
                            Log.w(TAG, "test log: [Refresh] Unexpected error response format, neither 'error' nor 'status':'no_errors' found")
                        }
                        retryCount++
                        Log.d(TAG, "test log: [Refresh] Moving to next error check iteration, retryCount=$retryCount")
                    }
                    
                    Log.d(TAG, "test log: [Refresh] Error check loop complete. loginErrorFound=$loginErrorFound, isLoggedIn=${_isLoggedIn.value}")

                    if (loginErrorFound || !_isLoggedIn.value) { // Check if error found OR login wasn't confirmed
                        if (!loginErrorFound) {
                            Log.e(TAG, "test log: [Refresh] No explicit error found but login wasn't confirmed either")
                            _errorMessage.value = "Login verification failed."
                        }
                        _isLoggedIn.value = false
                        _isLoading.value = false
                        clearAllData()
                        Log.d(TAG, "test log: [Refresh] Login failed, cleared all data")
                        return@launch
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "[Refresh] Error during credential submission: ${e.message}")
                    _errorMessage.value = "Authentication failed: ${e.message?.take(100)}" // Limit error message length
                    _isLoggedIn.value = false
                    _isLoading.value = false
                    return@launch
                }

                // --- At this point, re-authentication is successful ---

                // 5. Wait for attendance data to load (Fetch sequentially first)
                Log.d(TAG, "[Refresh] Fetching attendance data...")
                try {
                    val attendanceSessionId = _sessionId.value ?: throw IOException("Session ID became null before attendance fetch")
                    val attendanceResponse = apiService.getAttendanceData(attendanceSessionId)
                    if (attendanceResponse.isSuccessful && attendanceResponse.body() != null) {
                        val responseBody = attendanceResponse.body()?.string() ?: ""
                        val dataPrefix = "data: "
                        if (responseBody.contains(dataPrefix)) {
                            val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                            parseAttendanceData(jsonData)
                            _isAttendanceDataLoaded.value = true
                            Log.d(TAG, "[Refresh] Attendance data fetched successfully.")
                        } else {
                            throw IOException("Invalid SSE format for attendance")
                        }
                    } else {
                        throw IOException("Failed to get attendance (HTTP ${attendanceResponse.code()})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[Refresh] Error fetching/parsing attendance: ${e.message}")
                    _errorMessage.value = "Failed to load attendance: ${e.message?.take(100)}"
                    _isAttendanceDataLoaded.value = false
                    // Decide whether to stop or continue to profile/timetable
                    // Let's continue for now to potentially get other data
                }

                // 6. Load profile and timetable data in parallel
                Log.d(TAG, "[Refresh] Fetching profile and timetable data in parallel...")
                coroutineScope { // Use a coroutineScope to manage parallel jobs
                    val profileJob = async { fetchProfileDataInternal() } // Launch async
                    val timetableJob = async { fetchTimetableDataInternal(forceRefresh = true) } // Launch async

                    profileJob.await() // Wait for profile job to complete (or throw exception)
                    timetableJob.await() // Wait for timetable job to complete (or throw exception)
                }
                Log.d(TAG, "[Refresh] Profile and timetable fetches finished.")

            } catch (e: Exception) {
                // Catch any unexpected errors during the refresh flow
                Log.e(TAG, "[Refresh] Unexpected error: ${e.message}", e)
                _errorMessage.value = "An unexpected error occurred."
            } finally {
                // IMPORTANT: Ensure loading indicator is hidden
                Log.i(TAG, "[Refresh] Refresh process finished. Hiding indicator.")
                _isLoading.value = false
            }
        }
    }
    // --- END: NEW Pull-to-Refresh Function ---

    // --- Existing Functions (Modified slightly for internal use/clarity) ---

    fun setDynamicColorPreference(useDynamic: Boolean) {
        _useDynamicColors.value = useDynamic
    }

    // Initializes session, typically called on startup if credentials exist
    fun initializeSession() {
        if (_isSessionInitialized.value || activeLoginJob?.isActive == true) return // Avoid redundant calls

        viewModelScope.launch {
            _isSessionInitialized.value = false // Reset flag during init attempt
            try {
                Log.d(TAG, "Initializing session...")
                val sessionResponse = apiService.getSessionId()
                _sessionId.value = sessionResponse.session_id
                _isSessionInitialized.value = true
                Log.d(TAG, "Session initialized with ID: ${sessionResponse.session_id}")
            } catch (e: Exception) {
                Log.e(TAG, "Session initialization failed: ${e.message}")
                _errorMessage.value = "Failed to connect: ${e.message?.take(100)}" // Show connection error
                _isSessionInitialized.value = false
                _sessionId.value = null
            }
        }
    }

    fun login(username: String, password: String) {
        if (_isLoginInProgress.value) return // Prevent multiple login attempts

        activeLoginJob?.cancel() // Cancel previous login attempt
        activeLoginJob = viewModelScope.launch {
            _isLoginInProgress.value = true
            _isLoading.value = true // Show general loading during login
            _errorMessage.value = ""
            _profileError.value = null
            _timetableError.value = null

            Log.d(TAG, "test log: Login function started with username=$username and password length=${password.length}")

            try {
                // Ensure session is initialized
                if (!_isSessionInitialized.value || _sessionId.value == null) {
                    Log.d(TAG, "test log: Session not ready for login, initializing...")
                    initializeSession()
                    delay(1500) // Wait for session init
                    if (!_isSessionInitialized.value || _sessionId.value == null) {
                        Log.e(TAG, "test log: Session initialization failed after wait")
                        throw IOException("Connection error. Please try again.")
                    }
                }
                val currentSessionId = _sessionId.value!!

                Log.d(TAG, "test log: Starting login with session ID: $currentSessionId")
                val loginResponse = apiService.login(
                    LoginRequest(session_id = currentSessionId, uid = username, pwd = password)
                )
                
                Log.d(TAG, "test log: Login HTTP response code: ${loginResponse.code()}")
                Log.d(TAG, "test log: Login HTTP response is successful: ${loginResponse.isSuccessful}")
                
                if (!loginResponse.isSuccessful) {
                    Log.e(TAG, "test log: Login HTTP request failed with code ${loginResponse.code()}")
                    throw IOException("Login failed (HTTP ${loginResponse.code()})")
                }

                // Check for errors
                var retryCount = 0
                var errorFound = false
                var loggedInConfirmed = false
                
                Log.d(TAG, "test log: Starting error check loop")
                while (retryCount < 3) {
                    Log.d(TAG, "test log: Error check iteration ${retryCount + 1}/3")
                    delay(300)
                    val checkSessionId = _sessionId.value
                    
                    if (checkSessionId == null) {
                        Log.e(TAG, "test log: Session ID became null during error check")
                        break
                    }
                    
                    Log.d(TAG, "test log: Checking login errors with session ID: $checkSessionId")
                    val errorResponse = apiService.checkLoginErrors(checkSessionId)
                    Log.d(TAG, "test log: Error response JSON: $errorResponse")
                    
                    if (errorResponse.has("error")) {
                        val serverErrorMessage = errorResponse.get("error").asString
                        Log.e(TAG, "test log: Login error found: $serverErrorMessage")
                        _errorMessage.value = serverErrorMessage
                        errorFound = true
                        
                        if (errorResponse.has("new_session_id")) {
                            val newSessionId = errorResponse.get("new_session_id").asString
                            Log.d(TAG, "test log: Received new session ID during error check: $newSessionId")
                            _sessionId.value = newSessionId
                        }
                        break // Treat server error as final
                    } else if (errorResponse.has("status") && errorResponse.get("status").asString == "no_errors") {
                        Log.d(TAG, "test log: No login errors found, login confirmed successful")
                        loggedInConfirmed = true
                        _isLoggedIn.value = true
                        break
                    } else {
                        Log.w(TAG, "test log: Unexpected error response format, neither 'error' nor 'status':'no_errors' found")
                    }
                    retryCount++
                    Log.d(TAG, "test log: Moving to next error check iteration, retryCount=$retryCount")
                }

                Log.d(TAG, "test log: Error check loop complete. errorFound=$errorFound, loggedInConfirmed=$loggedInConfirmed")
                if (errorFound || !loggedInConfirmed) {
                    if (!errorFound) {
                        Log.e(TAG, "test log: No explicit error found but login wasn't confirmed either")
                        _errorMessage.value = "Login verification failed."
                    }
                    _isLoggedIn.value = false
                    Log.e(TAG, "test log: Final error message: ${_errorMessage.value}")
                    throw IOException(_errorMessage.value.ifEmpty { "Login failed after checks" })
                }

                // --- Move credential saving here ---
                Log.d(TAG, "test log: Login successful, saving credentials")
                _storedUsername.value = username
                _storedPassword.value = password
                sharedPreferences?.edit {
                    putString("username", username)
                    putString("password", password)
                    apply()
                    Log.d(TAG, "test log: Credentials stored successfully")
                }
                // --- End credential saving move ---

                // Fetch data sequentially after successful login confirmation
                Log.d(TAG, "test log: Login successful, beginning data fetch")
                fetchAttendanceDataInternal() // Fetch attendance first and wait
                _isAttendanceDataLoaded.value = true // Assume loaded if no exception
                Log.d(TAG, "test log: Attendance data fetched successfully")

                // Fetch profile and timetable in parallel
                Log.d(TAG, "test log: Starting parallel fetch of profile and timetable")
                coroutineScope {
                    val profileJob = async { fetchProfileDataInternal() }
                    val timetableJob = async { fetchTimetableDataInternal(forceRefresh = false) } // No force on initial login
                    profileJob.await()
                    timetableJob.await()
                }
                Log.d(TAG, "test log: All initial data fetch complete after login")

            } catch (e: Exception) {
                Log.e(TAG, "test log: Login process error: ${e.message}", e)
                if (_errorMessage.value.isEmpty()) {
                    _errorMessage.value = "Login error: ${e.message?.take(100)}"
                }
                _isLoggedIn.value = false
                clearAllData() // Clear data on login failure
                Log.d(TAG, "test log: Data cleared after login failure")
            } finally {
                _isLoginInProgress.value = false
                _isLoading.value = false // Turn off general loading
                Log.d(TAG, "test log: Login process complete, isLoggedIn=${_isLoggedIn.value}")
            }
        }
    }

    // Internal function to fetch attendance, used by login/refresh
    private suspend fun fetchAttendanceDataInternal() {
        val currentJob = activeAttendanceJob
        if (currentJob != null && currentJob.isActive) {
            Log.d(TAG, "test log: Waiting for existing attendance job to complete")
            currentJob.join() // Wait if already running
            return // Don't start a new one if previous one just finished
        }

        val job = viewModelScope.launch {
            // No _isLoading manipulation here; caller manages it.
            _isAttendanceDataLoaded.value = false // Reset loaded flag
            try {
                val currentSessionId = _sessionId.value
                if (currentSessionId == null) {
                    Log.e(TAG, "test log: Session ID is null when trying to fetch attendance")
                    throw IOException("Session ID missing")
                }
                
                Log.d(TAG, "test log: Fetching attendance data with session ID: $currentSessionId")
                val attendanceResponse = apiService.getAttendanceData(currentSessionId)
                Log.d(TAG, "test log: Attendance response code: ${attendanceResponse.code()}, successful: ${attendanceResponse.isSuccessful}, has body: ${attendanceResponse.body() != null}")
                
                if (attendanceResponse.isSuccessful && attendanceResponse.body() != null) {
                    val responseBody = attendanceResponse.body()?.string() ?: ""
                    Log.d(TAG, "test log: Attendance response body length: ${responseBody.length}")
                    
                    val dataPrefix = "data: "
                    if (responseBody.contains(dataPrefix)) {
                        val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                        Log.d(TAG, "test log: Extracted attendance JSON data, length: ${jsonData.length}")
                        parseAttendanceData(jsonData) // Parse the data
                        Log.d(TAG, "test log: Attendance data parsed successfully")
                        // _isAttendanceDataLoaded is set based on successful parsing within parseAttendanceData indirectly
                    } else {
                        Log.e(TAG, "test log: Invalid SSE format - 'data:' prefix not found in response")
                        throw IOException("Invalid SSE format for attendance")
                    }
                } else {
                    Log.e(TAG, "test log: Attendance fetch failed with HTTP ${attendanceResponse.code()}")
                    throw IOException("Fetch attendance failed (HTTP ${attendanceResponse.code()})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "test log: Error fetching/parsing attendance: ${e.message}")
                _errorMessage.value = "Error loading attendance: ${e.message?.take(100)}"
                _subjectData.value = emptyList() // Clear data on error
                _isAttendanceDataLoaded.value = false
                throw e // Re-throw to signal failure to caller (e.g., refresh)
            } finally {
                activeAttendanceJob = null // Clear job reference when done
                Log.d(TAG, "test log: Attendance fetch job completed, success: ${_isAttendanceDataLoaded.value}")
            }
        }
        activeAttendanceJob = job
        job.join() // Wait for this fetch to complete before proceeding in the calling function
        Log.d(TAG, "test log: fetchAttendanceDataInternal finished")
    }

    // Renamed public function for clarity, delegates to internal
    fun fetchProfileData() {
        viewModelScope.launch { fetchProfileDataInternal() }
    }

    // Internal function to fetch profile, managing its own state
    private suspend fun fetchProfileDataInternal() {
        if (activeProfileJob?.isActive == true) {
            Log.d(TAG, "Profile fetch already in progress.")
            activeProfileJob?.join() // Wait for existing job
            return
        }
        val job = viewModelScope.launch {
            _isProfileLoading.value = true
            _profileError.value = null
            Log.d(TAG,"Starting internal profile fetch.")
            try {
                val currentSessionId = _sessionId.value ?: throw IOException("Session ID missing")
                Log.d(TAG, "Fetching profile data with session ID: $currentSessionId")

                val profileResponse = apiService.getProfileData(currentSessionId)
                if (profileResponse.isSuccessful && profileResponse.body() != null) {
                    val responseBody = profileResponse.body()?.string() ?: ""
                    val dataPrefix = "data: "
                    if (responseBody.contains(dataPrefix)) {
                        val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                        parseProfileDataJson(jsonData) // Use helper to parse
                    } else {
                        throw IOException("Invalid SSE format for profile")
                    }
                } else {
                    throw IOException("Fetch profile failed (HTTP ${profileResponse.code()})")
                }
                Log.d(TAG,"Internal profile fetch success.")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching profile: ${e.message}")
                _profileError.value = "Error loading profile: ${e.message?.take(100)}"
                _profileData.value = null // Clear data on error
                throw e // Re-throw
            } finally {
                _isProfileLoading.value = false
                activeProfileJob = null
                Log.d(TAG,"Internal profile fetch finished.")
            }
        }
        activeProfileJob = job
        job.join() // Wait for completion
    }

    // Renamed public function for clarity, delegates to internal
    fun fetchTimetableData(forceRefresh: Boolean = false) {
        viewModelScope.launch { fetchTimetableDataInternal(forceRefresh) }
    }

    // Internal function to fetch timetable, managing its own state
    private suspend fun fetchTimetableDataInternal(forceRefresh: Boolean = false) {
        val currentData = _timetableData.value
        if (!forceRefresh && currentData != null && !currentData.isStale()) {
            Log.d(TAG, "Using cached timetable data.")
            return
        }
        if (activeTimetableJob?.isActive == true) {
            Log.d(TAG, "Timetable fetch already in progress.")
            activeTimetableJob?.join() // Wait for existing job
            return
        }

        val job = viewModelScope.launch {
            _isTimetableLoading.value = true
            _timetableError.value = null
            Log.d(TAG,"Starting internal timetable fetch (force: $forceRefresh).")
            try {
                val currentSessionId = _sessionId.value ?: throw IOException("Session ID missing")
                Log.d(TAG, "Fetching timetable data with session ID: $currentSessionId")

                val timetableResponse = apiService.getTimetableData(currentSessionId)
                if (timetableResponse.isSuccessful && timetableResponse.body() != null) {
                    val responseBody = timetableResponse.body()?.string() ?: ""
                    val dataPrefix = "data: "
                    if (responseBody.contains(dataPrefix)) {
                        val jsonData = responseBody.substringAfter(dataPrefix).substringBefore("\n")
                        parseTimetableDataJson(jsonData) // Use helper to parse
                    } else {
                        throw IOException("Invalid SSE format for timetable")
                    }
                } else {
                    throw IOException("Fetch timetable failed (HTTP ${timetableResponse.code()})")
                }
                Log.d(TAG,"Internal timetable fetch success.")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching timetable: ${e.message}", e)
                _timetableError.value = "Error loading timetable: ${e.message?.take(100)}"
                _timetableData.value = null // Clear data on error
                throw e // Re-throw
            } finally {
                _isTimetableLoading.value = false
                activeTimetableJob = null
                Log.d(TAG,"Internal timetable fetch finished.")
            }
        }
        activeTimetableJob = job
        job.join() // Wait for completion
    }

    // --- Parsing Logic ---

    private fun parseAttendanceData(jsonData: String) {
        try {
            Log.d(TAG, "Parsing attendance data...")
            val rawJsonString = jsonData.trim()
            val gson = Gson()

            if (rawJsonString.startsWith("{")) { // New Object Format
                Log.d(TAG, "Parsing as AttendanceResponse object.")
                val response = gson.fromJson(rawJsonString, AttendanceResponse::class.java)
                val subjects = response.subjects.map { apiSubject ->
                    val records = apiSubject.records.map { apiRecord ->
                        AttendanceRecord(date = apiRecord.date, status = apiRecord.status)
                    }
                    val percentage = if (apiSubject.total_classes > 0) {
                        (apiSubject.present.toFloat() / apiSubject.total_classes) * 100
                    } else 0f
                    SubjectData(
                        code = apiSubject.code, name = apiSubject.name,
                        attendancePercentage = percentage,
                        overallClasses = apiSubject.total_classes,
                        overallPresent = apiSubject.present, overallAbsent = apiSubject.absent,
                        records = records
                    )
                }
                Log.d(TAG, "Parsed ${subjects.size} subjects from AttendanceResponse.")
                _subjectData.value = subjects
                _isAttendanceDataLoaded.value = true // Mark as loaded

            } else if (rawJsonString.startsWith("[")) { // Legacy Array Format
                Log.d(TAG, "Parsing as legacy List<List<String>> array.")
                val typeToken = object : TypeToken<List<List<String>>>() {}.type
                val rawAttendanceData: List<List<String>> = gson.fromJson(jsonData, typeToken)

                // ... (Keep your existing legacy array parsing logic here) ...
                var subjectCodesRow: List<String>? = null
                var overallClassesRow: List<String>? = null
                var overallPresentRow: List<String>? = null
                var overallAbsentRow: List<String>? = null
                var subjectMappingRow: String = ""
                val dateWiseRecords = mutableListOf<Pair<String, List<String>>>()

                for (row in rawAttendanceData) {
                    if (row.isEmpty()) continue
                    when (row[0]) {
                        "Days" -> subjectCodesRow = row
                        "Overall Class" -> overallClassesRow = row
                        "Overall  Present" -> overallPresentRow = row
                        "Overall Absent" -> overallAbsentRow = row
                    }
                    if (row[0].contains("-") && !row[0].contains("FCCS")) {
                        val date = row[0]
                        val records = row.subList(1, row.size)
                        dateWiseRecords.add(Pair(date, records))
                    }
                }
                if (rawAttendanceData.isNotEmpty() && rawAttendanceData.last().isNotEmpty()) {
                    subjectMappingRow = rawAttendanceData.last()[0]
                }
                val subjectCodes = subjectCodesRow?.drop(1) ?: emptyList()
                val subjectMap = mutableMapOf<String, String>()
                // ... (logic to populate subjectMap) ...
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


                val subjects = mutableListOf<SubjectData>()
                for (i in subjectCodes.indices) {
                    val code = subjectCodes[i]
                    val overallClasses = overallClassesRow?.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    val overallPresent = overallPresentRow?.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    val overallAbsent = overallAbsentRow?.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    val name = subjectMap[code] ?: code
                    val records = mutableListOf<AttendanceRecord>()
                    for ((date, statuses) in dateWiseRecords) {
                        if (i < statuses.size) {
                            records.add(AttendanceRecord(date = date, status = statuses[i]))
                        }
                    }
                    val percentage = if (overallClasses > 0) (overallPresent.toFloat() / overallClasses) * 100 else 0f
                    subjects.add(
                        SubjectData(
                            code = code, name = name, attendancePercentage = percentage,
                            overallClasses = overallClasses, overallPresent = overallPresent,
                            overallAbsent = overallAbsent, records = records
                        )
                    )
                }
                Log.d(TAG, "Parsed ${subjects.size} subjects from legacy array.")
                _subjectData.value = subjects
                _isAttendanceDataLoaded.value = true // Mark as loaded

            } else {
                throw IOException("Unrecognized JSON format (neither object nor array)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing attendance JSON: ${e.message}", e)
            _errorMessage.value = "Failed to process attendance data."
            _subjectData.value = emptyList()
            _isAttendanceDataLoaded.value = false
        }
    }

    private fun parseProfileDataJson(jsonData: String) {
        try {
            val gson = Gson()
            // Check for error format first
            try {
                val errorObj = gson.fromJson(jsonData, JsonObject::class.java)
                if (errorObj.has("error")) {
                    val errorMessage = errorObj.get("error").asString
                    Log.e(TAG, "Profile error from server: $errorMessage")
                    _profileError.value = errorMessage
                    _profileData.value = null
                    return // Exit parsing
                }
            } catch (e: Exception) { /* Not an error object, continue */ }

            // Parse actual data
            val profile = gson.fromJson(jsonData, ProfileData::class.java)
            _profileData.value = profile
            _profileError.value = null // Clear error on success
            Log.d(TAG, "Successfully parsed profile data for: ${profile.studentName}")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing profile JSON: ${e.message}", e)
            _profileError.value = "Failed to parse profile data."
            _profileData.value = null
        }
    }

    private fun parseTimetableDataJson(jsonData: String) {
        try {
            val gson = Gson()
            // Check for error format first
            try {
                val errorObj = gson.fromJson(jsonData, JsonObject::class.java)
                if (errorObj.has("error")) {
                    val errorMessage = errorObj.get("error").asString
                    Log.e(TAG, "Timetable error from server: $errorMessage")
                    _timetableError.value = errorMessage
                    _timetableData.value = null
                    return // Exit parsing
                }
            } catch (e: Exception) { /* Not an error object, continue */ }

            // Parse actual data
            val baseData = gson.fromJson(jsonData, TimetableData::class.java)
            val freshData = TimetableData(
                schedule = baseData.schedule,
                fetchTimestamp = System.currentTimeMillis() // Add timestamp
            )
            _timetableData.value = freshData
            _timetableError.value = null // Clear error on success
            Log.d(TAG, "Successfully parsed timetable data.")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timetable JSON: ${e.message}", e)
            _timetableError.value = "Failed to parse timetable data."
            _timetableData.value = null
        }
    }


    // --- Logout and Cleanup ---

    fun logout() {
        viewModelScope.launch {
            Log.d(TAG, "Logging out user...")
            _isLoading.value = true // Show loading during logout

            cancelRequests() // Cancel any active jobs
            delay(100)

            clearAllData() // Clear local data state
            _isLoggedIn.value = false
            _isSessionInitialized.value = false
            _sessionId.value = null

            // Clear credentials in memory
            _storedUsername.value = null
            _storedPassword.value = null

            // Clear SharedPreferences
            sharedPreferences?.edit {
                remove("username")
                remove("password")
                apply()
                Log.d(TAG, "Cleared credentials from SharedPreferences.")
            }

            _logoutCompleted.value = true // Signal completion if needed
            delay(100)
            _logoutCompleted.value = false

            _isLoading.value = false // Hide loading
            Log.d(TAG, "Logout process complete.")
        }
    }

    // Helper to clear all fetched data
    private fun clearAllData() {
        _subjectData.value = emptyList()
        _profileData.value = null
        _timetableData.value = null
        _isAttendanceDataLoaded.value = false
        _errorMessage.value = ""
        _profileError.value = null
        _timetableError.value = null
    }


    fun cancelRequests() {
        Log.d(TAG, "Cancelling active jobs...")
        activeLoginJob?.cancel()
        activeAttendanceJob?.cancel()
        activeProfileJob?.cancel()
        activeTimetableJob?.cancel()
        activeLoginJob = null
        activeAttendanceJob = null
        activeProfileJob = null
        activeTimetableJob = null
        Log.d(TAG,"Jobs cancelled.")
    }

    // --- Utility Functions ---

    fun hasStoredCredentials(): Boolean {
        return !_storedUsername.value.isNullOrEmpty() && !_storedPassword.value.isNullOrEmpty()
    }

    fun getStoredCredentials(): Pair<String, String>? {
        val username = _storedUsername.value
        val password = _storedPassword.value
        return if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            Pair(username, password)
        } else {
            null
        }
    }

    @Composable
    fun colorForSubject(subjectCode: String): Color {
        // Ensure generateConsistentColor exists and works with MaterialTheme.colorScheme
        return generateConsistentColor(subjectCode, MaterialTheme.colorScheme)
    }

    @Composable
    fun getAttendanceStatusColor(percentage: Float): Color {
        val colorScheme = MaterialTheme.colorScheme
        return when {
            percentage >= 75.0f -> colorScheme.tertiary
            percentage >= 65.0f -> colorScheme.secondary
            else -> colorScheme.error
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared.")
        cancelRequests()
    }

    private fun getCurrentDayAbbreviation(): String {
        val calendar = Calendar.getInstance()
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }
}


// --- Data Classes (Ensure these match your project structure and API) ---

// API Response Structure for Attendance (Example)


// API Response for Session ID (Example)
data class SessionResponse(
    val session_id: String
)

// Internal UI Data Model for Subjects
data class SubjectData(
    val code: String,
    val name: String,
    val attendancePercentage: Float,
    val overallClasses: Int,
    val overallPresent: Int,
    val overallAbsent: Int,
    val records: List<AttendanceRecord> // Assumes AttendanceRecord exists
)

// Internal UI Data Model for Profile
data class ProfileData(
    @SerializedName("Student ID") val studentID: String = "",
    @SerializedName("Student Name") val studentName: String = "",
    @SerializedName("DOB") val dob: String = "",
    @SerializedName("Gender") val gender: String = "",
    @SerializedName("Category") val category: String = "",
    @SerializedName("Admission") val admission: String = "",
    @SerializedName("Branch Name") val branchName: String = "",
    @SerializedName("Degree") val degree: String = "",
    @SerializedName("FT/PT") val ftpt: String = "",
    @SerializedName("Specialization") val specialization: String = "",
    @SerializedName("Section") val section: String = ""
)

