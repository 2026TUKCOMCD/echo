package com.example.graduation_project.domain.voice

/**
 * VAD 설정 데이터 클래스
 * 어르신 대상 앱에 최적화된 기본값 사용
 *
 * [T2.2-3] VAD 파라미터 튜닝
 * - Silence threshold: 2초 (어르신 느린 발화 속도 고려)
 * - Speech duration: 100ms (배경 소음 필터링)
 * - Mode: NORMAL (일반 가정 환경 기준)
 */
data class VadConfig(
    /** 샘플 레이트 (Hz) - Silero VAD는 16000Hz 권장 */
    val sampleRate: Int = SAMPLE_RATE_16K,

    /** 프레임 크기 (samples) - 16kHz에서 512 권장 */
    val frameSize: Int = FRAME_SIZE_512,

    /**
     * 음성 종료로 판정하기 위한 무음 지속 시간 (ms)
     *
     * [T2.2-3] 어르신 말 속도 고려하여 2초로 설정
     * - 어르신은 단어 사이 쉼이 길 수 있음
     * - 너무 짧으면 문장 중간에 끊김 발생
     * - 권장 범위: 1500ms ~ 2000ms
     */
    val silenceDurationMs: Int = DEFAULT_SILENCE_DURATION_MS,

    /**
     * 음성 시작으로 판정하기 위한 최소 음성 지속 시간 (ms)
     *
     * [T2.2-3] 배경 소음 필터링을 위한 설정
     * - 100ms 미만의 짧은 소리는 노이즈로 판정
     * - 기침, 문 닫는 소리 등 순간적 소음 필터링
     * - 너무 길면 짧은 대답("네", "아니오") 누락 가능
     */
    val speechDurationMs: Int = DEFAULT_SPEECH_DURATION_MS,

    /**
     * 최대 녹음 시간 (ms)
     * 어르신의 긴 발화를 고려하여 60초로 설정
     */
    val maxRecordingDurationMs: Long = DEFAULT_MAX_RECORDING_DURATION_MS,

    /**
     * VAD 모드 (배경 소음 필터링 강도)
     *
     * [T2.2-3] 소음 필터링 설정
     * - NORMAL: 일반 가정 환경 (권장)
     * - AGGRESSIVE: 소음이 많은 환경
     * - VERY_AGGRESSIVE: 매우 시끄러운 환경
     */
    val mode: VadMode = VadMode.NORMAL
) {
    companion object {
        // 샘플 레이트
        const val SAMPLE_RATE_8K = 8000
        const val SAMPLE_RATE_16K = 16000

        // 프레임 크기
        const val FRAME_SIZE_256 = 256
        const val FRAME_SIZE_512 = 512
        const val FRAME_SIZE_768 = 768
        const val FRAME_SIZE_1024 = 1024
        const val FRAME_SIZE_1536 = 1536

        // 오디오 포맷
        const val CHANNELS_MONO = 1
        const val BITS_PER_SAMPLE = 16

        // [T2.2-3] 기본값 상수
        /** 기본 무음 지속 시간: 2초 (어르신 말 속도 고려) */
        const val DEFAULT_SILENCE_DURATION_MS = 2000

        /** 기본 최소 음성 지속 시간: 100ms (노이즈 필터링) */
        const val DEFAULT_SPEECH_DURATION_MS = 100

        /** 기본 최대 녹음 시간: 60초 */
        const val DEFAULT_MAX_RECORDING_DURATION_MS = 60_000L
    }
}

/**
 * VAD 모드 (배경 소음 필터링 강도)
 *
 * Silero VAD의 Mode와 매핑됨
 */
enum class VadMode {
    /** 일반 모드 - 가정 환경에 적합 */
    NORMAL,

    /** 공격적 모드 - 소음이 있는 환경 */
    AGGRESSIVE,

    /** 매우 공격적 모드 - 매우 시끄러운 환경 */
    VERY_AGGRESSIVE
}
