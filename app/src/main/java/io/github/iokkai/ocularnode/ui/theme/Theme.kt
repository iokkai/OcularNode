package io.github.iokkai.ocularnode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = Color(0xFF141218),
        surface = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        onSurface = Color(0xFFE6E0E9)
    )

private val LightColorScheme =
    lightColorScheme(
        primary = CleanMinimalPrimary,
        primaryContainer = CleanMinimalPrimaryContainer,
        onPrimaryContainer = CleanMinimalOnPrimaryContainer,
        secondaryContainer = CleanMinimalSecondaryContainer,
        onSecondaryContainer = CleanMinimalOnSecondaryContainer,
        background = CleanMinimalBackground,
        surface = CleanMinimalSurface,
        surfaceVariant = CleanMinimalSurfaceVariant,
        onBackground = CleanMinimalTextPrimary,
        onSurface = CleanMinimalTextPrimary,
        onSurfaceVariant = CleanMinimalTextSecondary,
        outline = CleanMinimalBorder
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
