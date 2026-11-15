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
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBB9FF), onPrimary = Color(0xFF30204E),
    primaryContainer = Color(0xFF423361), onPrimaryContainer = Color(0xFFF0E8FF),
    secondary = Color(0xFFFFC4B6), onSecondary = Color(0xFF52271F),
    secondaryContainer = Color(0xFF4C333F), onSecondaryContainer = Color(0xFFFFE2D8),
    background = Color(0xFF171622), surface = Color(0xFF171622),
    surfaceContainerLow = Color(0xFF201E2D), surfaceContainer = Color(0xFF292638),
    surfaceContainerHigh = Color(0xFF353045), surfaceVariant = Color(0xFF383146),
    onBackground = Color(0xFFF6F1FF), onSurface = Color(0xFFF6F1FF),
    onSurfaceVariant = Color(0xFFC1B9CD), outline = Color(0xFF8A8095),
    outlineVariant = Color(0xFF494153), error = Color(0xFFFFB7B0),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF67509B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DFFF), onPrimaryContainer = Color(0xFF342451),
    secondary = Color(0xFF90574A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4DA), onSecondaryContainer = Color(0xFF4C271F),
    background = Color(0xFFFAF8FE), surface = Color(0xFFFAF8FE),
    surfaceContainerLow = Color(0xFFF3EFFA), surfaceContainer = Color(0xFFEDE8F5),
    surfaceContainerHigh = Color(0xFFE4DDEC), surfaceVariant = Color(0xFFE9E1F0),
    onBackground = Color(0xFF292333), onSurface = Color(0xFF292333),
    onSurfaceVariant = Color(0xFF655B70), outline = Color(0xFF82778D),
    outlineVariant = Color(0xFFD9D0E0), error = Color(0xFFAE312E),
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
        shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)),
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
            headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 39.sp, letterSpacing = (-.6).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        ), content = content)
}
