package com.example.graduation_project.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.example.graduation_project.presentation.voice.VoiceRecordingState
import com.example.graduation_project.ui.theme.IdleGray
import com.example.graduation_project.ui.theme.ListeningBlue
import com.example.graduation_project.ui.theme.PreparingAmber
import com.example.graduation_project.ui.theme.RecordingGreen
import com.example.graduation_project.ui.theme.RecordingGreenLight

/**
 * 녹음 상태 시각적 피드백 메인 Composable
 *
 * VoiceRecordingState를 받아 5가지 시각 상태로 분기:
 * IDLE / PREPARING / LISTENING / RECORDING / PROCESSING
 *
 * 어르신 접근성: 80.dp 아이콘, 200.dp 터치 영역, 22sp 안내 텍스트
 *
 * [T2.2-5] 녹음 상태 시각적 피드백
 */
@Composable
fun RecordingIndicator(
    state: VoiceRecordingState,
    modifier: Modifier = Modifier
) {
    val visualState = remember(
        state.isPreparing,
        state.isProcessing,
        state.isRecording,
        state.isSpeechDetected
    ) {
        when {
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
    }

    val stateDescriptionText = when (visualState) {
        RecordingVisualState.IDLE -> "녹음 비활성"
        RecordingVisualState.PREPARING -> "녹음 준비 중"
        RecordingVisualState.LISTENING -> "음성 입력 대기 중"
        RecordingVisualState.RECORDING -> "음성 녹음 중"
        RecordingVisualState.PROCESSING -> "음성 처리 중"
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
    PROCESSING
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

// endregion
