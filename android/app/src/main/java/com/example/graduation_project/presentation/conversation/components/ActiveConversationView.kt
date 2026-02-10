package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.graduation_project.presentation.model.VoiceStatus
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 실시간 대화 중 화면
 *
 * ## 화면 구성
 * ┌─────────────────────────┐
 * │      상태 텍스트         │  <- "듣고 있어요", "말하고 있어요" 등
 * ├─────────────────────────┤
 * │                         │
 * │      AI 아이콘           │
 * │   현재 AI 응답 텍스트     │  <- 마지막 응답만 크게 표시
 * │                         │
 * ├─────────────────────────┤
 * │    원형 파동 애니메이션   │  <- RippleAnimation
 * └─────────────────────────┘
 *
 * ## 접근성
 * - liveRegion으로 상태 변화 자동 안내
 * - 큰 글씨와 명확한 색상 대비
 */
@Composable
fun ActiveConversationView(
    voiceStatus: VoiceStatus,
    currentAiMessage: String?,
    currentUserSpeech: String? = null,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    // 상태별 텍스트
    val statusText = when (voiceStatus) {
        VoiceStatus.IDLE -> "대기 중"
        VoiceStatus.LISTENING -> "듣고 있어요"
        VoiceStatus.RECORDING -> "녹음 중"
        VoiceStatus.PLAYING -> "말하고 있어요"
    }

    // 상태별 색상 (어르신 접근성 고려 - 고대비)
    val statusColor = when (voiceStatus) {
        VoiceStatus.IDLE -> Color(0xFF616161)        // 진한 회색
        VoiceStatus.LISTENING -> Color(0xFF0D47A1)   // 진한 파랑 (듣는 중)
        VoiceStatus.RECORDING -> Color(0xFFC62828)   // 진한 빨강 (녹음 중)
        VoiceStatus.PLAYING -> Color(0xFF2E7D32)     // 진한 녹색 (말하는 중)
    }

    // 접근성 설명
    val accessibilityDescription = when (voiceStatus) {
        VoiceStatus.IDLE -> "대기 중입니다"
        VoiceStatus.LISTENING -> "사용자의 음성을 듣고 있습니다"
        VoiceStatus.RECORDING -> "음성을 녹음하고 있습니다"
        VoiceStatus.PLAYING -> "AI가 응답 중입니다: ${currentAiMessage ?: ""}"
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
        // 상단: 상태 텍스트 (흰색 배경 + 테두리 말풍선)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(width = 2.dp, color = statusColor),
            modifier = Modifier.padding(top = Dimens.SpacingLarge)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = statusColor,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        // 중앙 영역: AI 아이콘 + 현재 응답
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // 상단 여백 (voice status와 AI 응답 사이 중간 위치)
            Spacer(modifier = Modifier.height(24.dp))

            // AI 캐릭터 이미지 (어르신 접근성 - 더 큰 사이즈)
            // AI가 말할 때만 애니메이션 활성화
            AiCharacterImage(
                size = 200.dp,
                enableFloatingAnimation = voiceStatus == VoiceStatus.PLAYING
            )

            Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

            // AI 응답 (고정 영역 - 최대 높이 제한)
            if (!currentAiMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentAiMessage,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Dimens.SpacingMedium)
                    )
                }
            }

            // 사용자 실시간 음성 인식 텍스트 (자동 스크롤)
            if (!currentUserSpeech.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))

                val userSpeechScrollState = rememberScrollState()

                // 사용자 발화가 변경될 때마다 자동으로 맨 아래로 스크롤
                LaunchedEffect(currentUserSpeech) {
                    userSpeechScrollState.animateScrollTo(userSpeechScrollState.maxValue)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(userSpeechScrollState),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentUserSpeech,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color(0xFF0D47A1),  // 파란색 (사용자 발화 구분)
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Dimens.SpacingMedium)
                    )
                }
            }
        }

        // 하단: 동심원 파동 애니메이션 (음성 볼륨에 반응)
        RippleAnimation(
            isActive = voiceStatus != VoiceStatus.IDLE,
            amplitude = voiceAmplitude,
            color = statusColor,
            size = 120.dp,
            modifier = Modifier.padding(bottom = Dimens.SpacingLarge)
        )
    }
}

// 미리보기: 듣고 있는 상태 (음성 인식 중)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_Listening() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.LISTENING,
            currentAiMessage = "안녕하세요! 오늘 하루는 어떠셨나요?",
            currentUserSpeech = "오늘 공원에서 산책을..."
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
            currentAiMessage = "오늘 하루는 어떠셨나요? 특별한 일이 있으셨는지 궁금해요."
        )
    }
}

// 미리보기: 긴 응답
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_LongMessage() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.PLAYING,
            currentAiMessage = "시간 여행이 가능하다면, 사람들이 타임머신을 만들 때 가장 간과할 수 있는 설계 결함은 무엇일까요?"
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
            currentAiMessage = "안녕하세요! 오늘 하루는 어떠셨나요?"
        )
    }
}

// 미리보기: 긴 AI 응답 + 긴 사용자 발화 (스크롤 테스트)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_LongBoth() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.LISTENING,
            currentAiMessage = "오늘 하루는 정말 좋은 날씨였네요! 공원에서 산책하셨다니 정말 좋은 선택이셨어요. 산책하면서 어떤 것들을 보셨나요? 꽃이나 나무, 또는 다른 사람들도 많이 보셨나요?",
            currentUserSpeech = "네, 오늘 공원에서 산책을 했는데요. 날씨가 정말 좋았어요. 벚꽃이 활짝 피어있었고, 아이들이 뛰어노는 모습도 보였어요. 그리고 강아지를 산책시키는 분들도 많이 계셨어요. 저도 예전에 강아지를 키웠었는데, 그때 생각이 많이 났어요. 정말 오랜만에 마음이 편안해지는 시간이었습니다."
        )
    }
}

// 미리보기: 매우 긴 사용자 발화 (자동 스크롤 테스트)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActiveConversationViewPreview_VeryLongUserSpeech() {
    Graduation_projectTheme {
        ActiveConversationView(
            voiceStatus = VoiceStatus.RECORDING,
            currentAiMessage = "오늘 하루는 어떠셨나요?",
            currentUserSpeech = "오늘은 아침에 일어나서 먼저 창문을 열고 환기를 시켰어요. 그리고 간단하게 아침 식사를 하고 나서 공원으로 산책을 나갔습니다. 공원에는 벚꽃이 정말 예쁘게 피어있었어요. 사람들도 많이 나와서 사진도 찍고 있었고요. 저도 핸드폰으로 사진을 몇 장 찍었어요. 그리고 벤치에 앉아서 잠시 쉬면서 새소리도 듣고 바람도 느꼈어요. 정말 평화로운 시간이었습니다. 집에 돌아와서는 점심을 먹고 TV를 좀 봤어요. 요즘 재미있게 보는 드라마가 있거든요."
        )
    }
}
