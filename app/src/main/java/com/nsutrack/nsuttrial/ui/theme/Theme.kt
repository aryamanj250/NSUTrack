package com.nsutrack.nsuttrial.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nsutrack.nsuttrial.AttendanceViewModel
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.unit.dp


private val SoftDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0A2340),
    primaryContainer = Color(0xFF1C3B5A),
    onPrimaryContainer = Color(0xFFD6E4FF),

    secondary = Color(0xFFBBC3FF),
    onSecondary = Color(0xFF282747),
    secondaryContainer = Color(0xFF363B64), // Muted purple container
    onSecondaryContainer = Color(0xFFE6E0FF),

    tertiary = Color(0xFF9DCCB6),        // Soft teal
    onTertiary = Color(0xFF173B2D),
    tertiaryContainer = Color(0xFF1E4B3C), // Muted teal container
    onTertiaryContainer = Color(0xFFCFF4D9),

    error = Color(0xFFF5B8B8),           // Softer red
    onError = Color(0xFF5C1A1A),
    errorContainer = Color(0xFF7A2F2F),  // Muted red container
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF121212),      // Dark but not pure black
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1E1E1E),         // Slightly lighter than background
    onSurface = Color(0xFFE2E2E6),

    surfaceVariant = Color(0xFF323438),  // Slightly lighter than surface
    onSurfaceVariant = Color(0xFFCDCED2),
    outline = Color(0xFF8B8D91),
    surfaceTint = Color(0xFF90CAF9)     // Same as primary
)

// Updated Material 3 color scheme for light theme with softer colors
private val SoftLightColorScheme = lightColorScheme(
    primary = Color(0xFF4285F4),         // Google Blue, but softer
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF), // Very light blue
    onPrimaryContainer = Color(0xFF0A2463),

    secondary = Color(0xFF7986CB),       // Soft indigo
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EAFF), // Very light indigo
    onSecondaryContainer = Color(0xFF28316B),

    tertiary = Color(0xFF66BB6A),        // Soft green
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8F2D8), // Very light green
    onTertiaryContainer = Color(0xFF1E3620),

    error = Color(0xFFEF5350),           // Softer red
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),  // Very light red
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF8F9FA),      // Off-white
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFFFF),         // White
    onSurface = Color(0xFF1D1B20),

    surfaceVariant = Color(0xFFF0F0F5),  // Very light gray with blue tint
    onSurfaceVariant = Color(0xFF494949),
    outline = Color(0xFFBABDC2),
    surfaceTint = Color(0xFF4285F4)      // Same as primary
)

@Composable
fun NSUTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    viewModel: AttendanceViewModel? = null,
    content: @Composable () -> Unit
) {

    // Use the useDynamicColors preference from ViewModel if available
    val useDynamicColors by viewModel?.useDynamicColors?.collectAsState() ?: run {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    }

    val colorScheme = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SoftDarkColorScheme
        else -> SoftLightColorScheme
    }

    // Calculate the surface color with the specific elevation used in BottomNavBar
    val navigationBarColor = colorScheme.surfaceColorAtElevation(2.dp)

    // Apply status bar and navigation bar styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Tell the system that our app will handle edge-to-edge
            // WindowCompat.setDecorFitsSystemWindows(window, false) // REMOVED: To disable edge-to-edge

            // Set status bar color to match the app's background color
            window.statusBarColor = colorScheme.background.toArgb() // Use app background color

            // Set navigation bar color using the value resolved outside the SideEffect
            window.navigationBarColor = navigationBarColor.toArgb() // Use resolved color

            // If you want a small tonal difference between system nav and app nav,
            // you can use surfaceVariant instead
            // window.navigationBarColor = colorScheme.surfaceVariant.toArgb()

            // Control navigation bar icon/button colors based on theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme

            // Remove the navigation bar divider on Android 10+
            // window.isNavigationBarContrastEnforced = false // Keep this commented/removed if not needed
        }
    }


    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
