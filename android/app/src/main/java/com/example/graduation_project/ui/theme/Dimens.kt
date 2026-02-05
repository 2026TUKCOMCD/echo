package com.example.graduation_project.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 접근성을 고려한 치수 상수
 * - 경도인지장애 어르신을 위해 큰 터치 영역과 여백 사용
 * - WCAG 기준: 최소 터치 영역 44dp, 권장 56dp 이상
 */
object Dimens {
    // 터치 영역 크기
    val MinTouchTarget = 56.dp      // 최소 터치 영역 (WCAG 권장)
    val ButtonHeight = 64.dp        // 버튼 높이 (큰 버튼)
    val ButtonMinWidth = 200.dp     // 버튼 최소 너비

    // 여백 (Spacing)
    val SpacingSmall = 8.dp         // 작은 여백
    val SpacingMedium = 16.dp       // 중간 여백
    val SpacingLarge = 24.dp        // 큰 여백
    val SpacingXLarge = 32.dp       // 매우 큰 여백

    // 아이콘 크기
    val IconSizeSmall = 24.dp       // 작은 아이콘
    val IconSizeMedium = 32.dp      // 중간 아이콘
    val IconSizeLarge = 48.dp       // 큰 아이콘
    val StatusIndicatorSize = 80.dp // 상태 표시 아이콘 (매우 큼)

    // 메시지 버블
    val MessageBubbleMaxWidth = 0.85f  // 화면 너비의 85%
    val MessageBubbleRadius = 16.dp    // 모서리 둥글기
    val MessageBubblePadding = 12.dp   // 내부 여백
}
