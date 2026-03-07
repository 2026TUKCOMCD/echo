package com.tukcomcd.echo.domain.voice

/**
 * AudioPlayerManager 관련 예외
 *
 * [T2.3-1] AI 응답 음성 재생 구현
 */
sealed class AudioPlayException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /** Base64 디코딩 실패 */
    data class DecodeError(
        override val message: String = "음성 데이터 디코딩에 실패했습니다",
        override val cause: Throwable? = null
    ) : AudioPlayException(message, cause)

    /** MediaPlayer 재생 실패 */
    data class PlaybackError(
        override val message: String = "음성 재생에 실패했습니다",
        override val cause: Throwable? = null
    ) : AudioPlayException(message, cause)

    /** 알 수 없는 에러 */
    data class UnknownError(
        override val message: String = "알 수 없는 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : AudioPlayException(message, cause)
}
