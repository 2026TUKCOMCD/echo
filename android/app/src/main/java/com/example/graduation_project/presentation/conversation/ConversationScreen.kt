package com.example.graduation_project.presentation.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.conversation.components.ConversationControls
import com.example.graduation_project.presentation.conversation.components.MessageList
import com.example.graduation_project.presentation.conversation.components.VoiceStatusIndicator
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.presentation.model.VoiceStatus
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 대화 메인 화면
 *
 * ## 화면 구성
 * ┌─────────────────────────┐
 * │      TopAppBar         │  <- 앱 타이틀
 * ├─────────────────────────┤
 * │   VoiceStatusIndicator │  <- 음성 상태 표시
 * ├─────────────────────────┤
 * │                         │
 * │      MessageList        │  <- 대화 메시지 (스크롤 가능)
 * │       (weight=1f)       │
 * │                         │
 * ├─────────────────────────┤
 * │  ConversationControls  │  <- 시작/종료 버튼
 * └─────────────────────────┘
 *
 * ## 상태 관리
 * - ViewModel에서 상태를 관리
 * - collectAsState()로 상태 변화 감지
 * - 상태가 바뀌면 자동으로 UI 업데이트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel = viewModel()
) {
    // ViewModel의 상태를 Compose State로 변환
    val uiState by viewModel.uiState.collectAsState()

    // Snackbar 상태 (에러 메시지 표시용)
    val snackbarHostState = remember { SnackbarHostState() }

    // 에러 메시지가 있으면 Snackbar 표시
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    // 화면 구성
    ConversationScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onStartClick = viewModel::startConversation,
        onEndClick = viewModel::endConversation
    )
}

/**
 * 화면 내용 (상태를 받아서 UI 렌더링)
 * - 테스트와 미리보기를 위해 분리
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreenContent(
    uiState: ConversationUiState,
    snackbarHostState: SnackbarHostState,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Scaffold(
        // 상단 앱바
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI 대화",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        // 스낵바 (에러 메시지 표시)
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 음성 상태 표시
            VoiceStatusIndicator(
                status = uiState.voiceStatus
            )

            // 대화 메시지 목록 (남은 공간 모두 사용)
            MessageList(
                messages = uiState.messages,
                modifier = Modifier.weight(1f)
            )

            // 대화 제어 버튼
            ConversationControls(
                isConversationActive = uiState.isConversationActive,
                isLoading = uiState.isLoading,
                onStartClick = onStartClick,
                onEndClick = onEndClick
            )
        }
    }
}

// 미리보기: 초기 상태
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Initial() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}

// 미리보기: 대화 진행 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Active() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(
                isConversationActive = true,
                voiceStatus = VoiceStatus.LISTENING,
                messages = listOf(
                    MessageUiModel(
                        id = "1",
                        text = "안녕하세요! 오늘 하루는 어떠셨나요?",
                        isFromUser = false,
                        timestamp = System.currentTimeMillis() - 60000
                    ),
                    MessageUiModel(
                        id = "2",
                        text = "좋았어요. 아침에 산책도 다녀왔어요.",
                        isFromUser = true,
                        timestamp = System.currentTimeMillis()
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}

// 미리보기: 로딩 중
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Loading() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(isLoading = true),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}
