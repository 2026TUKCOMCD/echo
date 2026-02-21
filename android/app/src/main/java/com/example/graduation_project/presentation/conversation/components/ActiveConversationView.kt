package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.graduation_project.presentation.character.CharacterAnimationManager
import com.example.graduation_project.presentation.component.RecordingIndicator
import com.example.graduation_project.presentation.model.ConversationError
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.ui.theme.CharacterVideoBgColor
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 실시간 대화 중 화면
 *
 * ## 화면 구성
 * ┌─────────────────────────┐
 * │   CharacterPlayer       │  <- ExoPlayer mp4 영상
 * │   + PROCESSING 오버레이  │  <- 3초 후 "잠시만요", 6초 후 "조금만 기다려주세요"
 * │   RecordingIndicator     │  <- 상태 아이콘 + 텍스트 ("말하고 있어요" 등)
 * │  or AudioFallbackText    │  <- 음성 재생 실패 시 텍스트 표시 [T2.3-3]
 * │  or ErrorOverlay         │  <- 네트워크/서버 오류 시 재시도 버튼
 * └─────────────────────────┘
 *
 * ## 설계 의도
 * - 어르신 대상 앱으로 UI 단순화 (인지 부담 감소)
 * - 음성 대화에 집중할 수 있도록 최소한의 요소만 표시
 * - 음성 재생 실패 시 자동으로 텍스트 폴백 표시 (접근성 보장)
 * - mp4 영상으로 캐릭터 애니메이션 표현 (ExoPlayer 사용)
 *
 * ## 접근성
 * - liveRegion으로 상태 변화 자동 안내 (스크린 리더 지원)
 * - AI 응답 텍스트는 접근성 설명으로 제공
 * - 음성 재생 실패 시 큰 폰트(24sp)로 텍스트 직접 표시
 */
@Composable
fun ActiveConversationView(
    conversationState: ConversationState,
    playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
    isSpeechDetected: Boolean = false,
    isRecordingPreparing: Boolean = false,
    currentAiMessage: String?,
    currentUserSpeech: String? = null,
    voiceAmplitude: Float = 0f,
    showAudioFallbackText: Boolean = false,  // [T2.3-3] 텍스트 폴백 표시 여부
    audioFallbackText: String? = null,        // [T2.3-3] 폴백 텍스트 (AI 응답)
    retryProgress: String? = null,            // [T2.3-3] 재시도 진행 상황
    // 캐릭터 애니메이션 관련
    animationManager: CharacterAnimationManager? = null,
    // 현재 오류 상태
    currentError: ConversationError? = null,
    // PROCESSING 오버레이 관련
    processingMessage: String? = null,
    // 발화 인식 오류 관련
    speechErrorMessage: String? = null,
    speechErrorHint: String? = null,
    // 재시도 관련 (네트워크/서버 오류)
    isRetryButtonEnabled: Boolean = false,
    showContactSupport: Boolean = false,
    onRetryClick: () -> Unit = {},
    onContactSupportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 접근성 설명
    val accessibilityDescription = when {
        speechErrorMessage != null -> "$speechErrorMessage $speechErrorHint"
        showAudioFallbackText && audioFallbackText != null
            -> "음성 재생에 실패했습니다. 텍스트로 보여드립니다: $audioFallbackText"
        processingMessage != null -> processingMessage
        retryProgress != null -> retryProgress
        conversationState is ConversationState.Playing && playbackStatus == PlaybackStatus.PREPARING
            -> "AI 응답을 준비하고 있습니다. 잠시만 기다려주세요."
        conversationState is ConversationState.Idle      -> "대기 중입니다"
        conversationState is ConversationState.Listening -> "사용자의 음성을 듣고 있습니다"
        conversationState is ConversationState.Recording -> "음성을 녹음하고 있습니다"
        conversationState is ConversationState.Sending   -> "서버에 전송 중입니다"
        conversationState is ConversationState.Playing   -> "AI가 응답 중입니다: ${currentAiMessage ?: ""}"
        conversationState is ConversationState.Ended     -> "대화가 종료됐습니다"
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

        // 캐릭터 영상 + 오버레이 (동그라미)
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // ExoPlayer 영상 (animationManager가 있으면 사용, 없으면 정적 이미지)
            if (animationManager != null) {
                CharacterPlayer(
                    animationManager = animationManager,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 폴백: 기존 정적 이미지 (Preview용)
                AiCharacterImage(
                    size = 280.dp,
                    enableFloatingAnimation = conversationState is ConversationState.Playing
                            && playbackStatus == PlaybackStatus.PLAYING
                )
            }

            // PROCESSING 오버레이 (3초 후 "잠시만요", 6초 후 "조금만 기다려주세요")
            if (processingMessage != null) {
                ProcessingOverlay(message = processingMessage)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMedium))

        // 발화 인식 오류 표시
        if (speechErrorMessage != null) {
            SpeechErrorView(
                message = speechErrorMessage,
                hint = speechErrorHint
            )
        }
        // 네트워크/서버 오류 시 재시도 버튼
        else if (currentError is ConversationError.NetworkError || currentError is ConversationError.ServerError) {
            ErrorRetryView(
                isNetworkError = currentError is ConversationError.NetworkError,
                isRetryButtonEnabled = isRetryButtonEnabled,
                showContactSupport = showContactSupport,
                onRetryClick = onRetryClick,
                onContactSupportClick = onContactSupportClick
            )
        }
        // [T2.3-3] 조건부 렌더링: 텍스트 폴백 OR 상태 인디케이터
        else if (showAudioFallbackText && audioFallbackText != null) {
            // 음성 재생 실패 → 텍스트 폴백 표시
            AudioFallbackTextView(
                text = audioFallbackText,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            // 녹음 + 재생 상태 통합 표시 (상태 아이콘 + "말하고 있어요" 등 텍스트)
            RecordingIndicator(
                conversationState = conversationState,
                playbackStatus = playbackStatus,
                isSpeechDetected = isSpeechDetected,
                isRecordingPreparing = isRecordingPreparing
            )

            // [T2.3-3] 재시도 진행 상황 표시
            if (retryProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = retryProgress,
                    fontSize = 18.sp,
                    color = Color(0xFFFF9800),  // 주황색 (준비 중 상태 색상)
                    textAlign = TextAlign.Center
                )
            }
        }

        // 하단 여백 (버튼과의 간격)
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * ExoPlayer 캐릭터 영상 재생 컴포넌트
 * 상반신만 보이도록 확대 + 위치 조정
 */
@Composable
fun CharacterPlayer(
    animationManager: CharacterAnimationManager,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = animationManager.player
                useController = false // 컨트롤러 숨김
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = 1.3f  // 확대
                scaleY = 1.3f
                translationY = 50f  // 상반신만 보이도록 아래로 이동
            }
    )
}

