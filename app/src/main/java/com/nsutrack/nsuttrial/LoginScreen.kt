package com.nsutrack.nsuttrial

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AttendanceViewModel,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoaded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val hapticFeedback = HapticFeedback.getHapticFeedback()
    val coroutineScope = rememberCoroutineScope()

    // Animation states
    var isUsernameFocused by remember { mutableStateOf(false) }
    var isPasswordFocused by remember { mutableStateOf(false) }
    var shouldNavigate by remember { mutableStateOf(false) }
    var showErrorAnimation by remember { mutableStateOf(false) }

    // Enhanced animation values
    val usernameScale by animateFloatAsState(
        targetValue = if (isUsernameFocused) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Username Scale"
    )

    val passwordScale by animateFloatAsState(
        targetValue = if (isPasswordFocused) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Password Scale"
    )

    // Error animation for login button
    val loginButtonScale by animateFloatAsState(
        targetValue = if (showErrorAnimation) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "Login Button Error Scale"
    )

    val loginButtonColor by animateColorAsState(
        targetValue = if (showErrorAnimation)
            MaterialTheme.colorScheme.errorContainer
        else
            MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "Login Button Error Color"
    )

    // Collect state flows more efficiently
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSessionInitialized by viewModel.isSessionInitialized.collectAsState()
    val isLoginInProgress by viewModel.isLoginInProgress.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isAttendanceDataLoaded by viewModel.isAttendanceDataLoaded.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()

    // Track if there's an error to show red borders
    val hasError = errorMessage.isNotEmpty()

    // Cancel error animation after a short delay
    LaunchedEffect(showErrorAnimation) {
        if (showErrorAnimation) {
            delay(600)
            showErrorAnimation = false
        }
    }

    // Clean up resources when screen is disposed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                viewModel.cancelRequests()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Animation delay for a smoother UX
    LaunchedEffect(key1 = true) {
        delay(100)
        isLoaded = true
    }

    // Navigate on successful attendance data loading with valid session
    LaunchedEffect(key1 = isAttendanceDataLoaded, key2 = isLoggedIn, key3 = sessionId) {
        if (isAttendanceDataLoaded && isLoggedIn && sessionId != null) {
            Log.d("SessionDebug", "Login successful with session ID: $sessionId")
            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.SUCCESS)
            shouldNavigate = true
        }
    }

    // Error feedback
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            // Trigger error animation
            showErrorAnimation = true

            // Error haptic feedback
            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.ERROR)
        }
    }

    // Perform actual navigation with a slight delay for better UX
    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            delay(300)
            onLoginSuccess()
        }
    }

    // Initialize session if needed (only if not already in progress)
    LaunchedEffect(key1 = isSessionInitialized, key2 = sessionId) {
        if ((!isSessionInitialized || sessionId == null) && !isLoading) {
            Log.d("SessionDebug", "Initializing session from LoginScreen")
            viewModel.initializeSession()
        }
    }
    LaunchedEffect(isSessionInitialized) {
        if (isSessionInitialized && !isLoading && !isLoginInProgress) {
            if (viewModel.hasStoredCredentials()) {
                Log.d("LoginScreen", "Found stored credentials, attempting auto-login")

                // Get stored credentials and use them
                val credentials = viewModel.getStoredCredentials()
                credentials?.let { (storedUsername, storedPassword) ->
                    // Update UI state to match stored credentials
                    username = storedUsername
                    password = storedPassword

                    // Delay slightly for better UX
                    delay(300)

                    // Attempt login with stored credentials
                    viewModel.login(storedUsername, storedPassword)

                    // Provide subtle feedback
                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                }
            } else {
                Log.d("LoginScreen", "No stored credentials found")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(animationSpec = tween(500)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .imePadding() // This will push content up when keyboard appears
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center // Center content vertically
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    )

                    // Username field with enhanced animation and error state
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .scale(usernameScale)
                            .onFocusChanged {
                                isUsernameFocused = it.isFocused
                                if (it.isFocused) {
                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                }
                            }
                            .then(
                                if (hasError) Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                        enabled = isSessionInitialized && !isLoading && !isLoginInProgress,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedLabelColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Password field with enhanced animation and error state
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .scale(passwordScale)
                            .onFocusChanged {
                                isPasswordFocused = it.isFocused
                                if (it.isFocused) {
                                    hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.LIGHT)
                                }
                            }
                            .then(
                                if (hasError) Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                        enabled = isSessionInitialized && !isLoading && !isLoginInProgress,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedLabelColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Login button with enhanced feedback and error animation
                    Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedback.FeedbackType.MEDIUM)
                            focusManager.clearFocus()
                            viewModel.login(username, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(loginButtonScale),
                        enabled = isSessionInitialized && username.isNotEmpty() && password.isNotEmpty() && !isLoading && !isLoginInProgress,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = loginButtonColor,
                            contentColor = if (showErrorAnimation)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        if (isLoading || isLoginInProgress) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(R.string.login_button),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Error message with animation
                    AnimatedVisibility(
                        visible = errorMessage.isNotEmpty(),
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(300))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    // Show session initialization state
                    if (!isSessionInitialized && isLoading) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Initializing session...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}