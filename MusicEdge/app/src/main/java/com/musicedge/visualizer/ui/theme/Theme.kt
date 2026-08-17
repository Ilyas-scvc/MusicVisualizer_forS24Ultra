package com.musicedge.visualizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One UI-like palette and shapes: pure black in dark mode (AMOLED panels switch
 * those pixels off), soft grey cards in light mode, generously rounded corners.
 *
 * Nothing here copies a Samsung asset - it is a plain Material 3 scheme tuned to
 * sit comfortably next to One UI, and the type scale uses the device default font.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3E6FF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF001B49),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF5A5C63),
    outline = Color(0xFFD6D8DE),
    error = Color(0xFFC0362C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CA0FF),
    onPrimary = Color(0xFF00204D),
    primaryContainer = Color(0xFF1B2C55),
    onPrimaryContainer = Color(0xFFD9E2FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFECECEF),
    surface = Color(0xFF121216),
    onSurface = Color(0xFFECECEF),
    surfaceVariant = Color(0xFF1B1B20),
    onSurfaceVariant = Color(0xFFB4B5BC),
    outline = Color(0xFF33343B),
    error = Color(0xFFFF9A90),
)

private val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MusicEdgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = OneUiShapes,
        content = content,
    )
}
