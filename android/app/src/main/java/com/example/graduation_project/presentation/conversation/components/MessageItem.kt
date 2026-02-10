package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 개별 메시지를 표시하는 컴포넌트
 *
 * ## 디자인
 * - AI 메시지: 왼쪽 정렬, 밝은 회색 배경
 * - 사용자 메시지: 오른쪽 정렬, Primary 색상 배경
 * - 둥근 모서리의 말풍선 스타일
 *
 * ## 접근성
 * - 큰 글씨 (20sp)
 * - 발화자 명시 (AI/나)
 * - TalkBack에서 전체 메시지 내용 읽기
 */
@Composable
fun MessageItem(
    message: MessageUiModel,
    modifier: Modifier = Modifier
) {
    // 화면 너비의 85%를 최대 너비로 설정
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp * Dimens.MessageBubbleMaxWidth).dp

    // 발화자 텍스트
    val senderText = if (message.isFromUser) "나" else "AI"

    // 시간 포맷팅
    val timeText = formatTime(message.timestamp)

    // TalkBack 접근성 설명
    val accessibilityText = "$senderText 님이 $timeText 에 보낸 메시지: ${message.text}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingSmall)
            .semantics { contentDescription = accessibilityText },
        horizontalArrangement = if (message.isFromUser) {
            Arrangement.End  // 사용자 메시지: 오른쪽
        } else {
            Arrangement.Start  // AI 메시지: 왼쪽
        }
    ) {
        Column(
            modifier = Modifier.widthIn(max = maxWidth),
            horizontalAlignment = if (message.isFromUser) {
                Alignment.End
            } else {
                Alignment.Start
            }
        ) {
            // 발화자 표시
            Text(
                text = senderText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = if (!message.isFromUser) Dimens.SpacingSmall else 0.dp,
                    end = if (message.isFromUser) Dimens.SpacingSmall else 0.dp,
                    bottom = 4.dp
                )
            )

            // 메시지 버블
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = Dimens.MessageBubbleRadius,
                            topEnd = Dimens.MessageBubbleRadius,
                            bottomStart = if (message.isFromUser) Dimens.MessageBubbleRadius else 4.dp,
                            bottomEnd = if (message.isFromUser) 4.dp else Dimens.MessageBubbleRadius
                        )
                    )
                    .background(
                        if (message.isFromUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(Dimens.MessageBubblePadding)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isFromUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 타임스탬프
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(
                    start = if (!message.isFromUser) Dimens.SpacingSmall else 0.dp,
                    end = if (message.isFromUser) Dimens.SpacingSmall else 0.dp,
                    top = 4.dp
                )
            )
        }
    }
}

// 시간 포맷팅 헬퍼 함수
private fun formatTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    return dateFormat.format(Date(timestamp))
}

// 미리보기
@Preview(showBackground = true)
@Composable
private fun MessageItemPreview_User() {
    Graduation_projectTheme {
        MessageItem(
            message = MessageUiModel(
                id = "1",
                text = "안녕하세요! 오늘 날씨가 좋네요.",
                isFromUser = true,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageItemPreview_AI() {
    Graduation_projectTheme {
        MessageItem(
            message = MessageUiModel(
                id = "2",
                text = "안녕하세요! 네, 오늘 날씨가 정말 화창하네요. 산책하기 좋은 날이에요. 오늘 하루는 어떻게 보내셨나요?",
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
