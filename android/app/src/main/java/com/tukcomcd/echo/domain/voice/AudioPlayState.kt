package com.tukcomcd.echo.domain.voice

/**
 * AudioPlayerManager의 재생 상태
 *
 * 상태 흐름: Idle → Preparing → Playing → Completed / Error
 *            Preparing → Retrying → Playing (재시도 성공)
 *                     → Error (재시도 실패)
 *
 * [T2.3-1] AI 응답 음성 재생 구현
 * [T2.3-3] 재생 에러 처리 및 재시도 로직
 */
sealed class AudioPlayState {

    /** 초기 상태 - 재생 비활성 */
    data object Idle : AudioPlayState()

    /** 준비 중 - Base64 디코딩, 파일 저장 진행 */
    data object Preparing : AudioPlayState()

    /** 재시도 중 - PlaybackError 발생 후 재시도 진행 중
     * @param currentAttempt 현재 재시도 횟수 (1, 2, 3)
     * @param maxAttempts 최대 재시도 횟수 (3)
     */
    data class Retrying(
        val currentAttempt: Int,
        val maxAttempts: Int
    ) : AudioPlayState()

    /** 재생 중 - MediaPlayer 재생 진행 */
    data object Playing : AudioPlayState()

    /** 완료 - 재생 정상 종료 */
    data object Completed : AudioPlayState()

    /** 에러 발생
     * @param exception 발생한 예외
     * @param isFallbackNeeded 텍스트 폴백 UI 표시 필요 여부 (기본: true)
     */
    data class Error(
        val exception: AudioPlayException,
        val isFallbackNeeded: Boolean = true
    ) : AudioPlayState()
}
