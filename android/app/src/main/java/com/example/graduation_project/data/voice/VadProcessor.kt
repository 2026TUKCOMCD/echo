package com.example.graduation_project.data.voice

import android.content.Context
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import com.example.graduation_project.domain.voice.VadMode
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate

/**
 * Silero VAD 래퍼 클래스
 * 음성/비음성 판정 기능 제공
 *
 * [T2.2-3] VAD 파라미터 튜닝 적용
 * - silenceDurationMs: 2초 (어르신 말 속도 고려)
 * - speechDurationMs: 100ms (배경 소음 필터링)
 * - mode: 설정 가능 (NORMAL/AGGRESSIVE/VERY_AGGRESSIVE)
 */
class VadProcessor(
    private val context: Context,
    private val config: VadConfig = VadConfig()
) : AutoCloseable {

    private var vad: VadSilero? = null

    /**
     * VAD 모델 초기화
     * @throws VadException.VadModelLoadError 모델 로드 실패
     */
    fun initialize() {
        try {
            vad = Vad.builder()
                .setContext(context)
                .setSampleRate(getSampleRate())
                .setFrameSize(getFrameSize())
                .setMode(getMode())
                .setSilenceDurationMs(config.silenceDurationMs)
                .setSpeechDurationMs(config.speechDurationMs)
                .build()
        } catch (e: Exception) {
            throw VadException.VadModelLoadError(cause = e)
        }
    }

    /**
     * 오디오 프레임이 음성인지 판정
     * @param audioFrame 16-bit PCM 오디오 프레임
     * @return true if speech detected
     */
    fun isSpeech(audioFrame: ShortArray): Boolean {
        return vad?.isSpeech(audioFrame) ?: false
    }

    /**
     * VAD가 초기화되었는지 확인
     */
    fun isInitialized(): Boolean = vad != null

    override fun close() {
        vad?.close()
        vad = null
    }

    private fun getSampleRate(): SampleRate = when (config.sampleRate) {
        VadConfig.SAMPLE_RATE_8K -> SampleRate.SAMPLE_RATE_8K
        VadConfig.SAMPLE_RATE_16K -> SampleRate.SAMPLE_RATE_16K
        else -> SampleRate.SAMPLE_RATE_16K
    }

    private fun getFrameSize(): FrameSize = when (config.frameSize) {
        VadConfig.FRAME_SIZE_256 -> FrameSize.FRAME_SIZE_256
        VadConfig.FRAME_SIZE_512 -> FrameSize.FRAME_SIZE_512
        VadConfig.FRAME_SIZE_768 -> FrameSize.FRAME_SIZE_768
        VadConfig.FRAME_SIZE_1024 -> FrameSize.FRAME_SIZE_1024
        VadConfig.FRAME_SIZE_1536 -> FrameSize.FRAME_SIZE_1536
        else -> FrameSize.FRAME_SIZE_512
    }

    /**
     * [T2.2-3] VadMode → Silero Mode 변환
     */
    private fun getMode(): Mode = when (config.mode) {
        VadMode.NORMAL -> Mode.NORMAL
        VadMode.AGGRESSIVE -> Mode.AGGRESSIVE
        VadMode.VERY_AGGRESSIVE -> Mode.VERY_AGGRESSIVE
    }
}
