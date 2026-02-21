package com.example.graduation_project.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.ui.theme.ListeningTeal
import com.example.graduation_project.ui.theme.ListeningTealLight
import com.example.graduation_project.ui.theme.PlayingAmber
import com.example.graduation_project.ui.theme.PlayingAmberLight
import com.example.graduation_project.ui.theme.ProcessingGray

/**
 * 녹음 + 재생 상태 시각적 피드백 통합 Composable
 *
 * ConversationState와 PlaybackStatus, isSpeechDetected를 받아 5가지 시각 상태로 분기:
 * PREPARING / LISTENING / RECORDING / PROCESSING / PLAYING
 *
 * - PREPARING: VAD 초기화 중 (로딩 + "준비 중...") - 어르신 혼란 방지
 * - LISTENING: 음성 입력 대기 (마이크 아이콘 + "말씀해 주세요")
 * - RECORDING: VAD 음성 감지됨 (마이크 + 파동 애니메이션 + "듣고 있어요")
 * - PROCESSING: 서버 처리 중 (마이크 꺼짐 + "잠시 기다려주세요") - Sending, PlaybackStatus.PREPARING 통합
 * - PLAYING: AI 응답 재생 중 (재생 아이콘 + "말하고 있어요")
 *
 * 어르신 접근성: 80.dp 아이콘, 200.dp 터치 영역, 22sp 안내 텍스트
 */
