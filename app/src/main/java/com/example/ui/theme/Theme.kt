package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Cyan400,
    onPrimary = Slate950,
    primaryContainer = Cyan900,
    onPrimaryContainer = Cyan400,
    secondary = Emerald400,
    onSecondary = Slate950,
    secondaryContainer = Emerald900,
    onSecondaryContainer = Emerald400,
    tertiary = Amber400,
    onTertiary = Slate950,
    tertiaryContainer = Amber900,
    onTertiaryContainer = Amber400,
    error = Rose400,
    onError = Slate950,
    errorContainer = Rose900,
    onErrorContainer = Rose400,
    background = Slate950,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate850,
    onSurfaceVariant = Slate400,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    outline = Slate700,
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan700,
    onPrimary = LightSurface,
    primaryContainer = ColorTokens.LightCyanContainer,
    onPrimaryContainer = Slate900,
    secondary = Emerald700,
    onSecondary = LightSurface,
    secondaryContainer = ColorTokens.LightEmeraldContainer,
    onSecondaryContainer = Slate900,
    tertiary = Amber700,
    onTertiary = LightSurface,
    tertiaryContainer = ColorTokens.LightAmberContainer,
    onTertiaryContainer = Slate900,
    error = Rose700,
    onError = LightSurface,
    background = LightBg,
    onBackground = Slate900,
    surface = LightSurface,
    onSurface = Slate900,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Slate700,
    outline = Slate400,
)

private object ColorTokens {
    val LightCyanContainer = androidx.compose.ui.graphics.Color(0xFFCFFAFE)
    val LightEmeraldContainer = androidx.compose.ui.graphics.Color(0xFFD1FAE5)
    val LightAmberContainer = androidx.compose.ui.graphics.Color(0xFFFEF3C7)
}

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SaeTtsBenchmarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
