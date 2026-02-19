package com.example.graduation_project.presentation.model

/**
 * 대화 전체 생명주기 상태를 나타내는 sealed class
 *
 * ## 상태 전이 흐름
 * IDLE → startConversation() → SENDING → (성공) PLAYING
 *                                       → (실패) IDLE
 *
 * PLAYING → 재생 완료 → LISTENING
 *
 * LISTENING → VAD 발화 감지 → RECORDING
 *           → endConversation() → SENDING → (성공) ENDED
 *
 * RECORDING → sendMessage() → SENDING → (성공) PLAYING
 *                                      → (실패) LISTENING
 *
 * ENDED → 사용자가 시작 버튼 클릭 → IDLE
 */
sealed class ConversationState {
    data object Idle      : ConversationState()  // 대화 미시작 / ENDED 복귀 후
    data object Listening : ConversationState()  // 마이크 활성, 발화 대기
    data object Recording : ConversationState()  // VAD 발화 감지, 녹음 중
    data object Sending   : ConversationState()  // 서버 전송 + AI 응답 대기
    data object Playing   : ConversationState()  // TTS 재생 중
    data object Ended     : ConversationState()  // 대화 종료 완료
}