@Composable
fun RecordingIndicator(
    conversationState: ConversationState,
    playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
    isSpeechDetected: Boolean = false,
    isRecordingPreparing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visualState = remember(
        playbackStatus,
        conversationState,
        isSpeechDetected,
        isRecordingPreparing
    ) {
        when {
            // VAD 초기화 중 (PREPARING 복원 - 어르신 혼란 방지)
            isRecordingPreparing -> RecordingVisualState.PREPARING
            // AI 응답 재생 중
            playbackStatus == PlaybackStatus.PLAYING -> RecordingVisualState.PLAYING
            // 서버 처리 중 (Sending 또는 PlaybackStatus.PREPARING)
            conversationState is ConversationState.Sending -> RecordingVisualState.PROCESSING
            playbackStatus == PlaybackStatus.PREPARING -> RecordingVisualState.PROCESSING
            // VAD 음성 감지됨
            conversationState is ConversationState.Listening && isSpeechDetected -> RecordingVisualState.RECORDING
            conversationState is ConversationState.Recording -> RecordingVisualState.RECORDING
            // 음성 입력 대기
            conversationState is ConversationState.Listening -> RecordingVisualState.LISTENING
            // 그 외 (Idle, Ended 등)
            else -> RecordingVisualState.LISTENING
        }
    }

    // 경도인지장애 어르신 접근성:
    // - LISTENING/RECORDING: 청록색 (사용자 행동)
    // - PLAYING: 앰버색 (AI 응답 - 따뜻하고 눈에 평안)
    val iconColor by animateColorAsState(
        targetValue = when (visualState) {
            RecordingVisualState.PREPARING -> ProcessingGray
            RecordingVisualState.LISTENING -> ListeningTeal  // 사용자: 청록색
            RecordingVisualState.RECORDING -> ListeningTeal  // 사용자: 청록색
            RecordingVisualState.PROCESSING -> ProcessingGray
            RecordingVisualState.PLAYING -> PlayingAmber     // AI: 앰버색
        },
        animationSpec = tween(300),
        label = "iconColor"
    )

    val statusText = when (visualState) {
        RecordingVisualState.PREPARING -> "준비 중..."
        RecordingVisualState.LISTENING -> "말씀해 주세요"
        RecordingVisualState.RECORDING -> "듣고 있어요"
        RecordingVisualState.PROCESSING -> "잠시 기다려주세요"
        RecordingVisualState.PLAYING -> "말하고 있어요"
    }

    val stateDescriptionText = when (visualState) {
        RecordingVisualState.PREPARING -> "음성 인식 준비 중"
        RecordingVisualState.LISTENING -> "음성 입력 대기 중"
        RecordingVisualState.RECORDING -> "음성 녹음 중"
        RecordingVisualState.PROCESSING -> "서버 처리 중"
        RecordingVisualState.PLAYING -> "AI가 응답을 말하고 있습니다"
    }

    Column(
        modifier = modifier.semantics {
            stateDescription = stateDescriptionText
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            when (visualState) {
                RecordingVisualState.PREPARING -> {
                    // VAD 초기화 중 - 로딩 인디케이터 + 마이크 아이콘 (어르신 혼란 방지)
                    CircularProgressIndicator(
                        modifier = Modifier.size(100.dp),
                        color = ProcessingGray,
                        strokeWidth = 4.dp
                    )
                    MicrophoneIcon(tint = iconColor, size = 60.dp)
                }

                RecordingVisualState.LISTENING -> {
                    BreathingAnimation(durationMs = 2000) {
                        MicrophoneIcon(tint = iconColor, size = 60.dp)
                    }
                }

                RecordingVisualState.RECORDING -> {
                    PulseEffect(color = ListeningTealLight) {
                        BreathingAnimation(
                            durationMs = 800,
                            minScale = 0.9f,
                            maxScale = 1.1f
                        ) {
                            MicrophoneIcon(tint = iconColor, size = 60.dp)
                        }
                    }
                }

                RecordingVisualState.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(100.dp),
                        color = ProcessingGray,
                        strokeWidth = 4.dp
                    )
                    MicrophoneOffIcon(tint = iconColor, size = 60.dp)
                }

                RecordingVisualState.PLAYING -> {
                    PulseEffect(color = PlayingAmberLight) {
                        BreathingAnimation(durationMs = 1500) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "AI 응답 재생 중",
                                modifier = Modifier.size(60.dp),
                                tint = iconColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = statusText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = iconColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * RecordingIndicator 내부 시각 상태 (5개)
 * 어르신 혼란 방지를 위해 PREPARING 상태 포함
 */
private enum class RecordingVisualState {
    /** VAD 초기화 중 - "준비 중..." (어르신 혼란 방지) */
    PREPARING,
    /** 음성 입력 대기 (ConversationState.Listening, 음성 미감지) */
    LISTENING,
    /** VAD 음성 감지됨 (ConversationState.Listening + isSpeechDetected, ConversationState.Recording) */
    RECORDING,
    /** 서버 처리 중 (ConversationState.Sending, PlaybackStatus.PREPARING 통합) */
    PROCESSING,
    /** AI 응답 재생 중 (PlaybackStatus.PLAYING) */
    PLAYING
}

// region Previews

@Preview(name = "Preparing", showBackground = true)
@Composable
private fun RecordingIndicatorPreparingPreview() {
    RecordingIndicator(
        conversationState = ConversationState.Listening,
        isRecordingPreparing = true
    )
}

@Preview(name = "Listening", showBackground = true)
@Composable
private fun RecordingIndicatorListeningPreview() {
    RecordingIndicator(conversationState = ConversationState.Listening)
}

@Preview(name = "Recording", showBackground = true)
@Composable
private fun RecordingIndicatorRecordingPreview() {
    RecordingIndicator(
        conversationState = ConversationState.Listening,
        isSpeechDetected = true
    )
}

@Preview(name = "Processing (Sending)", showBackground = true)
@Composable
private fun RecordingIndicatorProcessingPreview() {
    RecordingIndicator(conversationState = ConversationState.Sending)
}

@Preview(name = "Processing (Preparing Playback)", showBackground = true)
@Composable
private fun RecordingIndicatorPreparingPlaybackPreview() {
    RecordingIndicator(
        conversationState = ConversationState.Playing,
        playbackStatus = PlaybackStatus.PREPARING
    )
}

@Preview(name = "Playing", showBackground = true)
@Composable
private fun RecordingIndicatorPlayingPreview() {
    RecordingIndicator(
        conversationState = ConversationState.Playing,
        playbackStatus = PlaybackStatus.PLAYING
    )
}

// endregion
