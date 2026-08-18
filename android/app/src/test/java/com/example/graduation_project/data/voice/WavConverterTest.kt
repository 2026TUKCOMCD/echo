package com.example.graduation_project.data.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WavConverter.trimTrailingSilence 검증
 *
 * STT 환각(hallucination) 방지를 위해, 발화 종료 후 VAD hangover로 인해
 * 버퍼에 쌓인 무음 꼬리를 잘라내는 로직을 테스트한다.
 */
class WavConverterTest {

    private val sampleRate = 16000
    private val samplesPerFrame = 512
    private val frameSizeBytes = samplesPerFrame * 2 // 16-bit PCM
    private val thresholdDbfs = -40.0

    /** frameDurationMs = 512 / 16000 * 1000 = 32ms */
    private val frameDurationMs = samplesPerFrame.toDouble() / sampleRate * 1000

    @Test
    fun `trailing silence frames are trimmed`() {
        val loudFrames = repeatFrame(loudFrame(), 5)
        val silenceFrames = repeatFrame(silenceFrame(), 20)
        val pcmData = loudFrames + silenceFrames

        val result = WavConverter.trimTrailingSilence(
            pcmData = pcmData,
            sampleRate = sampleRate,
            frameSizeBytes = frameSizeBytes,
            thresholdDbfs = thresholdDbfs,
            paddingMs = 0
        )

        assertEquals(loudFrames.size, result.size)
        assertArrayEquals(loudFrames, result)
    }

    @Test
    fun `padding after last loud frame is preserved`() {
        val loudFrames = repeatFrame(loudFrame(), 5)
        val silenceFrames = repeatFrame(silenceFrame(), 20)
        val pcmData = loudFrames + silenceFrames
        val paddingMs = (frameDurationMs * 3).toInt() // 3프레임 분량

        val result = WavConverter.trimTrailingSilence(
            pcmData = pcmData,
            sampleRate = sampleRate,
            frameSizeBytes = frameSizeBytes,
            thresholdDbfs = thresholdDbfs,
            paddingMs = paddingMs
        )

        assertEquals((5 + 3) * frameSizeBytes, result.size)
    }

    @Test
    fun `short silence in the middle of an utterance is preserved`() {
        val pcmData = repeatFrame(loudFrame(), 3) +
            repeatFrame(silenceFrame(), 2) +
            repeatFrame(loudFrame(), 3)

        val result = WavConverter.trimTrailingSilence(
            pcmData = pcmData,
            sampleRate = sampleRate,
            frameSizeBytes = frameSizeBytes,
            thresholdDbfs = thresholdDbfs,
            paddingMs = 0
        )

        // 마지막 프레임이 발화이므로 트리밍 대상이 없어 원본 그대로 유지
        assertArrayEquals(pcmData, result)
    }

    @Test
    fun `entirely silent audio is not over-trimmed`() {
        val pcmData = repeatFrame(silenceFrame(), 10)

        val result = WavConverter.trimTrailingSilence(
            pcmData = pcmData,
            sampleRate = sampleRate,
            frameSizeBytes = frameSizeBytes,
            thresholdDbfs = thresholdDbfs,
            paddingMs = 300
        )

        assertArrayEquals(pcmData, result)
    }

    private fun repeatFrame(frame: ByteArray, count: Int): ByteArray {
        val result = ByteArray(frame.size * count)
        for (i in 0 until count) {
            frame.copyInto(result, destinationOffset = i * frame.size)
        }
        return result
    }

    /** 최대 진폭에 가까운(또렷한 발화를 흉내낸) 프레임 */
    private fun loudFrame(): ByteArray = makeFrame(amplitude = 20000)

    /** 진폭이 0인(무음) 프레임 */
    private fun silenceFrame(): ByteArray = makeFrame(amplitude = 0)

    private fun makeFrame(amplitude: Short): ByteArray {
        val buffer = ByteBuffer.allocate(frameSizeBytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samplesPerFrame) { buffer.putShort(amplitude) }
        return buffer.array()
    }
}
