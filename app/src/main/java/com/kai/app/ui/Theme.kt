package com.kai.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5D5C5), onPrimary = Color(0xFF1C352A), primaryContainer = Color(0xFF263F34), onPrimaryContainer = Color(0xFFD8EBDD),
    secondary = Color(0xFFB8C6D2), background = Color(0xFF101416), surface = Color(0xFF101416), surfaceVariant = Color(0xFF232B2E),
    onBackground = Color(0xFFE4E8E6), onSurface = Color(0xFFE4E8E6), onSurfaceVariant = Color(0xFFA4B0AB),
    outline = Color(0xFF66716B), outlineVariant = Color(0xFF303B36), error = Color(0xFFFFB4AB),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF365D49), onPrimary = Color.White, primaryContainer = Color(0xFFDCEBDF), onPrimaryContainer = Color(0xFF183326),
    secondary = Color(0xFF516674), background = Color(0xFFF6F7F3), surface = Color(0xFFF6F7F3), surfaceVariant = Color(0xFFE5EAE3),
    onBackground = Color(0xFF19231E), onSurface = Color(0xFF19231E), onSurfaceVariant = Color(0xFF526158),
    outline = Color(0xFF6A7A6F), outlineVariant = Color(0xFFD1DAD2),
)

@Composable fun KaiTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(0xFFE5EAE3.toInt(), 0xFF101416.toInt()) { dark },
        )
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
            headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.6).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        ), content = content)
}