/**
 * PROCESSING 상태 오버레이
 * 반투명 배경 + 메시지 텍스트
 */
@Composable
private fun ProcessingOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 발화 인식 오류 표시
 */
@Composable
private fun SpeechErrorView(
    message: String,
    hint: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .background(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE65100),
            textAlign = TextAlign.Center
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                fontSize = 18.sp,
                color = Color(0xFF795548),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 네트워크/서버 오류 재시도 UI
 */
@Composable
private fun ErrorRetryView(
    isNetworkError: Boolean,
    isRetryButtonEnabled: Boolean,
    showContactSupport: Boolean,
    onRetryClick: () -> Unit,
    onContactSupportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .background(
                color = if (isNetworkError) Color(0xFFFFEBEE) else Color(0xFFFFF3E0),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isNetworkError) "인터넷 연결을 확인해주세요" else "서버에 문제가 생겼어요",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (isNetworkError) Color(0xFFD32F2F) else Color(0xFFE65100),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isRetryButtonEnabled) {
            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNetworkError) Color(0xFFD32F2F) else Color(0xFFFF9800)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "다시 시도",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showContactSupport) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContactSupportClick
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "고객센터 연결",
                    fontSize = 16.sp
                )
            }
        }
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
            conversationState = ConversationState.Listening,
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
            conversationState = ConversationState.Playing,
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
            conversationState = ConversationState.Listening,
            currentAiMessage = "안녕하세요! 오늘 하루는 어떠셨나요?",
            currentUserSpeech = "오늘 공원에서 산책을..."
        )
    }
}

// 미리보기: AI 응답 준비 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_PreparingPlayback() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Playing,
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
            conversationState = ConversationState.Playing,
            playbackStatus = PlaybackStatus.PLAYING,
            currentAiMessage = "오늘 하루는 어떠셨나요? 특별한 일이 있으셨는지 궁금해요."
        )
    }
}

// 미리보기: 녹음 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Recording() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Recording,
            currentAiMessage = "안녕하세요! 오늘 하루는 어떠셨나요?"
        )
    }
}

// 미리보기: 전송 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Sending() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Sending,
            currentAiMessage = "안녕하세요! 오늘 하루는 어떠셨나요?"
        )
    }
}

// 미리보기: 대화 종료
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Ended() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Ended,
            currentAiMessage = "오늘도 즐거운 대화였습니다! 내일 또 만나요."
        )
    }
}

// 미리보기: PROCESSING 오버레이
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Processing() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Sending,
            currentAiMessage = null,
            processingMessage = "잠시만요"
        )
    }
}

// 미리보기: 발화 인식 오류
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_SpeechError() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Listening,
            currentAiMessage = null,
            currentError = ConversationError.SpeechUnrecognized,
            speechErrorMessage = "말씀이 잘 들리지 않았어요",
            speechErrorHint = "천천히, 또박또박 말씀해 주세요"
        )
    }
}

// 미리보기: 네트워크 오류 + 재시도 버튼
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_NetworkError() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Listening,
            currentAiMessage = null,
            currentError = ConversationError.NetworkError,
            isRetryButtonEnabled = true,
            showContactSupport = false
        )
    }
}

// 미리보기: 서버 오류 + 고객센터 연결
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_ServerErrorWithSupport() {
    Graduation_projectTheme {
        ActiveConversationView(
            conversationState = ConversationState.Listening,
            currentAiMessage = null,
            currentError = ConversationError.ServerError,
            isRetryButtonEnabled = false,
            showContactSupport = true
        )
    }
}
