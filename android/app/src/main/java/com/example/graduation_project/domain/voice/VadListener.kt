package com.example.graduation_project.domain.voice

/**
 * VAD 이벤트 콜백 인터페이스
 */
interface VadListener {
    /**
     * 음성 시작 감지
     */
    fun onSpeechStart()

    /**
     * 음성 종료 감지
     * @param wavData WAV 포맷의 녹음된 음성 데이터
     */
    fun onSpeechEnd(wavData: ByteArray)

    /**
     * VAD 처리 중 에러 발생
     * @param exception 발생한 예외
     */
    fun onError(exception: VadException)
}
