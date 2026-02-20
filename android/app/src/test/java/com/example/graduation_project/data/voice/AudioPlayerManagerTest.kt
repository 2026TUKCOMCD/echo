@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.graduation_project.data.voice

import com.example.graduation_project.domain.voice.AudioPlayException
import com.example.graduation_project.domain.voice.AudioPlayListener
import com.example.graduation_project.domain.voice.AudioPlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AudioPlayerManager 단위 테스트
 *
 * ## 테스트 대상
 * 1. isRetryableError() - 에러 타입별 재시도 가능 여부 판단 로직 (순수 로직, Android 의존성 없음)
 * 2. DecodeError 흐름 - forceDecodeErrorForTest 플래그로 Android 없이 에러 경로 테스트
 * 3. Stop/Release - 상태 전환 검증
 *
 * ## PlaybackError 흐름을 여기서 테스트하지 않는 이유
 * startPlayback()이 MediaPlayer(Android API)를 생성하기 때문에
 * JVM 환경에서는 실행 불가. PlaybackError 경로는 androidTest(에뮬레이터)에서 테스트 필요.
 */
class AudioPlayerManagerTest {

    private lateinit var manager: AudioPlayerManager

    @Before
    fun setUp() {
        // AudioPlayerManager 내부 scope가 Dispatchers.Main을 사용하므로
        // JVM 테스트에서 Main 디스패처를 교체해줘야 함
        Dispatchers.setMain(UnconfinedTestDispatcher())
        manager = AudioPlayerManager()
    }

    @After
    fun tearDown() {
        manager.release()
        Dispatchers.resetMain()
    }

    // ==========================================================================
    // isRetryableError() - 에러 타입별 재시도 가능 여부 (순수 로직 테스트)
    // ==========================================================================

    @Test
    fun `DecodeError는 재시도 불가`() {
        val result = manager.isRetryableError(AudioPlayException.DecodeError())
        assertFalse(result)
    }

    @Test
    fun `UnknownError는 재시도 가능`() {
        val result = manager.isRetryableError(AudioPlayException.UnknownError())
        assertTrue(result)
    }

    @Test
    fun `PlaybackError - MALFORMED(extra=-1004)은 재시도 불가`() {
        val exception = AudioPlayException.PlaybackError(
            message = "MediaPlayer 에러 (what=1, extra=-1004)"
        )
        assertFalse(manager.isRetryableError(exception))
    }

    @Test
    fun `PlaybackError - UNSUPPORTED(extra=-1007)은 재시도 불가`() {
        val exception = AudioPlayException.PlaybackError(
            message = "MediaPlayer 에러 (what=1, extra=-1007)"
        )
        assertFalse(manager.isRetryableError(exception))
    }

    @Test
    fun `PlaybackError - SERVER_DIED(what=100)은 재시도 가능`() {
        val exception = AudioPlayException.PlaybackError(
            message = "MediaPlayer 에러 (what=100, extra=0)"
        )
        assertTrue(manager.isRetryableError(exception))
    }

    @Test
    fun `PlaybackError - IO_TIMEOUT(what=1 extra=-110)은 재시도 가능`() {
        val exception = AudioPlayException.PlaybackError(
            message = "MediaPlayer 에러 (what=1, extra=-110)"
        )
        assertTrue(manager.isRetryableError(exception))
    }

    @Test
    fun `PlaybackError - UNKNOWN(what=1 extra=-2147483648)은 재시도 가능`() {
        val exception = AudioPlayException.PlaybackError(
            message = "MediaPlayer 에러 (what=1, extra=-2147483648)"
        )
        assertTrue(manager.isRetryableError(exception))
    }

    @Test
    fun `PlaybackError - 분류되지 않은 에러는 기본적으로 재시도 가능`() {
        val exception = AudioPlayException.PlaybackError(
            message = "MediaPlayer 에러 (what=999, extra=999)"
        )
        assertTrue(manager.isRetryableError(exception))
    }

    // ==========================================================================
    // DecodeError 흐름 테스트
    // forceDecodeErrorForTest = true로 Base64 디코딩 전에 강제 예외 발생
    // → MediaPlayer 없이 에러 경로 테스트 가능
    // ==========================================================================

