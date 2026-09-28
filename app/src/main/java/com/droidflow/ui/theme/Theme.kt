package com.droidflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletContainer,
    onPrimaryContainer = OnVioletContainer,
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = Amber,
    background = OffWhite,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E9F2),
    onSurfaceVariant = Color(0xFF55566B)
)

private val DarkColors = darkColorScheme(
    primary = PhaseViolet,
    onPrimary = Color(0xFF1A1440),
    primaryContainer = Color(0xFF3E3396),
    onPrimaryContainer = Color(0xFFE6E2FB),
    secondary = Color(0xFF5EEAD4),
    tertiary = Color(0xFFFCD34D),
    background = Color(0xFF111220),
    onBackground = Color(0xFFE8EAF6),
    surface = Color(0xFF171A2E),
    onSurface = Color(0xFFE8EAF6),
    surfaceVariant = Color(0xFF262C47),
    onSurfaceVariant = Color(0xFF9AA3C0)
)

@Composable
fun DroidFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DroidFlowTypography,
        content = content
    )
}
