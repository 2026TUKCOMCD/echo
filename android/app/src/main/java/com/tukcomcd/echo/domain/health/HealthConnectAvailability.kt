package com.tukcomcd.echo.domain.health

/**
 * Health Connect SDK 가용성 상태.
 * HealthConnectClient.getSdkStatus() 결과를 도메인 레이어 개념으로 래핑.
 */
sealed class HealthConnectAvailability {
    /** SDK 사용 가능 - Health Connect 설치됨, API 26+ */
    data object Available : HealthConnectAvailability()

    /**
     * SDK 사용 불가 - Health Connect 앱 설치/업데이트 필요
     * (SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) → Play Store 유도
     */
    data object NotInstalled : HealthConnectAvailability()

    /**
     * SDK 사용 불가 - 디바이스 자체가 지원 안 함
     * (SDK_UNAVAILABLE 또는 API < 26) → 폴백: HealthData null 전송
     */
    data object NotSupported : HealthConnectAvailability()
}
