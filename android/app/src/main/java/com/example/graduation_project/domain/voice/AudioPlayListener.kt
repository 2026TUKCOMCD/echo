package com.example.graduation_project.domain.voice

/**
 * AudioPlayerManager의 이벤트 콜백 인터페이스
 *
 * [T2.3-1] AI 응답 음성 재생 구현
 */
interface AudioPlayListener {

    /** 재생 시작됨 (Preparing 완료 → Playing 진입) */
    fun onPlaybackStart()

    /** 재생 완료됨 (정상 종료) */
    fun onPlaybackComplete()

    /** 에러 발생 */
    fun onError(exception: AudioPlayException)
}
