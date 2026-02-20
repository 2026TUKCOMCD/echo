package com.example.graduation_project.presentation.model

import com.example.graduation_project.presentation.character.CharacterState

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
 * 재생 세부 상태
 * voiceStatus == PLAYING일 때의 세부 단계를 나타냄
 *
 * [T2.3-2] 재생 상태 UI 연동
 */
enum class PlaybackStatus {
    NONE,       // 재생 비활성 (voiceStatus != PLAYING)
    PREPARING,  // Base64 디코딩 + 파일 저장 중
    PLAYING     // MediaPlayer 실제 재생 중
}

/**
 * 발화 인식 오류 타입
 */
enum class SpeechErrorType {
    NOT_DETECTED,  // 발화가 감지되지 않음
    TOO_SHORT,     // 발화가 너무 짧음
    STT_FAILED,    // 서버 STT 인식 실패
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
 * @param voiceAmplitude 음성 볼륨 (0.0 ~ 1.0, 이퀄라이저 애니메이션용)
 * @param messages 대화 메시지 목록
 * @param currentUserSpeech 실시간 음성 인식 텍스트 (녹음 중 표시)
 * @param userName 사용자 이름 (인사말에 표시)
 * @param errorMessage 에러 메시지 (null이면 에러 없음)
 * @param isAudioRetrying 오디오 재생 재시도 중 여부 [T2.3-3]
 * @param showAudioFallbackText 오디오 재생 실패 후 텍스트 폴백 표시 중 [T2.3-3]
 * @param audioFallbackText 폴백 텍스트 (AI 응답) [T2.3-3]
 * @param retryProgress 재시도 진행 상황 (예: "재시도 중 (1/3)") [T2.3-3]
 * @param characterState 캐릭터 애니메이션 상태
 * @param processingMessage PROCESSING 상태 오버레이 메시지 (3초 후 "잠시만요", 6초 후 "조금만 기다려주세요")
 * @param processingElapsedSeconds PROCESSING 상태 경과 시간 (초)
 * @param speechErrorMessage 발화 인식 실패 메시지
 * @param speechErrorHint 발화 인식 실패 힌트 (연속 실패 횟수에 따라 다른 힌트)
 * @param speechFailCount 발화 인식 연속 실패 횟수
 * @param userRetryCount 사용자 재시도 버튼 클릭 횟수 (네트워크/서버 오류)
 * @param isRetryButtonEnabled 재시도 버튼 활성화 여부
 * @param showContactSupport 고객센터 연결 버튼 표시 여부 (재시도 3회 초과)
 * @param showFarewellDialog 종료 확인 다이얼로그 표시 여부
 */
data class ConversationUiState(
    val isConversationActive: Boolean = false,
    val isLoading: Boolean = false,
    val sessionId: String? = null,
    val voiceStatus: VoiceStatus = VoiceStatus.IDLE,
    val playbackStatus: PlaybackStatus = PlaybackStatus.NONE,
    val voiceAmplitude: Float = 0f,
    val messages: List<MessageUiModel> = emptyList(),
    val currentUserSpeech: String? = null,
    val userName: String? = null,
    val errorMessage: String? = null,
    val isAudioRetrying: Boolean = false,
    val showAudioFallbackText: Boolean = false,
    val audioFallbackText: String? = null,
    val retryProgress: String? = null,
    // 캐릭터 상태
    val characterState: CharacterState = CharacterState.IDLE,
    // PROCESSING 오버레이
    val processingMessage: String? = null,
    val processingElapsedSeconds: Int = 0,
    // 발화 인식 실패
    val speechErrorMessage: String? = null,
    val speechErrorHint: String? = null,
    val speechFailCount: Int = 0,
    // 재시도 관련
    val userRetryCount: Int = 0,
    val isRetryButtonEnabled: Boolean = false,
    val showContactSupport: Boolean = false,
    // 종료 다이얼로그
    val showFarewellDialog: Boolean = false,
)
