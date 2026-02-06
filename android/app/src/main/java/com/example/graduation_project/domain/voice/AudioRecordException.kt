package com.example.graduation_project.domain.voice

/**
 * AudioRecordManager 관련 예외
 * VadException을 래핑하거나, 파일 I/O 관련 예외를 추가
 *
 * [T2.2-4] AudioRecordManager 구현
 */
sealed class AudioRecordException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /** VAD 관련 에러 (VadException 래핑) */
    data class VadError(
        val vadException: VadException,
        override val message: String = vadException.message,
        override val cause: Throwable? = vadException.cause
    ) : AudioRecordException(message, cause)

    /** 파일 저장 실패 */
    data class FileSaveError(
        override val message: String = "음성 파일 저장에 실패했습니다",
        override val cause: Throwable? = null
    ) : AudioRecordException(message, cause)

    /** 저장 공간 부족 */
    data class InsufficientStorageError(
        override val message: String = "저장 공간이 부족합니다",
        override val cause: Throwable? = null
    ) : AudioRecordException(message, cause)

    /** 알 수 없는 에러 */
    data class UnknownError(
        override val message: String = "알 수 없는 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : AudioRecordException(message, cause)
}
