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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material 3 color scheme for dark theme
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFCFE5FF),

    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = Color(0xFF384186),
    onSecondaryContainer = Color(0xFFDEE0FF),

    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = Color(0xFF004F58),
    onTertiaryContainer = Color(0xFFAEECFA),

    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,

    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9198),
    surfaceTint = PrimaryDark
)

// Material 3 color scheme for light theme
private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),

    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = Color(0xFFDEE0FF),
    onSecondaryContainer = Color(0xFF0B1566),

    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = Color(0xFFAEECFA),
    onTertiaryContainer = Color(0xFF001F24),

    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,

    surfaceVariant = Color(0xFFDFE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF73777F),
    surfaceTint = PrimaryLight
)

@Composable
fun NSUTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Update the system bars to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Set status bar color with improved contrast
            window.statusBarColor = if (darkTheme) {
                colorScheme.surface.toArgb()
            } else {
                colorScheme.primary.toArgb()
            }

            // Set the status bar icons to appropriate contrast
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme

            // Optional: Set navigation bar color to match theme
            window.navigationBarColor = if (darkTheme) {
                colorScheme.surface.toArgb()
            } else {
                Color.White.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}