package com.example.graduation_project.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class EchoColorScheme(
    val bgPage: Color,
    val bgCard: Color,
    val bgMuted: Color,
    val accentGreen: Color,
    val accentBlue: Color,
    val accentCoral: Color,
    val accentRed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val tabInactive: Color,
)

val defaultEchoColors = EchoColorScheme(
    bgPage        = Color(0xFFF5F4F1),
    bgCard        = Color(0xFFFFFFFF),
    bgMuted       = Color(0xFFEDECEA),
    accentGreen   = Color(0xFF3D8A5A),
    accentBlue    = Color(0xFF4A90D9),
    accentCoral   = Color(0xFFD89575),
    accentRed     = Color(0xFFD08068),
    textPrimary   = Color(0xFF1A1918),
    textSecondary = Color(0xFF6D6C6A),
    textTertiary  = Color(0xFF9C9B99),
    borderSubtle  = Color(0xFFE5E4E1),
    tabInactive   = Color(0xFFA8A7A5),
)

val highContrastEchoColors = EchoColorScheme(
    bgPage        = Color(0xFFFFFFFF),
    bgCard        = Color(0xFFFFFFFF),
    bgMuted       = Color(0xFFF0F0EE),
    accentGreen   = Color(0xFF2E6B47),
    accentBlue    = Color(0xFF1A6FBF),
    accentCoral   = Color(0xFFB86A4A),
    accentRed     = Color(0xFFB05030),
    textPrimary   = Color(0xFF0A0908),
    textSecondary = Color(0xFF3D3C3A),
    textTertiary  = Color(0xFF5A5957),
    borderSubtle  = Color(0xFFBBBAB6),
    tabInactive   = Color(0xFF6B6A68),
)

val LocalEchoColors = compositionLocalOf { defaultEchoColors }