    @Test
    fun `DecodeError 발생 시 onError가 즉시 호출됨`() {
        val latch = CountDownLatch(1)
        val capturedErrors = mutableListOf<AudioPlayException>()

        manager.setListener(makeListener(
            onError = { exception, _ ->
                capturedErrors.add(exception)
                latch.countDown()
            }
        ))

        manager.forceDecodeErrorForTest = true
        manager.play("any_audio_data")

        assertTrue("onError가 2초 내에 호출돼야 함", latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, capturedErrors.size)
        assertTrue(capturedErrors[0] is AudioPlayException.DecodeError)
    }

    @Test
    fun `DecodeError 발생 시 onRetrying은 호출되지 않음`() {
        val errorLatch = CountDownLatch(1)
        var retryCallCount = 0

        manager.setListener(makeListener(
            onRetrying = { _, _ -> retryCallCount++ },
            onError = { _, _ -> errorLatch.countDown() }
        ))

        manager.forceDecodeErrorForTest = true
        manager.play("any_audio_data")

        errorLatch.await(2, TimeUnit.SECONDS)
        assertEquals("DecodeError는 재시도 없이 즉시 실패해야 함", 0, retryCallCount)
    }

    @Test
    fun `DecodeError 발생 시 state가 Error로 전환됨`() {
        val latch = CountDownLatch(1)

        manager.setListener(makeListener(
            onError = { _, _ -> latch.countDown() }
        ))

        manager.forceDecodeErrorForTest = true
        manager.play("any_audio_data")

        latch.await(2, TimeUnit.SECONDS)
        assertTrue(
            "state가 Error여야 함, 실제: ${manager.state.value}",
            manager.state.value is AudioPlayState.Error
        )
    }

    @Test
    fun `DecodeError 발생 시 isFallbackNeeded가 true임`() {
        val latch = CountDownLatch(1)
        var capturedFallbackNeeded = false

        manager.setListener(makeListener(
            onError = { _, isFallbackNeeded ->
                capturedFallbackNeeded = isFallbackNeeded
                latch.countDown()
            }
        ))

        manager.forceDecodeErrorForTest = true
        manager.play("any_audio_data")

        latch.await(2, TimeUnit.SECONDS)
        assertTrue("isFallbackNeeded가 true여야 함", capturedFallbackNeeded)
    }

    // ==========================================================================
    // Stop / Release 테스트
    // ==========================================================================

    @Test
    fun `초기 state는 Idle임`() {
        assertEquals(AudioPlayState.Idle, manager.state.value)
    }

    @Test
    fun `stop 호출 시 state가 Idle로 전환됨`() {
        manager.stop()
        assertEquals(AudioPlayState.Idle, manager.state.value)
    }

    @Test
    fun `재생 준비 중 stop 호출 시 onError가 호출되지 않음`() {
        var errorCalled = false

        manager.setListener(makeListener(
            onError = { _, _ -> errorCalled = true }
        ))

        manager.forceDecodeErrorForTest = true
        manager.play("any_audio_data")
        manager.stop()  // 즉시 중지

        // stop 후에는 추가 콜백이 없어야 함
        Thread.sleep(200)
        assertEquals(AudioPlayState.Idle, manager.state.value)
    }

    // ==========================================================================
    // 헬퍼: 필요한 콜백만 override하는 리스너 생성
    // ==========================================================================

    private fun makeListener(
        onPlaybackStart: () -> Unit = {},
        onPlaybackComplete: () -> Unit = {},
        onRetrying: (Int, Int) -> Unit = { _, _ -> },
        onError: (AudioPlayException, Boolean) -> Unit = { _, _ -> }
    ): AudioPlayListener = object : AudioPlayListener {
        override fun onPlaybackStart() = onPlaybackStart()
        override fun onPlaybackComplete() = onPlaybackComplete()
        override fun onRetrying(currentAttempt: Int, maxAttempts: Int) = onRetrying(currentAttempt, maxAttempts)
        override fun onError(exception: AudioPlayException, isFallbackNeeded: Boolean) = onError(exception, isFallbackNeeded)
    }
}
