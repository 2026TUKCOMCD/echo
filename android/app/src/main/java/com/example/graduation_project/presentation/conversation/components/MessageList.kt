package com.example.graduation_project.presentation.conversation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.ui.theme.Dimens
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 대화 메시지 목록을 표시하는 컴포넌트
 *
 * ## 주요 기능
 * - LazyColumn으로 많은 메시지도 효율적으로 표시
 * - 새 메시지 추가 시 자동 스크롤
 * - 빈 상태일 때 안내 메시지 표시
 *
 * ## LazyColumn vs Column
 * - Column: 모든 아이템을 한 번에 렌더링 (적은 수에 적합)
 * - LazyColumn: 화면에 보이는 아이템만 렌더링 (많은 수에 적합)
 *
 * ## reverseLayout
 * - true: 최신 메시지가 아래에 (채팅 앱 스타일)
 * - 스크롤 없이 새 메시지가 바로 보임
 */
@Composable
fun MessageList(
    messages: List<MessageUiModel>,
    modifier: Modifier = Modifier
) {
    // 스크롤 상태 기억
    val listState = rememberLazyListState()

    // 새 메시지가 추가되면 맨 아래로 스크롤
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // reverseLayout이므로 index 0이 맨 아래
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            // 빈 상태: 안내 메시지 표시
            EmptyMessagePlaceholder(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 메시지 목록
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // reverseLayout: 최신 메시지가 아래에 표시
                reverseLayout = true,
                contentPadding = PaddingValues(
                    horizontal = Dimens.SpacingMedium,
                    vertical = Dimens.SpacingSmall
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                // reversed()로 최신 메시지가 먼저 오도록
                items(
                    items = messages.reversed(),
                    key = { it.id }  // 고유 키로 효율적인 업데이트
                ) { message ->
                    MessageItem(message = message)
                }
            }
        }
    }
}

/**
 * 메시지가 없을 때 표시되는 안내 컴포넌트
 */
@Composable
private fun EmptyMessagePlaceholder(
    modifier: Modifier = Modifier
) {
    Text(
        text = "아래 버튼을 눌러\n대화를 시작해보세요",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpacingXLarge)
    )
}

// 미리보기: 빈 상태
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun MessageListPreview_Empty() {
    Graduation_projectTheme {
        MessageList(messages = emptyList())
    }
}

// 미리보기: 메시지 있음
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun MessageListPreview_WithMessages() {
    Graduation_projectTheme {
        MessageList(
            messages = listOf(
                MessageUiModel(
                    id = "1",
                    text = "안녕하세요! 오늘 하루는 어떠셨나요?",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis() - 60000
                ),
                MessageUiModel(
                    id = "2",
                    text = "좋았어요. 산책도 다녀왔어요.",
                    isFromUser = true,
                    timestamp = System.currentTimeMillis() - 30000
                ),
                MessageUiModel(
                    id = "3",
                    text = "산책하셨군요! 날씨가 좋으니까 기분이 상쾌하셨겠어요. 어디로 산책 다녀오셨나요?",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        )
    }
}
