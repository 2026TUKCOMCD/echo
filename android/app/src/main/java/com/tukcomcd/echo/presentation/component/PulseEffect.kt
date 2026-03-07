package com.tukcomcd.echo.presentation.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tukcomcd.echo.ui.theme.RecordingGreenLight

/**
 * 동심원 리플 펄스 애니메이션 Composable
 *
 * 3개의 링이 시차를 두고 중심에서 확장되며 페이드 아웃
 * RECORDING 상태에서 음성 감지 피드백으로 사용
 *
 * [T2.2-5] 녹음 상태 시각적 피드백
 */
@Composable
fun PulseEffect(
    modifier: Modifier = Modifier,
    color: Color = RecordingGreenLight,
    ringCount: Int = 3,
    baseRadius: Dp = 60.dp,
    maxRadiusMultiplier: Float = 2.5f,
    durationMs: Int = 1500,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val ringProgresses = (0 until ringCount).map { index ->
        val delay = (durationMs / ringCount) * index
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_$index"
        )
        progress
    }

    val canvasSize = baseRadius * maxRadiusMultiplier * 2

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(canvasSize)) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadiusPx = baseRadius.toPx()
            val strokeWidthPx = 3.dp.toPx()

            ringProgresses.forEach { progress ->
                val currentRadius =
                    baseRadiusPx + (baseRadiusPx * (maxRadiusMultiplier - 1) * progress)
                val alpha = ((1f - progress) * 0.4f).coerceIn(0f, 0.4f)

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = currentRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = strokeWidthPx)
                )
            }
        }
        content()
    }
}
