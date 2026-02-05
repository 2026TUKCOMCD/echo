package com.example.graduation_project.presentation.voice

import com.example.graduation_project.domain.voice.VadException

/**
 * 음성 녹음 UI 상태
 */
data class VoiceRecordingState(
    /** 녹음 중 여부 */
    val isRecording: Boolean = false,

    /** 음성 감지 중 여부 */
    val isSpeechDetected: Boolean = false,

    /** 에러 메시지 */
    val error: VadException? = null,

    /** 마지막으로 녹음된 WAV 오디오 데이터 */
    val lastRecordedAudio: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceRecordingState) return false
        return isRecording == other.isRecording &&
                isSpeechDetected == other.isSpeechDetected &&
                error == other.error &&
                lastRecordedAudio?.contentEquals(other.lastRecordedAudio ?: byteArrayOf())
                    ?: (other.lastRecordedAudio == null)
    }

    override fun hashCode(): Int {
        var result = isRecording.hashCode()
        result = 31 * result + isSpeechDetected.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (lastRecordedAudio?.contentHashCode() ?: 0)
        return result
    }
}
