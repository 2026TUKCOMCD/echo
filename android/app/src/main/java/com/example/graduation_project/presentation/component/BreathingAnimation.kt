package com.example.graduation_project.presentation.component

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 숨쉬기(스케일 반복) 애니메이션 Composable
 *
 * LISTENING: durationMs=2000, 0.95~1.05 (느린 숨쉬기)
 * RECORDING: durationMs=800, 0.9~1.1 (빠른 펄스)
 *
 * [T2.2-5] 녹음 상태 시각적 피드백
 */
@Composable
fun BreathingAnimation(
    modifier: Modifier = Modifier,
    durationMs: Int = 2000,
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
