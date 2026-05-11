package com.example.graduation_project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
