package com.tukcomcd.echo.presentation.model

/**
 * 대화 중 발생 가능한 오류 상태
 *
 * CharacterState의 오류 상태를 분리하여 관리
 * 정상 상태는 ConversationState로 표현됨
 */
sealed class ConversationError {

    /**
     * 발화 인식 실패 (VAD_TIMEOUT + STT_ERROR 통합)
     * - VAD: 묵음 or 너무 짧은 발화 (서버 전송 전)
     * - STT: 서버가 음성 인식 실패 (서버 전송 후)
     * → 자동으로 LISTENING 복귀
     */
    data object SpeechUnrecognized : ConversationError()

    /**
     * 네트워크 연결 실패
     * - 서버 전송 시 connect timeout
     * - 자동 재시도 1회 후 실패 시 사용자에게 버튼 노출
     * - 버튼 3회 실패 시 고객센터 연결
     */
    data object NetworkError : ConversationError()

    /**
     * 서버 처리 실패 (500 / 503)
     * - 500: 3초 대기 후 자동 재시도 1회
     * - 503: 5초 대기 후 자동 재시도 1회
     * - 이후 사용자 버튼 최대 3회
     * - 3회 초과 시 버튼 비활성화 + 고객센터 연결
     */
    data object ServerError : ConversationError()

    /**
     * 오디오 재생 실패 (안전망)
     * - 현재: 텍스트 폴백으로 단순 처리
     * - 추후: 팀 설계 3단계 재시도 로직 추가 예정
     */
    data object TtsError : ConversationError()
}
