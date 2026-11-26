package ru.moscow.lineaccess.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A70),
    onPrimary = Color.White,
    background = Color(0xFFF5F8F8),
    onBackground = Color(0xFF102D34),
    surface = Color.White,
    onSurface = Color(0xFF102D34),
    onSurfaceVariant = Color(0xFF435D62),
    outlineVariant = Color(0xFFCDDDDB),
    primaryContainer = Color(0xFFD6EFEB),
    onPrimaryContainer = Color(0xFF124E51)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D9D9),
    onPrimary = Color(0xFF00383C),
    background = Color(0xFF101A20),
    onBackground = Color(0xFFE7F3F2),
    surface = Color(0xFF1B2B31),
    onSurface = Color(0xFFE7F3F2),
    onSurfaceVariant = Color(0xFFB4C9CD),
    outlineVariant = Color(0xFF354A4F),
    primaryContainer = Color(0xFF20484B),
    onPrimaryContainer = Color(0xFFB0EFEB)
)

@Composable
fun LineAccessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
