package com.example.graduation_project.presentation.voice

import com.example.graduation_project.domain.voice.VadException
import java.io.File

/**
 * 음성 녹음 UI 상태
 *
 * [T2.2-4] isPreparing, isProcessing, lastAudioFile 필드 추가
 */
data class VoiceRecordingState(
    /** 녹음 중 여부 */
    val isRecording: Boolean = false,

    /** 음성 감지 중 여부 */
    val isSpeechDetected: Boolean = false,

    /** 에러 메시지 */
    val error: VadException? = null,

    /** 마지막으로 녹음된 WAV 오디오 데이터 */
    val lastRecordedAudio: ByteArray? = null,

    /** 준비 중 여부 (VAD 초기화 중) */
    val isPreparing: Boolean = false,

    /** 파일 처리 중 여부 (WAV 저장 중) */
    val isProcessing: Boolean = false,

    /** 마지막으로 저장된 오디오 파일 */
    val lastAudioFile: File? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceRecordingState) return false
        return isRecording == other.isRecording &&
                isSpeechDetected == other.isSpeechDetected &&
                error == other.error &&
                isPreparing == other.isPreparing &&
                isProcessing == other.isProcessing &&
                lastAudioFile == other.lastAudioFile &&
                lastRecordedAudio?.contentEquals(other.lastRecordedAudio ?: byteArrayOf())
                    ?: (other.lastRecordedAudio == null)
    }

    override fun hashCode(): Int {
        var result = isRecording.hashCode()
        result = 31 * result + isSpeechDetected.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (lastRecordedAudio?.contentHashCode() ?: 0)
        result = 31 * result + isPreparing.hashCode()
        result = 31 * result + isProcessing.hashCode()
        result = 31 * result + (lastAudioFile?.hashCode() ?: 0)
        return result
    }
}
