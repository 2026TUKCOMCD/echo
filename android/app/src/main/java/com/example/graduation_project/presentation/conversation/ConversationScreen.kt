package com.example.graduation_project.presentation.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.presentation.permission.UnifiedPermissionHandler
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun ConversationScreen(
    onLogout: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ConversationViewModel = viewModel(factory = ConversationViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    // 화면 진입 시 자동으로 대화 시작
    LaunchedEffect(Unit) {
        viewModel.startConversation()
    }

    // Ended 상태가 되면 자동으로 뒤로 이동
    LaunchedEffect(uiState.conversationState) {
        if (uiState.conversationState is ConversationState.Ended) {
            onBack()
        }
    }

    UnifiedPermissionHandler {
        ConversationScreenContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onStartClick = viewModel::startConversation,
            onEndClick = viewModel::onFarewellButtonClicked,
            onRetryClick = viewModel::onUserRetryClicked
        )
    }

    if (uiState.showFarewellDialog) {
        FarewellDialog(
            onConfirm = viewModel::onFarewellConfirmed,
            onDismiss = viewModel::onFarewellCancelled
        )
    }
}

@Composable
private fun ConversationScreenContent(
    uiState: ConversationUiState,
    snackbarHostState: SnackbarHostState,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onRetryClick: () -> Unit = {}
) {
    val colors = LocalEchoColors.current
    Scaffold(
        containerColor = colors.bgPage,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // 캐릭터 이미지 영역
            StateIconSection(state = uiState.conversationState)

            Spacer(Modifier.height(32.dp))

            // 상태 텍스트
            StateTextSection(
                state = uiState.conversationState,
                currentAiMessage = uiState.messages.lastOrNull { !it.isFromUser }?.text,
                currentUserSpeech = uiState.currentUserSpeech
            )

            Spacer(Modifier.weight(1f))

            // 하단 버튼 영역
            BottomActionSection(
                state = uiState.conversationState,
                isLoading = uiState.isLoading,
                onStartClick = onStartClick,
                onEndClick = onEndClick
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StateIconSection(state: ConversationState) {
    val colors = LocalEchoColors.current
    val iconSize = 80.dp

    Box(
        modifier = Modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is ConversationState.Idle -> {
                // 아이콘 없음 — 홈 화면에서 캐릭터를 보여주므로 여기서는 비워둠
            }
            is ConversationState.Sending -> {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "처리 중",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(iconSize)
                )
            }
            is ConversationState.Playing -> {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .border(3.dp, colors.accentCoral, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "재생 중",
                        tint = colors.accentCoral,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            is ConversationState.Recording, is ConversationState.Listening -> {
                val isListening = state is ConversationState.Listening
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .then(
                            if (isListening)
                                Modifier.border(3.dp, colors.accentGreen, CircleShape)
                            else
                                Modifier.background(colors.accentGreen, CircleShape)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isListening) "듣고 있음" else "녹음 중",
                        tint = if (isListening) colors.accentGreen else Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            else -> {
                CircularProgressIndicator(
                    color = colors.accentGreen,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun StateTextSection(
    state: ConversationState,
    currentAiMessage: String?,
    currentUserSpeech: String?
) {
    val colors = LocalEchoColors.current
    val stateLabel = when (state) {
        is ConversationState.Idle -> "대화를 시작해보세요"
        is ConversationState.Sending -> "처리 중..."
        is ConversationState.Playing -> "에코가 말하고 있어요"
        is ConversationState.Recording -> "말씀해주세요"
        is ConversationState.Listening -> "듣고 있어요"
        else -> ""
    }

    Text(
        text = stateLabel,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFontFamily,
        color = colors.textPrimary,
        textAlign = TextAlign.Center
    )

    val subText = when {
        state is ConversationState.Playing && !currentAiMessage.isNullOrBlank() ->
            currentAiMessage.take(80) + if (currentAiMessage.length > 80) "…" else ""
        state is ConversationState.Listening && !currentUserSpeech.isNullOrBlank() ->
            currentUserSpeech
        else -> null
    }

    if (subText != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = subText,
            fontSize = 18.sp,
            fontFamily = OutfitFontFamily,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}

@Composable
private fun BottomActionSection(
    state: ConversationState,
    isLoading: Boolean,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    val isActive = state !is ConversationState.Idle && state !is ConversationState.Ended

    if (!isActive) {
        Button(
            onClick = onStartClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentGreen,
                contentColor = Color.White
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text("대화 시작", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFontFamily)
            }
        }
    } else {
        val isSending = state is ConversationState.Sending
        Button(
            onClick = onEndClick,
            enabled = !isSending,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSending) colors.bgMuted else colors.accentRed,
                contentColor = if (isSending) colors.textTertiary else Color.White,
                disabledContainerColor = colors.bgMuted,
                disabledContentColor = colors.textTertiary
            )
        ) {
            Text("대화 종료", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFontFamily)
        }
    }
}

@Composable
private fun FarewellDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEchoColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.bgCard
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "대화를 마치시겠어요?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "오늘 대화 내용은 일기로 저장됩니다",
                    fontSize = 15.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textSecondary
                        ),
                        border = BorderStroke(1.dp, colors.borderSubtle)
                    ) {
                        Text("이어하기", fontSize = 17.sp, fontFamily = OutfitFontFamily)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("마치기", fontSize = 17.sp, fontFamily = OutfitFontFamily)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationIdlePreview() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConversationListeningPreview() {
    Graduation_projectTheme {
        ConversationScreenContent(
            uiState = ConversationUiState(
                conversationState = ConversationState.Listening,
                currentUserSpeech = "오늘 공원에서 산책을..."
            ),
            snackbarHostState = SnackbarHostState(),
            onStartClick = {},
            onEndClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FarewellDialogPreview() {
    Graduation_projectTheme {
        FarewellDialog(onConfirm = {}, onDismiss = {})
    }
}
