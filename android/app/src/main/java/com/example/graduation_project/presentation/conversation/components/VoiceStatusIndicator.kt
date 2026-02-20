package com.example.graduation_project.presentation.conversation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 음성 상태를 시각적으로 표시하는 컴포넌트
 *
 * ## 주요 기능
 * - 상태별 아이콘과 색상 변화
 * - LISTENING/RECORDING 시 펄스 애니메이션
 * - TalkBack 지원 (liveRegion으로 상태 변화 자동 안내)
 *
 * ## 접근성
 * - 80dp 크기의 큰 아이콘
 * - 고대비 색상 사용
 * - 상태 변화 시 스크린 리더가 자동으로 읽음
 */
@Composable
fun VoiceStatusIndicator(
    status: ConversationState,
    modifier: Modifier = Modifier
) {
    // 상태별 텍스트 (한국어)
    val statusText = when (status) {
        is ConversationState.Idle      -> "대기 중"
        is ConversationState.Listening -> "듣고 있어요"
        is ConversationState.Recording -> "녹음 중"
        is ConversationState.Sending   -> "전송 중"
        is ConversationState.Playing   -> "말하고 있어요"
        is ConversationState.Ended     -> "대화 종료"
    }

    // 상태별 아이콘
    val statusIcon: ImageVector = when (status) {
        is ConversationState.Idle      -> Icons.Default.MicOff
        is ConversationState.Listening -> Icons.Default.Mic
        is ConversationState.Recording -> Icons.Default.RecordVoiceOver
        is ConversationState.Sending   -> Icons.Default.Send
        is ConversationState.Playing   -> Icons.Default.PlayArrow
        is ConversationState.Ended     -> Icons.Default.Done
    }

    // 상태별 색상 (애니메이션으로 부드럽게 전환)
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            is ConversationState.Idle      -> Color.Gray
            is ConversationState.Listening -> Color(0xFF1976D2)  // 파랑
            is ConversationState.Recording -> Color(0xFFD32F2F)  // 빨강
            is ConversationState.Sending   -> Color(0xFFE65100)  // 주황
            is ConversationState.Playing   -> Color(0xFF388E3C)  // 녹색
            is ConversationState.Ended     -> Color.Gray
        },
        animationSpec = tween(durationMillis = 300),
        label = "statusColorAnimation"
    )

    // 펄스 애니메이션 (LISTENING, RECORDING 상태에서만 활성화)
    val shouldAnimate = status is ConversationState.Listening || status is ConversationState.Recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldAnimate) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnimation"
    )

    // TalkBack을 위한 접근성 설명
    val accessibilityDescription = when (status) {
        is ConversationState.Idle      -> "음성 대화 대기 중입니다"
        is ConversationState.Listening -> "사용자의 음성을 듣고 있습니다"
        is ConversationState.Recording -> "음성을 녹음하고 있습니다"
        is ConversationState.Sending   -> "서버에 전송 중입니다"
        is ConversationState.Playing   -> "AI가 응답을 말하고 있습니다"
        is ConversationState.Ended     -> "대화가 종료됐습니다"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingLarge)
            // TalkBack: 상태 변화 시 자동으로 읽어줌
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = accessibilityDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 아이콘 컨테이너 (원형 배경)
        Box(
            modifier = Modifier
                .size(Dimens.StatusIndicatorSize)
                .scale(scale)  // 펄스 애니메이션 적용
                .background(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,  // 부모에서 설명 제공
                modifier = Modifier.size(Dimens.IconSizeLarge),
                tint = statusColor
            )
        }

        // 상태 텍스트
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineSmall,
            color = statusColor,
            modifier = Modifier.padding(top = Dimens.SpacingMedium)
        )
    }
}

// 미리보기 (Android Studio에서 확인 가능)
@Preview(showBackground = true)
@Composable
private fun VoiceStatusIndicatorPreview_Idle() {
    Graduation_projectTheme {
        VoiceStatusIndicator(status = ConversationState.Idle)
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceStatusIndicatorPreview_Listening() {
    Graduation_projectTheme {
        VoiceStatusIndicator(status = ConversationState.Listening)
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceStatusIndicatorPreview_Playing() {
    Graduation_projectTheme {
        VoiceStatusIndicator(status = ConversationState.Playing)
    }
}
