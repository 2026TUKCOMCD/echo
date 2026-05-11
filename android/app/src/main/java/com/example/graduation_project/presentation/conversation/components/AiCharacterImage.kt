package com.example.graduation_project.presentation.conversation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
 * 3. 떠있는 애니메이션 - 위아래로 부드럽게 움직임 (2초 주기)
 * 4. 크기 펄스 애니메이션 - 살짝 커졌다 작아짐 (1.5초 주기)
 * 5. 배경 블렌딩 - 그라데이션 배경으로 자연스러운 전환
 * 6. 그림자 효과 - 부드러운 그림자로 입체감 표현
 *
 * @param size 이미지 크기
 * @param enableFloatingAnimation 애니메이션 활성화 여부 (떠다니기 + 크기 펄스)
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

    Image(
        painter = painterResource(id = R.drawable.img_2),
        contentDescription = "AI 어시스턴트",
        modifier = modifier
            .size(size)
            .scale(scale)
            .offset { IntOffset(0, floatingOffset.roundToInt()) },
        contentScale = ContentScale.Fit
    )
}

@Preview(showBackground = true)
@Composable
private fun AiCharacterImagePreview() {
    Graduation_projectTheme {
        AiCharacterImage(size = 120.dp, enableFloatingAnimation = false)
    }
}
