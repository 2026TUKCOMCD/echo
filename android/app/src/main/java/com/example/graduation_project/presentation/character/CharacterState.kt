package com.example.graduation_project.presentation.character

/**
 * 캐릭터 상태 정의
 *
 * [정상 흐름]
 * IDLE → LISTENING → PROCESSING → SPEAKING → (IDLE or FAREWELL)
 *
 * [오류 흐름]
 * LISTENING   → SPEECH_UNRECOGNIZED → LISTENING (자동 복귀)
 * PROCESSING  → NETWORK_ERROR / SERVER_ERROR
 * SPEAKING    → TTS_ERROR
 */
enum class CharacterState {

    // ── 정상 흐름 ──────────────────────────────────────────────

    /** 시작 버튼 누르기 전 대기 상태 */
    IDLE,

    /** VAD가 발화를 감지하고 있는 상태 (루프 재생) */
    LISTENING,

    /**
     * 서버 처리 중 상태 (루프 재생)
     * 내부적으로 STT → LLM → TTS 전부 포함
     * - 0~3초: 오버레이 없음
     * - 3~6초: "잠시만요"
     * - 6초~: "조금만 기다려주세요" + 점 애니메이션
     */
    PROCESSING,

    /** 서버로부터 받은 오디오를 재생 중인 상태 (루프 재생) */
    SPEAKING,

    /** 종료 확인 후 작별 애니메이션 재생 (1회 재생 후 종료) */
    FAREWELL,

    // ── 오류 흐름 ──────────────────────────────────────────────

    /**
     * 발화 인식 실패 (VAD_TIMEOUT + STT_ERROR 통합)
     * - VAD: 묵음 or 너무 짧은 발화 (서버 전송 전)
     * - STT: 서버가 음성 인식 실패 (서버 전송 후)
     * → 자동으로 LISTENING 복귀
     */
    SPEECH_UNRECOGNIZED,

    /**
     * 네트워크 연결 실패
     * - 서버 전송 시 connect timeout
     * - 자동 재시도 1회 후 실패 시 사용자에게 버튼 노출
     * - 버튼 3회 실패 시 고객센터 연결
     */
    NETWORK_ERROR,

    /**
     * 서버 처리 실패 (500 / 503)
     * - 500: 3초 대기 후 자동 재시도 1회
     * - 503: 5초 대기 후 자동 재시도 1회
     * - 이후 사용자 버튼 최대 3회
     * - 3회 초과 시 버튼 비활성화 + 고객센터 연결
     */
    SERVER_ERROR,

    /**
     * 오디오 재생 실패 (안전망)
     * - 현재: 텍스트 폴백으로 단순 처리
     * - 추후: 팀 설계 3단계 재시도 로직 추가 예정
     */
    TTS_ERROR,
}
