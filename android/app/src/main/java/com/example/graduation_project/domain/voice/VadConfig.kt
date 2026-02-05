package com.example.graduation_project.domain.voice

/**
 * VAD 설정 데이터 클래스
 * 어르신 대상 앱에 최적화된 기본값 사용
 */
data class VadConfig(
    /** 샘플 레이트 (Hz) - Silero VAD는 16000Hz 권장 */
    val sampleRate: Int = SAMPLE_RATE_16K,

    /** 프레임 크기 (samples) - 16kHz에서 512 권장 */
    val frameSize: Int = FRAME_SIZE_512,

    /**
     * 음성 종료로 판정하기 위한 무음 지속 시간 (ms)
     * 어르신의 느린 발화 속도를 고려하여 800ms로 설정
     */
    val silenceDurationMs: Int = 800,

    /**
     * 음성 시작으로 판정하기 위한 음성 지속 시간 (ms)
     * 너무 민감하지 않도록 100ms로 설정
     */
    val speechDurationMs: Int = 100,

    /**
     * 최대 녹음 시간 (ms)
     * 어르신의 긴 발화를 고려하여 60초로 설정
     */
    val maxRecordingDurationMs: Long = 60_000L
) {
    companion object {
        const val SAMPLE_RATE_8K = 8000
        const val SAMPLE_RATE_16K = 16000

        const val FRAME_SIZE_256 = 256
        const val FRAME_SIZE_512 = 512
        const val FRAME_SIZE_768 = 768
        const val FRAME_SIZE_1024 = 1024
        const val FRAME_SIZE_1536 = 1536

        const val CHANNELS_MONO = 1
        const val BITS_PER_SAMPLE = 16
    }
}
