package com.example.graduation_project.presentation.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.settings.VoiceSettingsDialog
import com.example.graduation_project.presentation.conversation.components.ActiveConversationView
import com.example.graduation_project.presentation.conversation.components.ConversationControls
import com.example.graduation_project.presentation.conversation.components.EmptyConversationView
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.ui.theme.Graduation_projectTheme

/**
 * 대화 메인 화면
 *
 * ## 화면 구성
 * ┌─────────────────────────┐
 * │   [조건부 표시]          │
 * │   - 대화 시작 전:        │
 * │     EmptyConversationView│  <- AI 아이콘 + 인사 메시지
 * │   - 대화 중:             │
 * │     ActiveConversationView│ <- 상태 + AI응답 + 동심원 애니메이션
 * ├─────────────────────────┤
 * │  ConversationControls  │  <- 시작/종료 버튼
 * └─────────────────────────┘
 *
 * ## 상태 관리
 * - ViewModel에서 상태를 관리
 * - collectAsState()로 상태 변화 감지
 * - 상태가 바뀌면 자동으로 UI 업데이트
 */
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

    // 설정 다이얼로그 상태
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 화면 구성
    ConversationScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onStartClick = viewModel::startConversation,
        onEndClick = viewModel::endConversation,
        onSettingsClick = { showSettingsDialog = true }
    )

    // 음성 설정 다이얼로그
    if (showSettingsDialog) {
        VoiceSettingsDialog(onDismiss = { showSettingsDialog = false })
    }
}

/**
 * 화면 내용 (상태를 받아서 UI 렌더링)
 * - 테스트와 미리보기를 위해 분리
 */
@Composable
private fun ConversationScreenContent(
    uiState: ConversationUiState,
    snackbarHostState: SnackbarHostState,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    // 따뜻한 느낌 + 고대비 색상 (어르신 접근성 고려)
    val backgroundColor = Color(0xFFFFFDF9)  // 아주 연한 아이보리

    Scaffold(
        containerColor = backgroundColor,
        // 스낵바 (에러 메시지 표시)
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 메인 콘텐츠 영역 (조건부 표시)
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // 대화 시작 전: 설정 버튼 (우측 상단)
                if (!uiState.isConversationActive) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .height(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color(0xFF666666)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "음성 설정"
                        )
                    }
                }

                if (uiState.isConversationActive) {
                    // 대화 중: 상태 + AI 응답 + 사용자 음성 + 동심원 애니메이션
                    val currentAiMessage = uiState.messages
                        .lastOrNull { !it.isFromUser }
                        ?.text

                    ActiveConversationView(
                        conversationState = uiState.conversationState,
                        currentAiMessage = currentAiMessage,
                        currentUserSpeech = uiState.currentUserSpeech,
                        voiceAmplitude = uiState.voiceAmplitude
                    )
                } else {
                    // 대화 시작 전: AI 아이콘 + 인사 메시지
                    EmptyConversationView(userName = uiState.userName)
                }
            }



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

// 미리보기: 초기 상태 (사용자 이름 포함)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Initial() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(userName = "홍길동"),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}

// 미리보기: 대화 진행 중 (듣고 있는 상태 + 실시간 음성 인식)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Listening() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(
                conversationState = ConversationState.Listening,
                currentUserSpeech = "오늘 공원에서 산책을...",
                messages = listOf(
                    MessageUiModel(
                        id = "1",
                        text = "안녕하세요! 오늘 하루는 어떠셨나요?",
                        isFromUser = false,
                        timestamp = System.currentTimeMillis() - 60000
                    )
                )
            ),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}

// 미리보기: AI가 말하고 있는 상태
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Playing() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(
                conversationState = ConversationState.Playing,
                messages = listOf(
                    MessageUiModel(
                        id = "1",
                        text = "산책하셨군요! 어디로 산책을 가셨나요? 날씨가 좋았으면 기분도 좋으셨겠어요.",
                        isFromUser = false,
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

// 미리보기: 전송 중 (로딩)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationScreenPreview_Sending() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(conversationState = ConversationState.Sending),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}
