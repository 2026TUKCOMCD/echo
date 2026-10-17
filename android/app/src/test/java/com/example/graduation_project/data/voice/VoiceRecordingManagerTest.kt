package com.example.graduation_project.data.voice

import android.content.Context
import android.util.Log
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import com.example.graduation_project.domain.voice.VadListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 듣기 세션의 길이 제한 - VadConfig.maxRecordingDurationMs(60초)는 발화 하나에만 적용되고,
 * 말을 기다리는 시간에는 제한이 없어야 한다 (오래 생각하다 말해도 마이크가 살아 있어야 함).
 *
 * 마이크(AudioRecorder)와 VAD는 가짜로 바꾸고, 프레임 하나(512샘플 @16kHz = 32ms)마다 가상 시간을 흘린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRecordingManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val config = VadConfig()
    private val frameMs = config.frameSize * 1000L / config.sampleRate

    private val audioRecorder = mockk<AudioRecorder>(relaxed = true)
    private val vadProcessor = mockk<VadProcessor>(relaxed = true)
    private lateinit var manager: VoiceRecordingManager

    private var speechStarts = 0
    private val speechEnds = mutableListOf<ByteArray>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0

        // 소리가 있는 프레임만 음성으로 판정
        every { vadProcessor.isSpeech(any()) } answers { firstArg<ShortArray>().any { it != 0.toShort() } }

        manager = VoiceRecordingManager(mockk<Context>(relaxed = true), config, audioRecorder, vadProcessor)
        manager.setVadListener(object : VadListener {
            override fun onSpeechStart() {
                speechStarts++
            }

            override fun onSpeechEnd(wavData: ByteArray) {
                speechEnds += wavData
            }

            override fun onError(exception: VadException) = Unit
        })
    }

    @After
    fun tearDown() {
        manager.release()
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `60초 넘게 말이 없다가 말해도 마이크가 살아 있어 발화를 받는다`() = runTest(testDispatcher) {
        every { audioRecorder.startRecording() } returns micFrames(
            silenceSeconds(70),
            speechSeconds(3),
            silenceSeconds(1)
        )

        manager.start()
        advanceUntilIdle()

        assertTrue("듣기가 계속되어야 함", manager.isRunning())
        assertEquals(1, speechStarts)
        assertEquals(1, speechEnds.size)
    }

    @Test
    fun `50초 기다렸다 20초 말해도 중간에 잘리지 않는다`() = runTest(testDispatcher) {
        every { audioRecorder.startRecording() } returns micFrames(
            silenceSeconds(50),
            speechSeconds(20),
            silenceSeconds(1)
        )

        manager.start()
        advanceUntilIdle()

        assertEquals(1, speechEnds.size)
        assertTrue("발화 20초가 모두 담겨야 함", pcmMs(speechEnds.single()) >= 20_000)
    }

    @Test
    fun `발화가 최대 길이를 넘으면 그 길이에서 끊어 보내고 계속 듣는다`() = runTest(testDispatcher) {
        every { audioRecorder.startRecording() } returns micFrames(speechSeconds(70))

        manager.start()
        advanceUntilIdle()

        assertEquals(1, speechEnds.size)
        val lengthMs = pcmMs(speechEnds.single())
        assertTrue("60초에서 끊겨야 함 (실제 ${lengthMs}ms)", lengthMs in 60_000..60_000 + frameMs)
        // 끊긴 뒤에도 이어지는 말은 새 발화로 받는다
        assertTrue(manager.isRunning())
        assertEquals(2, speechStarts)
    }

    // ===== 헬퍼 =====

    /** (음성 여부, 프레임 수) 구간을 차례로 흘리고, 실제 마이크처럼 stop() 전까지 끝나지 않는 스트림 */
    private fun micFrames(vararg segments: Pair<Boolean, Int>): Flow<ShortArray> = flow {
        for ((speech, count) in segments) {
            repeat(count) {
                delay(frameMs)
                emit(ShortArray(config.frameSize) { (if (speech) 8000 else 0).toShort() })
            }
        }
        awaitCancellation()
    }

    private fun silenceSeconds(seconds: Int) = false to (seconds * 1000 / frameMs).toInt()
    private fun speechSeconds(seconds: Int) = true to (seconds * 1000 / frameMs).toInt()

    /** WAV(44바이트 헤더 + 16-bit 모노 PCM) → 소리 길이(ms) */
    private fun pcmMs(wav: ByteArray): Long = (wav.size - 44) * 1000L / (config.sampleRate * 2)
}
