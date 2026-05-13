package com.example.graduation_project.presentation.conversation.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.graduation_project.R
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * AI 캐릭터(WELLBOT) 이미지 컴포넌트
 *
 * 대화 상태별 애니메이션:
 * - Idle      : 위아래 부드럽게 떠다니기 (2.5초)
 * - Listening : 좌우 기울기 흔들기 (귀 기울이는 느낌, 0.7초)
 * - Recording : 빠른 스케일 펄스 (0.93 ~ 1.07, 0.5초)
 * - Sending   : 앞뒤 회전 흔들기 (±8°, 0.6초)
 * - Playing   : 위아래 바운스 + 약한 스케일 (1.2초)
 * - Ended     : 정지 (애니메이션 없음)
 */
@Composable
fun AiCharacterImage(
    size: Dp,
    conversationState: ConversationState = ConversationState.Idle,
    enableFloatingAnimation: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "characterAnim")

    // ── 위아래 오프셋 (Idle / Playing) ──────────────────────────
    val floatTarget = when {
        !enableFloatingAnimation -> 0f
        conversationState is ConversationState.Idle -> -8f
        conversationState is ConversationState.Playing -> -12f
        else -> 0f
    }
    val floatDuration = if (conversationState is ConversationState.Playing) 1200 else 2500

    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = floatTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(floatDuration, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    // ── 좌우 흔들기 (Listening) ──────────────────────────────────
    val shakeTarget =
        if (enableFloatingAnimation && conversationState is ConversationState.Listening) 5f else 0f

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -shakeTarget,
        targetValue = shakeTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    // ── 스케일 펄스 (Recording / Playing / Idle) ─────────────────
    val (scaleMin, scaleMax, scaleDur) = when {
        !enableFloatingAnimation -> Triple(1f, 1f, 1000)
        conversationState is ConversationState.Recording -> Triple(0.93f, 1.07f, 500)
        conversationState is ConversationState.Playing   -> Triple(0.98f, 1.05f, 1200)
        conversationState is ConversationState.Idle      -> Triple(0.98f, 1.02f, 3000)
        else -> Triple(1f, 1f, 1000)
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = scaleMin,
        targetValue = scaleMax,
        animationSpec = infiniteRepeatable(
            animation = tween(scaleDur, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // ── 회전 흔들기 (Sending) ────────────────────────────────────
    val rotTarget =
        if (enableFloatingAnimation && conversationState is ConversationState.Sending) 8f else 0f

    val rotation by infiniteTransition.animateFloat(
        initialValue = -rotTarget,
        targetValue = rotTarget,
        animationSpec = infiniteRepeatable(
            animation = tween(if (rotTarget > 0f) 600 else 5000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Image(
        painter = painterResource(id = R.drawable.wellbot),
        contentDescription = "AI 어시스턴트 웰봇",
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                translationX = offsetX * density
                translationY = offsetY * density
            },
        contentScale = ContentScale.Fit
    )
}

@Preview(showBackground = true)
@Composable
private fun AiCharacterImagePreview() {
    Graduation_projectTheme {
        AiCharacterImage(size = 120.dp)
    }
}
