package com.example.graduation_project.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.presentation.voice.VoiceRecordingState
import com.example.graduation_project.ui.theme.IdleGray
import com.example.graduation_project.ui.theme.ListeningBlue
import com.example.graduation_project.ui.theme.PlayingGreen
import com.example.graduation_project.ui.theme.PlayingGreenLight
import com.example.graduation_project.ui.theme.PreparingAmber
import com.example.graduation_project.ui.theme.PreparingOrange
import com.example.graduation_project.ui.theme.RecordingGreen
import com.example.graduation_project.ui.theme.RecordingGreenLight

/**
 * 녹음 + 재생 상태 시각적 피드백 통합 Composable
 *
 * VoiceRecordingState와 PlaybackStatus를 받아 7가지 시각 상태로 분기:
 * IDLE / PREPARING / LISTENING / RECORDING / PROCESSING / PREPARING_PLAYBACK / PLAYING_AUDIO
 *
 * playbackStatus가 NONE이 아닌 경우 재생 상태가 우선 표시됨.
 *
 * 어르신 접근성: 80.dp 아이콘, 200.dp 터치 영역, 22sp 안내 텍스트
 *
 * [T2.2-5] 녹음 상태 시각적 피드백
 * [T2.3-2] 재생 상태 UI 연동
 */
@Composable
fun RecordingIndicator(
    state: VoiceRecordingState,
    playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
    modifier: Modifier = Modifier
) {
    val visualState = remember(
        playbackStatus,
        state.isPreparing,
        state.isProcessing,
        state.isRecording,
        state.isSpeechDetected
    ) {
        when {
            playbackStatus == PlaybackStatus.PREPARING -> RecordingVisualState.PREPARING_PLAYBACK
            playbackStatus == PlaybackStatus.PLAYING -> RecordingVisualState.PLAYING_AUDIO
            state.isPreparing -> RecordingVisualState.PREPARING
            state.isProcessing -> RecordingVisualState.PROCESSING
            state.isRecording && state.isSpeechDetected -> RecordingVisualState.RECORDING
            state.isRecording && !state.isSpeechDetected -> RecordingVisualState.LISTENING
            else -> RecordingVisualState.IDLE
        }
    }

    val iconColor by animateColorAsState(
        targetValue = when (visualState) {
            RecordingVisualState.IDLE -> IdleGray
            RecordingVisualState.PREPARING -> PreparingAmber
            RecordingVisualState.LISTENING -> ListeningBlue
            RecordingVisualState.RECORDING -> RecordingGreen
            RecordingVisualState.PROCESSING -> IdleGray
            RecordingVisualState.PREPARING_PLAYBACK -> PreparingOrange
            RecordingVisualState.PLAYING_AUDIO -> PlayingGreen
        },
        animationSpec = tween(300),
        label = "iconColor"
    )

    val statusText = when (visualState) {
        RecordingVisualState.IDLE -> ""
        RecordingVisualState.PREPARING -> "준비 중..."
        RecordingVisualState.LISTENING -> "말씀해 주세요"
        RecordingVisualState.RECORDING -> "듣고 있어요"
        RecordingVisualState.PROCESSING -> "처리 중..."
        RecordingVisualState.PREPARING_PLAYBACK -> "응답 준비 중..."
        RecordingVisualState.PLAYING_AUDIO -> "말하고 있어요"
    }

    val stateDescriptionText = when (visualState) {
        RecordingVisualState.IDLE -> "녹음 비활성"
        RecordingVisualState.PREPARING -> "녹음 준비 중"
        RecordingVisualState.LISTENING -> "음성 입력 대기 중"
        RecordingVisualState.RECORDING -> "음성 녹음 중"
        RecordingVisualState.PROCESSING -> "음성 처리 중"
        RecordingVisualState.PREPARING_PLAYBACK -> "AI 응답을 준비하고 있습니다"
        RecordingVisualState.PLAYING_AUDIO -> "AI가 응답을 말하고 있습니다"
    }

    // 회전 애니메이션 (PREPARING_PLAYBACK 상태에서만)
    val infiniteTransition = rememberInfiniteTransition(label = "playbackTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (visualState == RecordingVisualState.PREPARING_PLAYBACK) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAnimation"
    )

    Column(
        modifier = modifier.semantics {
            stateDescription = stateDescriptionText
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            when (visualState) {
                RecordingVisualState.IDLE -> {
                    MicrophoneIcon(tint = iconColor)
                }

                RecordingVisualState.PREPARING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(120.dp),
                        color = PreparingAmber,
                        strokeWidth = 4.dp
                    )
                    MicrophoneIcon(tint = iconColor)
                }

                RecordingVisualState.LISTENING -> {
                    BreathingAnimation(durationMs = 2000) {
                        MicrophoneIcon(tint = iconColor)
                    }
                }

                RecordingVisualState.RECORDING -> {
                    PulseEffect(color = RecordingGreenLight) {
                        BreathingAnimation(
                            durationMs = 800,
                            minScale = 0.9f,
                            maxScale = 1.1f
                        ) {
                            MicrophoneIcon(tint = iconColor)
                        }
                    }
                }

                RecordingVisualState.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(120.dp),
                        color = IdleGray,
                        strokeWidth = 4.dp
                    )
                    MicrophoneIcon(tint = iconColor)
                }

                RecordingVisualState.PREPARING_PLAYBACK -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "응답 준비 중",
                        modifier = Modifier
                            .size(80.dp)
                            .rotate(rotation),
                        tint = iconColor
                    )
                }

                RecordingVisualState.PLAYING_AUDIO -> {
                    PulseEffect(color = PlayingGreenLight) {
                        BreathingAnimation(durationMs = 1500) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "AI 응답 재생 중",
                                modifier = Modifier.size(80.dp),
                                tint = iconColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = iconColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * RecordingIndicator 내부 시각 상태
 */
private enum class RecordingVisualState {
    IDLE,
    PREPARING,
    LISTENING,
    RECORDING,
    PROCESSING,
    PREPARING_PLAYBACK,
    PLAYING_AUDIO
}

// region Previews

@Preview(name = "Idle", showBackground = true)
@Composable
private fun RecordingIndicatorIdlePreview() {
    RecordingIndicator(state = VoiceRecordingState())
}

@Preview(name = "Preparing", showBackground = true)
@Composable
private fun RecordingIndicatorPreparingPreview() {
    RecordingIndicator(state = VoiceRecordingState(isPreparing = true))
}

@Preview(name = "Listening", showBackground = true)
@Composable
private fun RecordingIndicatorListeningPreview() {
    RecordingIndicator(state = VoiceRecordingState(isRecording = true))
}

@Preview(name = "Recording", showBackground = true)
@Composable
private fun RecordingIndicatorRecordingPreview() {
    RecordingIndicator(
        state = VoiceRecordingState(isRecording = true, isSpeechDetected = true)
    )
}

@Preview(name = "Processing", showBackground = true)
@Composable
private fun RecordingIndicatorProcessingPreview() {
    RecordingIndicator(state = VoiceRecordingState(isProcessing = true))
}

@Preview(name = "Preparing Playback", showBackground = true)
@Composable
private fun RecordingIndicatorPreparingPlaybackPreview() {
    RecordingIndicator(
        state = VoiceRecordingState(),
        playbackStatus = PlaybackStatus.PREPARING
    )
}

@Preview(name = "Playing Audio", showBackground = true)
@Composable
private fun RecordingIndicatorPlayingAudioPreview() {
    RecordingIndicator(
        state = VoiceRecordingState(),
        playbackStatus = PlaybackStatus.PLAYING
    )
}

// endregion
