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
     * [T2.2-3] 어르신 말 속도 고려하여 3초로 설정
     * - 어르신은 단어 사이 쉼이 길 수 있음
     * - 너무 짧으면 문장 중간에 끊김 발생
     * - 권장 범위: 2000ms ~ 3500ms
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
     * AI 응답 재생 완료 후 녹음 시작까지의 대기 시간 (ms)
     *
     * - AI 발화 직후 바로 녹음이 시작되면 어색하므로 텀을 둠
     * - 어르신이 AI 말을 듣고 생각할 시간 확보
     */
    val postResponseDelayMs: Long = DEFAULT_POST_RESPONSE_DELAY_MS,

    /**
     * VAD 모드 (배경 소음 필터링 강도)
     *
     * [T2.2-3] 소음 필터링 설정
     * - NORMAL: 일반 가정 환경 (권장)
     * - AGGRESSIVE: 소음이 많은 환경
     * - VERY_AGGRESSIVE: 매우 시끄러운 환경
     */
    val mode: VadMode = VadMode.NORMAL,

    /**
     * 발화 종료 후 무음 꼬리 트리밍 임계값 (dBFS)
     *
     * Silero VAD의 isSpeech()는 silenceDurationMs 동안 hangover로 스무딩되어
     * true를 반환하므로, 실제 발화가 끝난 뒤에도 무음이 버퍼에 함께 쌓임.
     * WAV로 변환하기 직전 원본 PCM에서 프레임별 에너지를 다시 계산해
     * 이 값 미만인 뒤쪽 구간을 잘라낸다. (STT 환각 방지)
     */
    val silenceTrimThresholdDbfs: Double = DEFAULT_SILENCE_TRIM_THRESHOLD_DBFS,

    /**
     * 무음 트리밍 시 마지막 유효 프레임 뒤에 남겨둘 여유 시간 (ms)
     * 어미가 잘리지 않도록 약간의 여유를 둠
     */
    val silenceTrimPaddingMs: Int = DEFAULT_SILENCE_TRIM_PADDING_MS
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
        /** 기본 무음 지속 시간: 3초 (어르신 말 속도 고려, 넉넉한 침묵 허용) */
        const val DEFAULT_SILENCE_DURATION_MS = 3000

        /** 기본 최소 음성 지속 시간: 100ms (노이즈 필터링) */
        const val DEFAULT_SPEECH_DURATION_MS = 100

        /** 기본 최대 녹음 시간: 60초 */
        const val DEFAULT_MAX_RECORDING_DURATION_MS = 60_000L

        /** AI 응답 후 녹음 시작까지의 대기 시간: 1.5초 */
        const val DEFAULT_POST_RESPONSE_DELAY_MS = 1500L

        /** 기본 무음 트리밍 임계값: -40dBFS (일반적인 무음 판정 기준) */
        const val DEFAULT_SILENCE_TRIM_THRESHOLD_DBFS = -40.0

        /** 기본 무음 트리밍 여유 시간: 300ms (어미 보존) */
        const val DEFAULT_SILENCE_TRIM_PADDING_MS = 300
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
