package com.example.graduation_project.presentation.model

/**
 * 개별 메시지를 나타내는 데이터 클래스
 * @param id 메시지 고유 ID (중복 방지)
 * @param text 메시지 내용
 * @param isFromUser true면 사용자 메시지, false면 AI 메시지
 * @param timestamp 메시지 생성 시간 (밀리초)
 */
data class MessageUiModel(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long
)

/**
 * 대화 화면의 전체 UI 상태
 * - ViewModel에서 이 상태를 업데이트하면 화면이 자동으로 다시 그려짐
 * - 불변(immutable) 객체로, 상태 변경 시 copy()로 새 객체 생성
 *
 * @param conversationState 대화 전체 생명주기 상태 (ConversationState sealed class)
 * @param sessionId 현재 대화 세션 ID (서버에서 발급)
 * @param voiceAmplitude 음성 볼륨 (0.0 ~ 1.0, 이퀄라이저 애니메이션용)
 * @param messages 대화 메시지 목록
 * @param currentUserSpeech 실시간 음성 인식 텍스트 (녹음 중 표시)
 * @param userName 사용자 이름 (인사말에 표시)
 * @param errorMessage 에러 메시지 (null이면 에러 없음)
 */
data class ConversationUiState(
    val conversationState: ConversationState = ConversationState.Idle,
    val sessionId: String? = null,
    val voiceAmplitude: Float = 0f,
    val messages: List<MessageUiModel> = emptyList(),
    val currentUserSpeech: String? = null,
    val userName: String? = null,
    val errorMessage: String? = null
) {
    val isConversationActive: Boolean
        get() = conversationState !is ConversationState.Idle
             && conversationState !is ConversationState.Ended

    val isLoading: Boolean
        get() = conversationState is ConversationState.Sending
}
