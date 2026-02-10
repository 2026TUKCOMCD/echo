package com.example.graduation_project.presentation.model

/**
 * 음성 상태를 나타내는 열거형
 * - 화면에서 현재 음성 처리 상태를 표시하는 데 사용
 */
enum class VoiceStatus {
    IDLE,       // 대기 중 (대화 시작 전 또는 종료 후)
    LISTENING,  // 사용자 음성을 듣고 있음
    RECORDING,  // 음성을 녹음 중
    PLAYING     // AI 응답을 재생 중
}

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
 * @param isConversationActive 대화가 진행 중인지 여부
 * @param isLoading API 호출 등 로딩 중인지 여부
 * @param sessionId 현재 대화 세션 ID (서버에서 발급)
 * @param voiceStatus 현재 음성 상태
 * @param messages 대화 메시지 목록
 * @param errorMessage 에러 메시지 (null이면 에러 없음)
 */
data class ConversationUiState(
    val isConversationActive: Boolean = false,
    val isLoading: Boolean = false,
    val sessionId: String? = null,
    val voiceStatus: VoiceStatus = VoiceStatus.IDLE,
    val messages: List<MessageUiModel> = emptyList(),
    val errorMessage: String? = null
)
