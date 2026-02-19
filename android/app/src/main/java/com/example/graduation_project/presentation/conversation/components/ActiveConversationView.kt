package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * │  or AudioFallbackText    │  <- 음성 재생 실패 시 텍스트 표시 [T2.3-3]
 * └─────────────────────────┘
 *
 * ## 설계 의도
 * - 어르신 대상 앱으로 UI 단순화 (인지 부담 감소)
 * - 음성 대화에 집중할 수 있도록 최소한의 요소만 표시
 * - 음성 재생 실패 시 자동으로 텍스트 폴백 표시 (접근성 보장)
 *
 * ## 접근성
 * - liveRegion으로 상태 변화 자동 안내 (스크린 리더 지원)
 * - AI 응답 텍스트는 접근성 설명으로 제공
 * - 음성 재생 실패 시 큰 폰트(24sp)로 텍스트 직접 표시
 */
@Composable
fun ActiveConversationView(
    voiceStatus: VoiceStatus,
    playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
    recordingState: VoiceRecordingState = VoiceRecordingState(),
    currentAiMessage: String?,
    currentUserSpeech: String? = null,
    voiceAmplitude: Float = 0f,
    showAudioFallbackText: Boolean = false,  // [T2.3-3] 텍스트 폴백 표시 여부
    audioFallbackText: String? = null,        // [T2.3-3] 폴백 텍스트 (AI 응답)
    retryProgress: String? = null,            // [T2.3-3] 재시도 진행 상황
    modifier: Modifier = Modifier
) {
    // 접근성 설명
    val accessibilityDescription = when {
        showAudioFallbackText && audioFallbackText != null
            -> "음성 재생에 실패했습니다. 텍스트로 보여드립니다: $audioFallbackText"
        retryProgress != null
            -> retryProgress
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

        // [T2.3-3] 조건부 렌더링: 텍스트 폴백 OR 상태 인디케이터
        if (showAudioFallbackText && audioFallbackText != null) {
            // 음성 재생 실패 → 텍스트 폴백 표시
            AudioFallbackTextView(
                text = audioFallbackText,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            // 녹음 + 재생 상태 통합 표시 (상태 아이콘 + "말하고 있어요" 등 텍스트)
            RecordingIndicator(
                state = recordingState,
                playbackStatus = playbackStatus
            )

            // [T2.3-3] 재시도 진행 상황 표시
            if (retryProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    text = retryProgress,
                    fontSize = 18.sp,
                    color = androidx.compose.ui.graphics.Color(0xFFFF9800),  // 주황색 (준비 중 상태 색상)
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 하단 여백 (버튼과의 간격)
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * 오디오 재생 실패 시 텍스트 폴백 UI
 *
 * ## 디자인 특징
 * - 큰 폰트 (24sp): 어르신 접근성 고려
 * - 고대비 색상: 가독성 최대화
 * - 명확한 안내 메시지: "음성을 재생할 수 없어 텍스트로 보여드려요"
 * - 긍정적 톤: "실패" 대신 "대안 제공" 강조
 *
 * @param text AI 응답 텍스트
 *
 * [T2.3-3] 재생 에러 처리 및 텍스트 폴백
 */
@Composable
private fun AudioFallbackTextView(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .background(
                color = Color(0xFFFFF3E0),  // 연한 주황색 배경 (주의 환기)
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 안내 아이콘 (텍스트 아이콘)
        Icon(
            imageVector = Icons.Default.TextFields,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = Color(0xFFFF9800)  // 주황색
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 안내 메시지
        Text(
            text = "음성을 재생할 수 없어\n텍스트로 보여드려요",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE65100),  // 진한 주황색
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AI 응답 텍스트 (큰 폰트)
        Text(
            text = text,
            fontSize = 24.sp,            // 어르신 접근성 - 큰 폰트
            fontWeight = FontWeight.Bold,
            color = Color(0xFF212121),   // 고대비 - 검은색
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )
    }
}

// 미리보기: 텍스트 폴백 표시
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_AudioFallback() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.LISTENING,
            playbackStatus = PlaybackStatus.NONE,
            currentAiMessage = "오늘 하루는 어떠셨나요?",
            showAudioFallbackText = true,
            audioFallbackText = "오늘 하루는 어떠셨나요?"
        )
    }
}

// 미리보기: 재시도 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Retrying() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.PLAYING,
            playbackStatus = PlaybackStatus.PREPARING,
            currentAiMessage = null,
            retryProgress = "재시도 중 (2/3)"
        )
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
