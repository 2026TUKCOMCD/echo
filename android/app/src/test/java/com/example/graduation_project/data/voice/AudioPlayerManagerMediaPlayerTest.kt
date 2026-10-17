package com.example.graduation_project.data.voice

import android.os.Build
import android.util.Base64
import com.example.graduation_project.domain.voice.AudioPlayException
import com.example.graduation_project.domain.voice.AudioPlayListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 재생 준비(setDataSource/prepare)가 실패해도 만든 MediaPlayer를 모두 해제하는지 확인한다.
 * MediaPlayer는 기기 전체에서 동시에 쓸 수 있는 디코더 등 네이티브 자원을 잡고 있어, 해제하지 않으면 쌓인다.
 *
 * Robolectric의 ShadowMediaPlayer는 미디어 정보를 등록하지 않은 데이터 소스에 setDataSource()가
 * 예외를 던지므로, 깨진 음성을 받은 상황을 그대로 재현할 수 있다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AudioPlayerManagerMediaPlayerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val createdPlayers = mutableListOf<ShadowMediaPlayer>()
    private lateinit var manager: AudioPlayerManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ShadowMediaPlayer.setCreateListener { _, shadow -> createdPlayers += shadow }
        manager = AudioPlayerManager()
    }

    @After
    fun tearDown() {
        manager.release()
        ShadowMediaPlayer.setCreateListener(null)
        Dispatchers.resetMain()
    }

    @Test
    fun `스트리밍 재생 준비가 실패해도 만든 MediaPlayer를 해제한다`() {
        val failed = CountDownLatch(1)
        manager.setListener(errorListener(failed))

        manager.playStream(ByteArrayInputStream(ByteArray(0)))

        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertEquals(1, createdPlayers.size)
        assertAllReleased()
    }

    @Test
    fun `Base64 음성 재생 준비가 재시도까지 모두 실패해도 만든 MediaPlayer를 전부 해제한다`() {
        val failed = CountDownLatch(1)
        manager.setListener(errorListener(failed))

        manager.play(Base64.encodeToString(ByteArray(16), Base64.DEFAULT))

        // 디코딩(IO 스레드) → 재생 시도·재시도(Main, 100/300/900ms 대기)를 끝까지 진행
        val deadline = System.currentTimeMillis() + 5_000
        while (failed.count > 0 && System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertEquals("최종 실패 콜백", 0, failed.count)
        assertEquals("첫 시도 + 재시도 3회", 4, createdPlayers.size)
        assertAllReleased()
    }

    private fun errorListener(failed: CountDownLatch) = object : AudioPlayListener {
        override fun onPlaybackStart() = Unit
        override fun onPlaybackComplete() = Unit
        override fun onRetrying(currentAttempt: Int, maxAttempts: Int) = Unit
        override fun onError(exception: AudioPlayException, isFallbackNeeded: Boolean) {
            failed.countDown()
        }
    }

    private fun assertAllReleased() {
        createdPlayers.forEachIndexed { index, player ->
            assertEquals("MediaPlayer #$index 해제 여부", ShadowMediaPlayer.State.END, player.state)
        }
    }
}
