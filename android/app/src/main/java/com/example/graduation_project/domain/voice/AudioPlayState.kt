package com.example.graduation_project.domain.voice

/**
 * AudioPlayerManager의 재생 상태
 *
 * 상태 흐름: Idle → Preparing → Playing → Completed / Error
 *
 * [T2.3-1] AI 응답 음성 재생 구현
 */
sealed class AudioPlayState {

    /** 초기 상태 - 재생 비활성 */
    data object Idle : AudioPlayState()

    /** 준비 중 - Base64 디코딩, 파일 저장 진행 */
    data object Preparing : AudioPlayState()

    /** 재생 중 - MediaPlayer 재생 진행 */
    data object Playing : AudioPlayState()

    /** 완료 - 재생 정상 종료 */
    data object Completed : AudioPlayState()

    /** 에러 발생 */
    data class Error(val exception: AudioPlayException) : AudioPlayState()
}
