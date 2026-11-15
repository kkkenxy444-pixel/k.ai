package com.kai.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Night Studio: dark-first indigo surfaces, an amber stage light for actions and voice,
// and a moon-lavender tone reserved for roleplay and stories. Serif carries the story voice.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2B75C), onPrimary = Color(0xFF271B03),
    primaryContainer = Color(0xFF4A3413), onPrimaryContainer = Color(0xFFFFDFA8),
    secondary = Color(0xFFC9B8F2), onSecondary = Color(0xFF241A40),
    secondaryContainer = Color(0xFF392D58), onSecondaryContainer = Color(0xFFE9E1FF),
    background = Color(0xFF0F0E17), surface = Color(0xFF0F0E17),
    surfaceContainerLow = Color(0xFF191727), surfaceContainer = Color(0xFF211E31),
    surfaceContainerHigh = Color(0xFF2B2740), surfaceVariant = Color(0xFF3B3554),
    onBackground = Color(0xFFF0EDF6), onSurface = Color(0xFFF0EDF6),
    onSurfaceVariant = Color(0xFFB4ACC6), outline = Color(0xFF847B9B),
    outlineVariant = Color(0xFF3B3554), error = Color(0xFFFFB4AB),
    onError = Color(0xFF5C1109), errorContainer = Color(0xFF7A2216), onErrorContainer = Color(0xFFFFDAD5),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A14), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDFAE), onPrimaryContainer = Color(0xFF2C1E04),
    secondary = Color(0xFF5A4A8C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E1FF), onSecondaryContainer = Color(0xFF241A40),
    background = Color(0xFFFAF8F3), surface = Color(0xFFFAF8F3),
    surfaceContainerLow = Color(0xFFF2EFE7), surfaceContainer = Color(0xFFEAE6DC),
    surfaceContainerHigh = Color(0xFFE1DCCF), surfaceVariant = Color(0xFFE5DFD2),
    onBackground = Color(0xFF211D2E), onSurface = Color(0xFF211D2E),
    onSurfaceVariant = Color(0xFF675F76), outline = Color(0xFF7E7490),
    outlineVariant = Color(0xFFD9D2C4), error = Color(0xFF9C4137),
    onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF410002),
)

@Composable fun KaiTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(0xFFFAF8F3.toInt(), 0xFF0F0E17.toInt()) { dark },
        )
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 43.sp, letterSpacing = (-.5).sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.4).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-.3).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
        ), content = content)
}
