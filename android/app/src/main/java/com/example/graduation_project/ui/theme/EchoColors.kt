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
    bgPage        = Color(0xFFFFFBF5),  // 따뜻한 오프화이트 — 노안 눈부심 감소
    bgCard        = Color(0xFFFFFFFF),
    bgMuted       = Color(0xFFEEECE8),
    accentGreen   = Color(0xFF1A5C38),  // 채도 유지하며 어둡게 — 버튼 가독성
    accentBlue    = Color(0xFF0D4D9C),
    accentCoral   = Color(0xFFA84A25),  // 따뜻한 강조색 명확히
    accentRed     = Color(0xFF962010),
    textPrimary   = Color(0xFF0F0E0D),  // 준검정 — 할레이션 없이 선명
    textSecondary = Color(0xFF2A2928),  // 확실히 읽힘
    textTertiary  = Color(0xFF4A4947),  // 노안 인식 한계 수준 확보
    borderSubtle  = Color(0xFF888683),  // 칸 구분선 명확히
    tabInactive   = Color(0xFF555453),  // 비활성 탭 인식
)

val LocalEchoColors = compositionLocalOf { defaultEchoColors }
