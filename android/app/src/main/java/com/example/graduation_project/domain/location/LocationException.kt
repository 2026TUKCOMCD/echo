package com.example.graduation_project.domain.location

sealed class LocationException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    data class PermissionDenied(
        override val message: String = "위치 권한이 필요합니다"
    ) : LocationException(message)

    data class Timeout(
        override val message: String = "위치 조회 시간이 초과되었습니다"
    ) : LocationException(message)

    data class UnknownError(
        override val message: String = "위치 조회 중 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : LocationException(message, cause)
}
