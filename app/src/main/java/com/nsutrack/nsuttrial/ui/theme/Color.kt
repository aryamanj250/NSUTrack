package com.nsutrack.nsuttrial.ui.theme

import androidx.compose.ui.graphics.Color

// Google's primary blue color
val GoogleBlue = Color(0xFF1a73e8)
val GoogleBlueLight = Color(0xFF4285F4)
val GoogleBlueDark = Color(0xFF0d47a1)

// Other Google brand colors
val GoogleRed = Color(0xFFea4335)
val GoogleYellow = Color(0xFFfbbc04)
val GoogleGreen = Color(0xFF34a853)

// Light theme colors - following Google's Material Theme guidelines
val PrimaryLight = GoogleBlue
val SecondaryLight = Color(0xFF673AB7) // Google purple-ish
val TertiaryLight = GoogleGreen
val BackgroundLight = Color(0xFFFAFAFA) // Google uses very light gray background
val SurfaceLight = Color.White
val OnPrimaryLight = Color.White
val OnSecondaryLight = Color.White
val OnTertiaryLight = Color.White
val OnBackgroundLight = Color(0xFF202124) // Google's dark gray for text
val OnSurfaceLight = Color(0xFF202124)

// Dark theme colors - Google's dark theme colors
val PrimaryDark = Color(0xFF8AB4F8) // Lighter blue for dark theme
val SecondaryDark = Color(0xFFD0BCFF) // Light purple for dark theme
val TertiaryDark = Color(0xFFA8DAB5) // Light green for dark theme
val BackgroundDark = Color(0xFF202124) // Google's dark gray
val SurfaceDark = Color(0xFF292A2D) // Slightly lighter than background
val OnPrimaryDark = Color(0xFF002C5C)
val OnSecondaryDark = Color(0xFF381E72)
val OnTertiaryDark = Color(0xFF0F3921)
val OnBackgroundDark = Color(0xFFE1E3E3) // Google uses off-white text
val OnSurfaceDark = Color(0xFFE1E3E3)

// Status colors
val Error = GoogleRed
val ErrorDark = Color(0xFFF48FB1)
val Success = GoogleGreen
val SuccessDark = Color(0xFF81C784)
val Warning = GoogleYellow
val WarningDark = Color(0xFFFFE082)