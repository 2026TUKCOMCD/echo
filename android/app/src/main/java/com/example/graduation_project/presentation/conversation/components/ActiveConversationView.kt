package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.graduation_project.presentation.component.RecordingIndicator
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.presentation.model.VoiceStatus
import com.example.graduation_project.presentation.voice.VoiceRecordingState
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 실시간 대화 중 화면
 *
 * ## 화면 구성
 * ┌─────────────────────────┐
 * │      AI 캐릭터 이미지     │
 * │   RecordingIndicator     │  <- 상태 아이콘 + 텍스트 ("말하고 있어요" 등)
 * └─────────────────────────┘
 *
 * ## 설계 의도
 * - 어르신 대상 앱으로 UI 단순화 (인지 부담 감소)
 * - 음성 대화에 집중할 수 있도록 최소한의 요소만 표시
 *
 * ## 접근성
 * - liveRegion으로 상태 변화 자동 안내 (스크린 리더 지원)
 * - AI 응답 텍스트는 접근성 설명으로만 제공
 */
@Composable
fun ActiveConversationView(
    voiceStatus: VoiceStatus,
    playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
    recordingState: VoiceRecordingState = VoiceRecordingState(),
    currentAiMessage: String?,
    currentUserSpeech: String? = null,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    // 접근성 설명
    val accessibilityDescription = when {
        voiceStatus == VoiceStatus.PLAYING && playbackStatus == PlaybackStatus.PREPARING
            -> "AI 응답을 준비하고 있습니다. 잠시만 기다려주세요."
        voiceStatus == VoiceStatus.IDLE -> "대기 중입니다"
        voiceStatus == VoiceStatus.LISTENING -> "사용자의 음성을 듣고 있습니다"
        voiceStatus == VoiceStatus.RECORDING -> "음성을 녹음하고 있습니다"
        voiceStatus == VoiceStatus.PLAYING -> "AI가 응답 중입니다: ${currentAiMessage ?: ""}"
        else -> "대기 중입니다"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SpacingMedium)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = accessibilityDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 여백
        Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

        // AI 캐릭터 이미지 (어르신 접근성 - 더 큰 사이즈)
        // 실제 재생 중일 때만 떠다니기 애니메이션 활성화
        AiCharacterImage(
            size = 200.dp,
            enableFloatingAnimation = voiceStatus == VoiceStatus.PLAYING
                && playbackStatus == PlaybackStatus.PLAYING
        )

        // 녹음 + 재생 상태 통합 표시 (상태 아이콘 + "말하고 있어요" 등 텍스트)
        RecordingIndicator(
            state = recordingState,
            playbackStatus = playbackStatus
        )

        // 하단 여백 (버튼과의 간격)
        Spacer(modifier = Modifier.weight(1f))
    }
}

// 미리보기: 듣고 있는 상태
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Listening() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.LISTENING,
            currentAiMessage = null
        )
    }
}

// 미리보기: AI 응답 준비 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_PreparingPlayback() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.PLAYING,
            playbackStatus = PlaybackStatus.PREPARING,
            currentAiMessage = null
        )
    }
}

// 미리보기: AI 응답 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Playing() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.PLAYING,
            playbackStatus = PlaybackStatus.PLAYING,
            currentAiMessage = "오늘 하루는 어떠셨나요?"  // 접근성용
        )
    }
}

// 미리보기: 녹음 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Recording() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.RECORDING,
            currentAiMessage = null
        )
    }
}
