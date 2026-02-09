package com.example.graduation_project.presentation.conversation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 원형 파동 애니메이션 컴포넌트
 *
 * ## 동작
 * - 3개의 원이 중앙에서 순차적으로 퍼져나가는 효과
 * - 각 원은 scale과 alpha 애니메이션 조합
 * - isActive가 false면 애니메이션 정지
 *
 * ## 사용 예시
 * ```kotlin
 * RippleAnimation(
 *     isActive = voiceStatus != VoiceStatus.IDLE,
 *     color = MaterialTheme.colorScheme.primary
 * )
 * ```
 */
@Composable
fun RippleAnimation(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 200.dp,
    rippleCount: Int = 3
) {
    // 각 원의 애니메이션 상태
    val animatables = remember {
        List(rippleCount) { Animatable(0f) }
    }

    // 애니메이션 실행 - 각 원을 별도의 코루틴에서 실행
    LaunchedEffect(isActive) {
        if (isActive) {
            animatables.forEachIndexed { index, animatable ->
                launch {
                    // 각 원이 순차적으로 시작하도록 딜레이
                    delay(index * 400L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 1200,
                                easing = LinearEasing
                            ),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                }
            }
        } else {
            // 비활성화 시 애니메이션 초기화
            animatables.forEach { it.snapTo(0f) }
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerX = size.toPx() / 2
            val centerY = size.toPx() / 2
            val maxRadius = size.toPx() / 2

            animatables.forEach { animatable ->
                val progress = animatable.value
                if (progress > 0f) {
                    val radius = maxRadius * progress
                    val alpha = (1f - progress) * 0.4f

                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )
                }
            }
        }
    }
}

// 미리보기
@Preview(showBackground = true)
@Composable
private fun RippleAnimationPreview_Active() {
    Graduation_projectTheme {
        RippleAnimation(isActive = true)
    }
}

@Preview(showBackground = true)
@Composable
private fun RippleAnimationPreview_Inactive() {
    Graduation_projectTheme {
        RippleAnimation(isActive = false)
    }
}
