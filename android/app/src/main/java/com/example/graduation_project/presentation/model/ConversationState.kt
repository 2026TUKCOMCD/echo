package com.example.graduation_project.presentation.model

/**
 * 대화 전체 생명주기 상태를 나타내는 sealed class
 *
 * ## 상태 전이 흐름
 * IDLE → startConversation() → SENDING → (성공) PLAYING
 *                                       → (실패) IDLE
 *
 * PLAYING → 재생 완료 → LISTENING
 *         → endConversation() → SENDING (재생 중에도 대화 종료 가능)
 *
 * LISTENING → VAD 발화 감지 → RECORDING
 *           → endConversation() → SENDING → (성공) ENDED
 *
 * RECORDING → sendMessage() → SENDING → (성공) PLAYING
 *                                      → (실패) LISTENING
 *           → 녹음 오류/백그라운드 복귀 → LISTENING (발화 대기로 복구)
 *
 * ENDED → 사용자가 시작 버튼 클릭 → IDLE
 */
sealed class ConversationState {

    /**
     * 현재 상태에서 [next] 상태로의 전이가 허용되는지 반환합니다.
     * 위 상태 전이 흐름도를 기준으로 각 subtype이 구현합니다.
     */
    abstract fun canTransitionTo(next: ConversationState): Boolean

    data object Idle : ConversationState() {
        // startConversation() 호출 시에만 Sending으로 전이
        override fun canTransitionTo(next: ConversationState) = next is Sending
    }

    data object Listening : ConversationState() {
        // VAD 발화 감지 → Recording, endConversation() → Sending
        override fun canTransitionTo(next: ConversationState) =
            next is Recording || next is Sending
    }

    data object Recording : ConversationState() {
        // sendMessage() → Sending, 녹음 오류/백그라운드 복귀 → Listening
        override fun canTransitionTo(next: ConversationState) =
            next is Sending || next is Listening
    }

    data object Sending : ConversationState() {
        // Playing  : startConversation/sendMessage 성공
        // Idle     : startConversation 실패
        // Ended    : endConversation 성공
        // Listening: sendMessage/endConversation 실패
        override fun canTransitionTo(next: ConversationState) =
            next is Playing || next is Idle || next is Ended || next is Listening
    }

    data object Playing : ConversationState() {
        // TTS 재생 완료 → Listening, 재생 중 endConversation() → Sending
        override fun canTransitionTo(next: ConversationState) =
            next is Listening || next is Sending
    }

    data object Ended : ConversationState() {
        // 사용자가 시작 버튼 클릭 → 초기화
        override fun canTransitionTo(next: ConversationState) = next is Idle
    }
}
