package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 대화 시작/종료 버튼 컴포넌트
 *
 * ## 상태별 동작
 * - 대화 미진행 (isConversationActive = false): "대화 시작" 버튼 표시
 * - 대화 진행 중 (isConversationActive = true): "대화 종료" 버튼 표시
 * - 로딩 중: 버튼 비활성화 + 로딩 인디케이터 표시
 *
 * ## 접근성
 * - 큰 버튼 (64dp 높이, 최소 200dp 너비)
 * - 명확한 레이블 ("대화 시작하기", "대화 종료하기")
 * - TalkBack에서 현재 상태와 버튼 역할 안내
 */
@Composable
fun ConversationControls(
    isConversationActive: Boolean,
    isLoading: Boolean,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 버튼 텍스트
    val buttonText = if (isConversationActive) "대화 종료하기" else "대화 시작하기"

    // 버튼 클릭 핸들러
    val onClick = if (isConversationActive) onEndClick else onStartClick

    // TalkBack 접근성 설명
    val accessibilityDescription = if (isConversationActive) {
        "대화 종료 버튼. 현재 대화가 진행 중입니다. 눌러서 대화를 종료하세요."
    } else {
        "대화 시작 버튼. 눌러서 AI와 대화를 시작하세요."
    }

    // 버튼 색상 (시작: Primary, 종료: Error)
    val buttonColors = if (isConversationActive) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.SpacingMedium,
                vertical = Dimens.SpacingMedium
            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = !isLoading,  // 로딩 중에는 비활성화
            modifier = Modifier
                .height(Dimens.ButtonHeight)
                .widthIn(min = Dimens.ButtonMinWidth)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityDescription
                },
            colors = buttonColors,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                // 로딩 인디케이터
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                // 버튼 텍스트
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// 미리보기: 대화 시작 버튼
@Preview(showBackground = true)
@Composable
private fun ConversationControlsPreview_Start() {
    Graduation_projectTheme {
        ConversationControls(
            isConversationActive = false,
            isLoading = false,
            onStartClick = {},
            onEndClick = {}
        )
    }
}

// 미리보기: 대화 종료 버튼
@Preview(showBackground = true)
@Composable
private fun ConversationControlsPreview_End() {
    Graduation_projectTheme {
        ConversationControls(
            isConversationActive = true,
            isLoading = false,
            onStartClick = {},
            onEndClick = {}
        )
    }
}

// 미리보기: 로딩 중
@Preview(showBackground = true)
@Composable
private fun ConversationControlsPreview_Loading() {
    Graduation_projectTheme {
        ConversationControls(
            isConversationActive = false,
            isLoading = true,
            onStartClick = {},
            onEndClick = {}
        )
    }
}
