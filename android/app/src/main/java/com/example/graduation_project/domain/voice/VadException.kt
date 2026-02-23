package com.example.graduation_project.domain.voice

/**
 * VAD 관련 예외를 나타내는 sealed class
 */
sealed class VadException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /** 마이크 권한 없음 */
    data class PermissionDenied(
        override val message: String = "마이크 권한이 필요합니다"
    ) : VadException(message)

    /** AudioRecord 초기화 실패 */
    data class AudioRecordInitError(
        override val message: String = "오디오 녹음 초기화에 실패했습니다",
        override val cause: Throwable? = null
    ) : VadException(message, cause)

    /** VAD 모델 로드 실패 */
    data class VadModelLoadError(
        override val message: String = "음성 감지 모델 로드에 실패했습니다",
        override val cause: Throwable? = null
    ) : VadException(message, cause)

    /** 녹음 중 에러 */
    data class RecordingError(
        override val message: String = "녹음 중 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : VadException(message, cause)

    /** 알 수 없는 에러 */
    data class UnknownError(
        override val message: String = "알 수 없는 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : VadException(message, cause)
}
