package com.example.graduation_project.presentation.conversation

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.graduation_project.presentation.component.BreathingAnimation
import com.example.graduation_project.presentation.component.MicrophoneIcon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.R
import kotlinx.coroutines.delay
import com.example.graduation_project.presentation.conversation.components.AnimatedWebpImage
import com.example.graduation_project.presentation.model.ConversationError
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.permission.UnifiedPermissionHandler
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun ConversationScreen(
    onLogout: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ConversationViewModel = viewModel(factory = ConversationViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 앱이 백그라운드로 가면 마이크/스피커 중지, 복귀 시 발화 대기 재개
    // (화면 회전 등 구성 변경으로 인한 ON_STOP은 제외 - 대화 유지)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val isChangingConfigurations =
                        (context as? Activity)?.isChangingConfigurations == true
                    if (!isChangingConfigurations) {
                        viewModel.onAppBackgrounded()
                    }
                }
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> { /* 무시 */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 대화 중(Idle이 아닐 때)에는 화면이 자동으로 꺼지지 않도록 유지
    val view = LocalView.current
    val isConversationActive = uiState.conversationState !is ConversationState.Idle
    DisposableEffect(isConversationActive) {
        view.keepScreenOn = isConversationActive
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    // Ended 상태가 되면 웨이브 애니메이션 + 작별 메시지를 보여준 후 뒤로 이동
    LaunchedEffect(uiState.conversationState) {
        if (uiState.conversationState is ConversationState.Ended) {
            delay(2000L)
            onBack()
        }
    }

    // 대화 시작 실패 시 에러 메시지를 잠깐 보여준 뒤 홈으로 복귀
    LaunchedEffect(uiState.startFailed) {
        if (uiState.startFailed) {
            delay(2000L)
            viewModel.consumeStartFailedEvent()
            onBack()
        }
    }

    // 자동 시작은 화면당 1회만. 권한 다이얼로그로 content가 컴포지션에서 빠졌다가
    // 다시 들어오거나(설정 왕복), 회전으로 컴포지션이 재생성돼도 재발화하지 않도록
    // 플래그를 권한 게이트 "바깥"에 둔다.
    var autoStartRequested by rememberSaveable { mutableStateOf(false) }

    UnifiedPermissionHandler {
        // 권한 흐름이 끝나고 마이크 권한이 확인된 뒤에만 컴포즈되므로,
        // 세션 생성 / 알림 취소 / 위치·건강 수집 / TTS 재생이 권한 다이얼로그
        // 뒤에서 선행 실행되지 않는다.
        LaunchedEffect(Unit) {
            if (!autoStartRequested) {
                autoStartRequested = true
                viewModel.startConversation()
            }
        }

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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        containerColor = colors.bgPage,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 캐릭터 이미지 영역 (가로모드에서는 축소하여 버튼이 가려지지 않도록 함)
            CharacterWebpSection(
                state = uiState.conversationState,
                error = uiState.currentError,
                modifier = Modifier.size(if (isLandscape) 140.dp else 240.dp)
            )

            Spacer(Modifier.height(32.dp))

            // 내 차례 표시 (대기: 테두리 마이크 숨쉬기 / 말하는 중: 꽉 찬 마이크 + 파동)
            TurnMicIndicator(state = uiState.conversationState)

            // 파동이 칸 밖으로 최대 약 17dp까지 퍼지므로 글자와 닿지 않게 여유를 둠
            Spacer(Modifier.height(24.dp))

            // 상태 텍스트
            StateTextSection(
                state = uiState.conversationState,
                currentAiMessage = uiState.messages.lastOrNull { !it.isFromUser }?.text,
                currentUserSpeech = uiState.currentUserSpeech
            )

            Spacer(Modifier.height(32.dp))

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
private fun CharacterWebpSection(
    state: ConversationState,
    error: ConversationError?,
    modifier: Modifier = Modifier
) {
    val resId = when {
        error != null                      -> R.raw.echo_02_empathy
        state is ConversationState.Playing -> R.raw.echo_new
        state is ConversationState.Idle ||
        state is ConversationState.Ended   -> R.raw.echo_05_greeting
        else                               -> R.raw.echo_02_empathy
    }
    AnimatedWebpImage(resId = resId, modifier = modifier)
}

/**
 * 내 차례 마이크 표시 - "말씀해 주세요"(대기)와 "듣고 있어요"(말하는 중)를 글자 외에 모양으로도 구분
 * - 대기(Listening): 초록 테두리 원 + 초록 마이크, 2초 주기로 천천히 숨쉬기
 * - 말하는 중(Recording): 꽉 찬 초록 원 + 흰 마이크, 0.8초 빠른 숨쉬기 + 퍼지는 파동
 * - 그 외 상태: 같은 높이를 비워 두어 상태가 바뀔 때 화면이 위아래로 튀지 않게 함
 * 애니메이션을 끈 기기에서도 채움(테두리/꽉 참)과 문구 색으로 구분됨
 */
@Composable
private fun TurnMicIndicator(state: ConversationState) {
    val colors = LocalEchoColors.current
    val isListening = state is ConversationState.Listening
    val isSpeaking = state is ConversationState.Recording

    Box(modifier = Modifier.size(TURN_MIC_SIZE), contentAlignment = Alignment.Center) {
        when {
            isSpeaking -> {
                val pulse = rememberInfiniteTransition(label = "turnMicPulse")
                val progress by pulse.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                    label = "turnMicPulseProgress"
                )
                BreathingAnimation(durationMs = 800, minScale = 0.92f, maxScale = 1.08f) {
                    Box(
                        modifier = Modifier
                            .size(TURN_MIC_SIZE)
                            // 파동은 칸 밖까지 그리되 레이아웃 크기는 바꾸지 않음
                            .drawBehind {
                                val base = size.minDimension / 2
                                drawCircle(
                                    color = colors.accentGreen.copy(alpha = 0.35f * (1f - progress)),
                                    radius = base * (1f + 0.4f * progress),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                            .clip(CircleShape)
                            .background(colors.accentGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        MicrophoneIcon(tint = Color.White, size = 36.dp, contentDescription = null)
                    }
                }
            }
            isListening -> {
                BreathingAnimation(durationMs = 2000) {
                    Box(
                        modifier = Modifier
                            .size(TURN_MIC_SIZE)
                            .border(3.dp, colors.accentGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        MicrophoneIcon(tint = colors.accentGreen, size = 36.dp, contentDescription = null)
                    }
                }
            }
            else -> Unit
        }
    }
}

private val TURN_MIC_SIZE = 72.dp

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
        // Listening = 말을 기다리는 중, Recording = VAD가 발화를 감지해 듣는 중
        is ConversationState.Listening -> "말씀해 주세요"
        is ConversationState.Recording -> "듣고 있어요"
        is ConversationState.Ended -> "오늘 대화가 저장되었으니, 내일 또 봐요"
    }

    Text(
        text = stateLabel,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = OutfitFontFamily,
        // 듣는 중에만 마이크와 같은 초록으로 강조 (22sp 굵은 글씨라 대비 기준 3:1, accentGreen 3.83:1)
        color = if (state is ConversationState.Recording) colors.accentGreen else colors.textPrimary,
        textAlign = TextAlign.Center
    )

    val subText = when {
        // 사용자 실시간 발화 텍스트가 있으면 최우선 표시
        state is ConversationState.Listening && !currentUserSpeech.isNullOrBlank() ->
            currentUserSpeech
        // AI가 마지막으로 한 말: 재생 중뿐 아니라 응답을 기다리는 동안(Listening, Recording)에도 유지
        !currentAiMessage.isNullOrBlank() &&
            (state is ConversationState.Playing ||
                state is ConversationState.Listening ||
                state is ConversationState.Recording) ->
            currentAiMessage
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

    when {
        state is ConversationState.Ended -> { /* 종료 후 대기 중 — 버튼 없음 */ }
        state is ConversationState.Idle -> {
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
        }
        else -> {
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
