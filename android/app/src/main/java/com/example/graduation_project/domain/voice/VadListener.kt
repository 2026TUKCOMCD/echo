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
     * 발화 중 PCM 조각 (16-bit 모노, 실시간 전송용). onSpeechStart 뒤부터 onSpeechEnd 전까지 순서대로 온다.
     * 무음 꼬리는 걸러져 있어, 이어 붙이면 onSpeechEnd의 WAV 본문과 같다.
     */
    fun onSpeechAudio(pcm: ByteArray) {}

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
