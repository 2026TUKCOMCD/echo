package com.tukcomcd.echo.domain.voice

/**
 * AudioPlayerManager의 이벤트 콜백 인터페이스
 *
 * [T2.3-1] AI 응답 음성 재생 구현
 * [T2.3-3] 재생 에러 처리 및 재시도 로직
 */
interface AudioPlayListener {

    /** 재생 시작됨 (Preparing 완료 → Playing 진입) */
    fun onPlaybackStart()

    /** 재생 완료됨 (정상 종료) */
    fun onPlaybackComplete()

    /** 재시도 시작됨 (PlaybackError 발생 후 자동 재시도)
     * @param currentAttempt 현재 재시도 횟수 (1, 2, 3)
     * @param maxAttempts 최대 재시도 횟수 (3)
     */
    fun onRetrying(currentAttempt: Int, maxAttempts: Int)

    /** 최종 에러 발생 (재시도 소진 또는 영구 에러)
     * @param exception 발생한 예외
     * @param isFallbackNeeded 텍스트 폴백 UI 표시 필요 여부 (기본: true)
     */
    fun onError(exception: AudioPlayException, isFallbackNeeded: Boolean = true)
}
