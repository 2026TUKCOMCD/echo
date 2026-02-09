package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 대화 시작 전 화면
 *
 * ## 화면 구성
 * - 중앙에 큰 AI 아이콘
 * - "안녕하세요" 인사 메시지
 * - "무엇이든 물어보세요" 안내 문구 (강조 색상)
 *
 * ## 접근성
 * - 큰 아이콘과 글씨로 가독성 향상
 * - 스크린 리더를 위한 contentDescription 제공
 */
@Composable
fun EmptyConversationView(
    userName: String? = null,
    modifier: Modifier = Modifier
) {
    // 인사 메시지 (이름이 있으면 포함)
    val greetingText = if (!userName.isNullOrBlank()) {
        "안녕하세요, ${userName}님"
    } else {
        "안녕하세요"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "AI 대화를 시작하려면 아래 버튼을 눌러주세요"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // AI 캐릭터 이미지 (큰 사이즈)
        AiCharacterImage(
            size = 200 .dp,
            enableFloatingAnimation = true
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

        // 인사 메시지
        Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))

        // 안내 문구 (강조 색상)
        Text(
            text = "함께 이야기 나눠요",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

// 미리보기
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun EmptyConversationViewPreview() {
    Graduation_projectTheme {
        EmptyConversationView(userName = "홍길동")
    }
}

@Preview(showBackground = true, showSystemUi = true, fontScale = 1.5f)
@Composable
private fun EmptyConversationViewPreview_LargeFont() {
    Graduation_projectTheme {
        EmptyConversationView(userName = "홍길동")
    }
}
