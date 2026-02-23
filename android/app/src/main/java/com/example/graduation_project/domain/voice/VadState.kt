package com.example.graduation_project.domain.voice

/**
 * VAD(Voice Activity Detection) 상태를 나타내는 sealed class
 */
sealed class VadState {
    /** VAD가 초기화되지 않은 상태 */
    data object Idle : VadState()

    /** VAD가 시작되어 음성을 감지 중인 상태 (무음) */
    data object Listening : VadState()

    /** 음성이 감지되어 녹음 중인 상태 */
    data object SpeechDetected : VadState()

    /** 음성이 종료되어 처리 대기 중인 상태 (WAV 포맷) */
    data class SpeechEnded(val wavData: ByteArray) : VadState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpeechEnded) return false
            return wavData.contentEquals(other.wavData)
        }

        override fun hashCode(): Int = wavData.contentHashCode()
    }

    /** VAD 처리 중 에러 발생 */
    data class Error(val exception: VadException) : VadState()

    /** VAD가 중지된 상태 */
    data object Stopped : VadState()
}
