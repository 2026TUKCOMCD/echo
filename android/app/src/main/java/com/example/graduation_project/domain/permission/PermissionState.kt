package com.example.graduation_project.domain.permission

/**
 * 권한 상태를 나타내는 sealed class
 */
sealed class PermissionState {
    /** 권한이 부여된 상태 */
    data object Granted : PermissionState()

    /** 권한이 거부된 상태 (다시 요청 가능) */
    data object Denied : PermissionState()

    /** 권한이 영구적으로 거부된 상태 (설정에서 직접 변경 필요) */
    data object PermanentlyDenied : PermissionState()

    /** 권한 요청 전 상태 */
    data object NotRequested : PermissionState()
}

/**
 * 위치 권한 상태를 나타내는 sealed class
 * 일반 PermissionState와 달리 CoarseOnly 상태를 포함
 */
sealed class LocationPermissionState {
    /** 정밀 위치 권한(FINE)이 부여된 상태 */
    data object Granted : LocationPermissionState()

    /** 대략적 위치 권한(COARSE)만 부여된 상태 - 위치 수집 불가 */
    data object CoarseOnly : LocationPermissionState()

    /** 권한이 거부된 상태 (다시 요청 가능) */
    data object Denied : LocationPermissionState()

    /** 권한이 영구적으로 거부된 상태 (설정에서 직접 변경 필요) */
    data object PermanentlyDenied : LocationPermissionState()

    /** 권한 요청 전 상태 */
    data object NotRequested : LocationPermissionState()
}

/**
 * 마이크 권한 관련 이벤트
 */
sealed class MicrophonePermissionEvent {
    /** 권한 요청 */
    data object RequestPermission : MicrophonePermissionEvent()

    /** 설정 화면으로 이동 */
    data object OpenSettings : MicrophonePermissionEvent()

    /** 권한 상태 변경 */
    data class PermissionResult(val isGranted: Boolean) : MicrophonePermissionEvent()
}

/**
 * 위치 권한 관련 이벤트
 */
sealed class LocationPermissionEvent {
    /** 권한 요청 */
    data object RequestPermission : LocationPermissionEvent()

    /** 설정 화면으로 이동 */
    data object OpenSettings : LocationPermissionEvent()

    /** 권한 상태 변경 */
    data class PermissionResult(val isGranted: Boolean) : LocationPermissionEvent()
}
