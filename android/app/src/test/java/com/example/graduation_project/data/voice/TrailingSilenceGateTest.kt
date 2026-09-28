package com.example.graduation_project.data.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * 실시간 전송분(TrailingSilenceGate)을 이어 붙이면 WAV 트리밍(WavConverter.trimTrailingSilence) 결과와 같아야 한다 -
 * 한국어 품질 비교는 트리밍된 오디오로 검증했으므로, 실시간 전송도 같은 오디오를 보내야 그 검증이 유효하다.
 */
class TrailingSilenceGateTest {

    private val sampleRate = 16_000
    private val frameSamples = 512
    private val frameBytes = frameSamples * 2
    private val thresholdDbfs = -40.0
    private val paddingMs = 300

    private fun frame(amplitude: Int): ByteArray {
        val bytes = ByteArray(frameBytes)
        for (i in 0 until frameSamples) {
            val sample = if (i % 2 == 0) amplitude else -amplitude
            bytes[i * 2] = sample.toByte()
            bytes[i * 2 + 1] = (sample shr 8).toByte()
        }
        return bytes
    }

    private val loud get() = frame(8000)
    private val quiet get() = frame(20)

    private fun runGate(frames: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        val gate = TrailingSilenceGate(sampleRate, frameBytes, thresholdDbfs, paddingMs) { out.write(it) }
        frames.forEach { gate.offer(it) }
        gate.finish()
        return out.toByteArray()
    }

    private fun trimmed(frames: List<ByteArray>): ByteArray {
        val pcm = ByteArrayOutputStream().apply { frames.forEach { write(it) } }.toByteArray()
        return WavConverter.trimTrailingSilence(pcm, sampleRate, frameBytes, thresholdDbfs, paddingMs)
    }

    @Test
    fun `말 끝 무음 꼬리는 여유 300ms만 남기고 버린다`() {
        val frames = List(5) { loud } + List(90) { quiet } // 약 2.9초 무음 꼬리
        val result = runGate(frames)

        // 300ms / 32ms = 9.4 → 10프레임
        assertEquals((5 + 10) * frameBytes, result.size)
        assertArrayEquals(trimmed(frames), result)
    }

    @Test
    fun `문장 중간에 쉰 부분은 그대로 보낸다`() {
        val frames = List(3) { loud } + List(20) { quiet } + List(3) { loud } + List(40) { quiet }
        val result = runGate(frames)

        assertEquals((3 + 20 + 3 + 10) * frameBytes, result.size)
        assertArrayEquals(trimmed(frames), result)
    }

    @Test
    fun `발화 전체가 조용하면 자르지 않고 전부 보낸다`() {
        val frames = List(30) { quiet }
        val result = runGate(frames)

        assertEquals(30 * frameBytes, result.size)
        assertArrayEquals(trimmed(frames), result)
    }

    @Test
    fun `무작위 패턴에서도 WAV 트리밍과 같은 결과`() {
        val random = Random(42)
        repeat(200) {
            val frames = List(random.nextInt(1, 120)) { if (random.nextInt(4) == 0) loud else quiet }
            assertArrayEquals(trimmed(frames), runGate(frames))
        }
    }

    @Test
    fun `큰 소리는 바로 내보낸다 - 말하는 동안 지연 없음`() {
        val emitted = mutableListOf<ByteArray>()
        val gate = TrailingSilenceGate(sampleRate, frameBytes, thresholdDbfs, paddingMs) { emitted.add(it) }

        gate.offer(loud)
        assertEquals(1, emitted.size)
        gate.offer(quiet)
        assertEquals("조용한 프레임은 판단 전까지 들고 있음", 1, emitted.size)
        gate.offer(loud)
        assertEquals(3, emitted.size)
    }
}
