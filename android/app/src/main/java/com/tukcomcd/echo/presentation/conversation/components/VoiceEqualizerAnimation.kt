package com.tukcomcd.echo.presentation.conversation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tukcomcd.echo.ui.theme.Graduation_projectTheme
import kotlin.random.Random

/**
 * 이퀄라이저 막대 애니메이션 컴포넌트
 *
 * ## 동작
 * - 3개의 막대가 실제 음성 볼륨(amplitude)에 반응하여 움직임
 * - amplitude 값(0.0 ~ 1.0)에 따라 막대 높이가 변함
 * - 각 막대는 약간의 랜덤 변화를 주어 자연스러운 효과
 *
 * ## 접근성
 * - 어르신들이 쉽게 인식할 수 있는 큰 막대
 * - 상태별 색상으로 명확한 구분
 *
 * @param isActive 애니메이션 활성화 여부
 * @param amplitude 음성 볼륨 (0.0 ~ 1.0), 실제 마이크 입력에서 전달
 * @param color 막대 색상
 */
@Composable
fun VoiceEqualizerAnimation(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 3,
    barWidth: Dp = 20.dp,
    maxHeight: Dp = 80.dp,
    minHeight: Dp = 24.dp,
    spacing: Dp = 12.dp
) {
    // 각 막대별 랜덤 가중치 (자연스러운 변화를 위해)
    val barWeights = remember {
        List(barCount) { 0.7f + Random.nextFloat() * 0.6f }  // 0.7 ~ 1.3
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barWeights.forEachIndexed { index, weight ->
            // 각 막대의 높이 계산 (amplitude에 가중치 적용)
            val targetHeight = if (isActive && amplitude > 0.01f) {
                val adjustedAmplitude = (amplitude * weight).coerceIn(0f, 1f)
                minHeight + (maxHeight - minHeight) * adjustedAmplitude
            } else {
                minHeight
            }

            // 부드러운 높이 전환 애니메이션
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 100),
                label = "barHeight$index"
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}

// 미리보기: 낮은 볼륨
@Preview(showBackground = true)
@Composable
private fun VoiceEqualizerAnimationPreview_LowVolume() {
    Graduation_projectTheme {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceEqualizerAnimation(
                isActive = true,
                amplitude = 0.3f,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

// 미리보기: 높은 볼륨
@Preview(showBackground = true)
@Composable
private fun VoiceEqualizerAnimationPreview_HighVolume() {
    Graduation_projectTheme {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceEqualizerAnimation(
                isActive = true,
                amplitude = 0.9f,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

// 미리보기: 비활성화 상태
@Preview(showBackground = true)
@Composable
private fun VoiceEqualizerAnimationPreview_Inactive() {
    Graduation_projectTheme {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceEqualizerAnimation(
                isActive = false,
                amplitude = 0f,
                color = Color(0xFF616161)
            )
        }
    }
}

// 미리보기: 듣는 중 (파란색)
@Preview(showBackground = true)
@Composable
private fun VoiceEqualizerAnimationPreview_Listening() {
    Graduation_projectTheme {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceEqualizerAnimation(
                isActive = true,
                amplitude = 0.6f,
                color = Color(0xFF0D47A1)
            )
        }
    }
}
