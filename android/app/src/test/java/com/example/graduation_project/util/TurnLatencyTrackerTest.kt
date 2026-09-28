package com.example.graduation_project.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TurnLatencyTrackerTest {

    private var now = 0L
    private val logs = mutableListOf<String>()

    @Before
    fun setUp() {
        TurnLatencyTracker.reset()
        TurnLatencyTracker.clock = { now }
        TurnLatencyTracker.logger = { logs.add(it) }
    }

    @After
    fun tearDown() {
        TurnLatencyTracker.reset()
        TurnLatencyTracker.clock = { System.nanoTime() / 1_000_000 }
    }

    private fun elapsed(stage: String): Long? =
        logs.firstOrNull { it.contains("stage=$stage,") }
            ?.substringAfter("elapsedMs=")?.toLong()

    @Test
    fun `메시지 턴 - 말 끝부터 첫 소리까지 구간을 모두 기록한다`() {
        now = 10_000
        TurnLatencyTracker.onSpeechEnded(silenceTailMs = 3_000, audioLengthMs = 2_500, wavBytes = 80_044)
        now = 10_050
        TurnLatencyTracker.onRequestStart(TurnLatencyTracker.KIND_MESSAGE)
        now = 10_450
        TurnLatencyTracker.onUploadEnd()
        now = 15_550
        TurnLatencyTracker.onFirstFrame()
        now = 15_700
        TurnLatencyTracker.onPlaybackStart()

        assertEquals(3_000L, elapsed("app_silence_tail"))
        assertEquals(50L, elapsed("app_handoff"))
        assertEquals(400L, elapsed("app_upload"))
        assertEquals(5_100L, elapsed("app_wait_first_frame"))
        assertEquals(150L, elapsed("app_player_start"))
        // 실제 말 끝(10_000 - 3_000) → 첫 소리(15_700)
        assertEquals(8_700L, elapsed("app_perceived_total"))
        assertTrue(logs.any { it.contains("stage=app_audio_length") && it.contains("wavBytes=80044") })
    }

    @Test
    fun `첫 인사 - 이전 녹음 기록 없이 요청 시작부터 잰다`() {
        TurnLatencyTracker.onSpeechEnded(silenceTailMs = 3_000, audioLengthMs = 1_000, wavBytes = 1)
        now = 100
        TurnLatencyTracker.onRequestStart(TurnLatencyTracker.KIND_START)
        now = 200
        TurnLatencyTracker.onUploadEnd()
        now = 6_700
        TurnLatencyTracker.onFirstFrame()
        now = 6_800
        TurnLatencyTracker.onPlaybackStart()

        assertEquals(null, elapsed("app_silence_tail"))
        assertEquals(6_700L, elapsed("app_perceived_total"))
        assertTrue(logs.all { it.contains("kind=start") })
    }

    @Test
    fun `요청 기록이 없는 재생(tts-retry 등)은 기록하지 않고, 한 턴은 한 번만 기록한다`() {
        TurnLatencyTracker.onPlaybackStart()
        assertTrue(logs.isEmpty())

        TurnLatencyTracker.onRequestStart(TurnLatencyTracker.KIND_START)
        TurnLatencyTracker.onPlaybackStart()
        val count = logs.size
        TurnLatencyTracker.onPlaybackStart()
        assertEquals(count, logs.size)
    }
}
