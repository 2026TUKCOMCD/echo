package com.example.graduation_project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.graduation_project.presentation.settings.DisplaySettings

private val LightColorScheme = lightColorScheme(
    primary             = EchoAccentGreen,
    onPrimary           = Color.White,
    primaryContainer    = EchoBgMuted,
    onPrimaryContainer  = EchoTextPrimary,
    secondary           = EchoAccentCoral,
    onSecondary         = Color.White,
    secondaryContainer  = EchoBgMuted,
    onSecondaryContainer = EchoTextPrimary,
    background          = EchoBgPage,
    onBackground        = EchoTextPrimary,
    surface             = EchoBgCard,
    onSurface           = EchoTextPrimary,
    onSurfaceVariant    = EchoTextSecondary,
    outline             = EchoBorderSubtle,
    error               = EchoAccentRed,
    onError             = Color.White,
)

@Composable
fun Graduation_projectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    displaySettings: DisplaySettings = DisplaySettings(),
    content: @Composable () -> Unit
) {
    val echoColors = if (displaySettings.isHighContrast) highContrastEchoColors else defaultEchoColors
    val currentDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalEchoColors provides echoColors,
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * displaySettings.fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
