package com.example.graduation_project.presentation.conversation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.graduation_project.R
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import kotlin.math.roundToInt

/**
 * AI 캐릭터 이미지 컴포넌트
 *
 * ## 적용된 효과
 * 1. 원형 클리핑 - 캐릭터를 동그랗게 표시
 * 2. 둥근 카드 - 그림자와 배경이 있는 카드 스타일
 * 3. 떠있는 애니메이션 - 위아래로 부드럽게 움직임
 * 4. 배경 블렌딩 - 그라데이션 배경으로 자연스러운 전환
 * 5. 그림자 효과 - 부드러운 그림자로 입체감 표현
 *
 * @param size 이미지 크기
 * @param enableFloatingAnimation 떠있는 애니메이션 활성화 여부
 * @param modifier Modifier
 */
@Composable
fun AiCharacterImage(
    size: Dp,
    enableFloatingAnimation: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 3. 떠있는 애니메이션 + 크기 펄스
    val infiniteTransition = rememberInfiniteTransition(label = "characterAnimation")

    val floatingOffset = if (enableFloatingAnimation) {
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatingOffset"
        )
        offsetY
    } else {
        0f
    }

    // 크기 펄스 애니메이션 (살짝 커졌다 작아짐) - 애니메이션 활성화 시에만
    val scale = if (enableFloatingAnimation) {
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scaleAnimation"
        )
        animatedScale
    } else {
        1f
    }

    // 4. 배경 블렌딩을 위한 그라데이션 색상
    val backgroundGradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFFF5F0E8),  // 이미지 배경과 비슷한 따뜻한 베이지
            Color(0xFFE8F4F8),  // 밝은 하늘색
            MaterialTheme.colorScheme.surface
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .offset { IntOffset(0, floatingOffset.roundToInt()) },
        contentAlignment = Alignment.Center
    ) {
        // 5. 그림자 효과 + 2. 둥근 카드 배경
        Box(
            modifier = Modifier
                .size(size + 16.dp)  // 패딩을 위해 약간 더 크게
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x40000000),
                    spotColor = Color(0x30000000)
                )
                .background(
                    brush = backgroundGradient,
                    shape = CircleShape
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. 원형 클리핑 적용된 이미지
            Image(
                painter = painterResource(id = R.drawable.conversation_character),
                contentDescription = "AI 어시스턴트",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// 미리보기: 기본
@Preview(showBackground = true)
@Composable
private fun AiCharacterImagePreview() {
    Graduation_projectTheme {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            AiCharacterImage(
                size = 120.dp,
                enableFloatingAnimation = false
            )
        }
    }
}

// 미리보기: 작은 사이즈
@Preview(showBackground = true)
@Composable
private fun AiCharacterImagePreview_Small() {
    Graduation_projectTheme {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            AiCharacterImage(
                size = 80.dp,
                enableFloatingAnimation = false
            )
        }
    }
}
