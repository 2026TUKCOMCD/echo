package com.example.graduation_project.data.voice

import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Base64
import com.example.graduation_project.domain.voice.AudioPlayException
import com.example.graduation_project.domain.voice.AudioPlayListener
import com.example.graduation_project.domain.voice.AudioPlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [T2.3-1] AudioPlayerManager
 *
 * AI 응답 음성(Base64 MP3)을 디코딩하여 재생하는 관리자
 * 1. Base64 String → ByteArray 디코딩
 * 2. ByteArray → MediaDataSource로 메모리에서 직접 재생
 *
 * 데이터 흐름:
 * play(base64AudioData)
 *   → Base64.decode()
 *   → ByteArrayMediaDataSource(bytes)
 *   → MediaPlayer.setDataSource(dataSource) → prepare() → start()
 *   → onCompletion → Completed
 */
class AudioPlayerManager {

    private val _state = MutableStateFlow<AudioPlayState>(AudioPlayState.Idle)
    val state: StateFlow<AudioPlayState> = _state.asStateFlow()

    private var listener: AudioPlayListener? = null
    private var scope: CoroutineScope? = null
    private var mediaPlayer: MediaPlayer? = null

    // [T2.3-3] 재시도 로직 관련 필드
    private var cachedAudioBytes: ByteArray? = null  // 재시도용 디코딩된 오디오 데이터 캐시
    private var retryCount: Int = 0                  // 현재 재시도 횟수
    private val maxRetries: Int = 3                  // 최대 재시도 횟수
    private val retryDelays = listOf(100L, 300L, 900L)  // Exponential backoff (ms)

    // [TEST ONLY] true로 설정하면 play() 호출 시 강제로 DecodeError 발생 → 서버 TTS 재요청 흐름 테스트
    var forceDecodeErrorForTest = false

    /**
     * AudioPlayListener 설정
     */
    fun setListener(listener: AudioPlayListener) {
        this.listener = listener
    }

    /**
     * AudioPlayListener 제거
     */
    fun removeListener() {
        this.listener = null
    }

    /**
     * Base64 인코딩된 MP3 음성 데이터를 재생
     *
     * @param base64AudioData 서버에서 받은 audioData (Base64 String)
     *
     * 흐름: Idle → Preparing → Playing → Completed / Error
     *      Preparing → Retrying → Playing (재시도 성공)
     *               → Error (재시도 실패)
     *
     * [T2.3-3] 재시도 로직: PlaybackError 발생 시 자동으로 2-3회 재시도
     * [개선] Base64 디코딩을 한 번만 수행하고 ByteArray 캐시 (재시도 시 디코딩 불필요)
     */
    fun play(base64AudioData: String) {
        // 이전 재생 중이면 중지
        if (_state.value is AudioPlayState.Preparing ||
            _state.value is AudioPlayState.Playing
        ) {
            stop()
        }

        // 재시도 카운터 초기화
        retryCount = 0

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        _state.value = AudioPlayState.Preparing

        // Base64 디코딩 (한 번만 수행)
        scope?.launch(Dispatchers.IO) {
            try {
                // [TEST ONLY] 강제 DecodeError 발생
                if (forceDecodeErrorForTest) {
                    throw AudioPlayException.DecodeError(message = "[테스트] 강제 DecodeError 발생")
                }

                val audioBytes = try {
                    Base64.decode(base64AudioData, Base64.DEFAULT)
                } catch (e: IllegalArgumentException) {
                    throw AudioPlayException.DecodeError(cause = e)
                }

                // 첫 재생 시도 (Main 스레드로 전환)
                launch(Dispatchers.Main) {
                    // 디코딩 성공 → ByteArray 캐시 (Main 스레드에서)
                    cachedAudioBytes = audioBytes
                    attemptPlayback(audioBytes, isRetry = false)
                }
            } catch (e: AudioPlayException) {
                launch(Dispatchers.Main) {
                    handlePlaybackError(e, isRetry = false)
                }
            } catch (e: Exception) {
                val exception = AudioPlayException.UnknownError(cause = e)
                launch(Dispatchers.Main) {
                    handlePlaybackError(exception, isRetry = false)
                }
            }
        }
    }

    /**
     * 실제 재생 시도 (재시도 로직 포함)
     *
     * @param audioBytes 디코딩된 오디오 데이터 (ByteArray)
     * @param isRetry 재시도 여부 (true면 재시도 중)
     */
    private fun attemptPlayback(audioBytes: ByteArray, isRetry: Boolean) {
        // 디코딩된 ByteArray를 바로 MediaPlayer로 재생
        startPlayback(audioBytes, isRetry)
    }

    /**
     * 재생 중지 (재시도 중이어도 즉시 중지)
     *
     * [T2.3-3] 재시도 코루틴도 함께 취소됨
     */
    fun stop() {
        releaseMediaPlayer()

        // 재시도 관련 상태 초기화
        cachedAudioBytes = null
        retryCount = 0

        _state.value = AudioPlayState.Idle

        scope?.cancel()  // 재시도 중인 코루틴도 취소됨
        scope = null
    }

    /**
     * 리소스 해제 (ViewModel.onCleared()에서 호출)
     */
    fun release() {
        stop()
        listener = null
    }

    // ---- Private: MediaPlayer ----

    /**
     * MediaPlayer로 오디오 재생
     *
     * @param audioBytes 디코딩된 오디오 바이트 배열
     * @param isRetry 재시도 여부
     */
    private fun startPlayback(audioBytes: ByteArray, isRetry: Boolean) {
        try {
            val dataSource = ByteArrayMediaDataSource(audioBytes)

            val player = MediaPlayer().apply {
                setDataSource(dataSource)

                setOnCompletionListener {
                    // 재생 성공 → 캐시 해제
                    cachedAudioBytes = null
                    retryCount = 0

                    _state.value = AudioPlayState.Completed
                    listener?.onPlaybackComplete()
                    releaseMediaPlayer()
                }

                setOnErrorListener { _, what, extra ->
                    val exception = AudioPlayException.PlaybackError(
                        message = "MediaPlayer 에러 (what=$what, extra=$extra)"
                    )
                    releaseMediaPlayer()
                    handlePlaybackError(exception, isRetry)
                    true
                }

                prepare()
                start()
            }

            mediaPlayer = player
            _state.value = AudioPlayState.Playing
            listener?.onPlaybackStart()
        } catch (e: Exception) {
            val exception = AudioPlayException.PlaybackError(cause = e)
            releaseMediaPlayer()
            handlePlaybackError(exception, isRetry)
        }
    }

    /**
     * 재생 에러 처리 - 재시도 또는 최종 실패
     *
     * @param exception 발생한 예외
     * @param isRetry 현재 재시도 중인지 여부
     *
     * [T2.3-3] 재시도 로직 핵심 메서드
     * [개선] 캐시된 ByteArray 재사용 (디코딩 불필요)
     */
    private fun handlePlaybackError(exception: AudioPlayException, isRetry: Boolean) {
        val canRetry = isRetryableError(exception) && retryCount < maxRetries

        if (canRetry && cachedAudioBytes != null) {
            // 재시도 가능 → 자동 재시도 (디코딩 불필요)
            retryCount++
            _state.value = AudioPlayState.Retrying(retryCount, maxRetries)
            listener?.onRetrying(retryCount, maxRetries)

            // Exponential backoff delay
            val delay = retryDelays.getOrElse(retryCount - 1) { 900L }
            scope?.launch {
                kotlinx.coroutines.delay(delay)
                attemptPlayback(cachedAudioBytes!!, isRetry = true)
            }
        } else {
            // 최종 실패 → 캐시 해제 및 에러 전파
            cachedAudioBytes = null
            retryCount = 0

            _state.value = AudioPlayState.Error(exception, isFallbackNeeded = true)
            listener?.onError(exception, isFallbackNeeded = true)
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (_: Exception) {
                // MediaPlayer may be in invalid state
            }
        }
        mediaPlayer = null
    }

    /**
     * 에러 타입 분석 - 재시도 가능 여부 판단
     *
     * @param exception 발생한 AudioPlayException
     * @return true면 재시도 가능 (일시적 에러), false면 영구 에러
     *
     * [T2.3-3] 재생 에러 처리 및 재시도 로직
     */
    internal fun isRetryableError(exception: AudioPlayException): Boolean {
        return when (exception) {
            is AudioPlayException.DecodeError -> {
                // Base64 디코딩 실패 → 같은 데이터로 재시도해도 실패
                false
            }
            is AudioPlayException.PlaybackError -> {
                // MediaPlayer 에러 메시지에서 what/extra 코드 분석
                val errorMsg = exception.message ?: ""
                when {
                    // Transient errors (일시적 에러 - 재시도 가능)
                    errorMsg.contains("what=100") -> true   // MEDIA_ERROR_SERVER_DIED
                    errorMsg.contains("what=1") && errorMsg.contains("extra=-110") -> true  // MEDIA_ERROR_IO (timeout)
                    errorMsg.contains("what=1") && errorMsg.contains("extra=-2147483648") -> true  // MEDIA_ERROR_UNKNOWN

                    // Permanent errors (영구 에러 - 재시도 불가)
                    errorMsg.contains("extra=-1004") -> false  // MEDIA_ERROR_MALFORMED
                    errorMsg.contains("extra=-1007") -> false  // MEDIA_ERROR_UNSUPPORTED

                    // Default: Conservative approach - 재시도 시도
                    else -> true
                }
            }
            is AudioPlayException.UnknownError -> {
                // 알 수 없는 에러 → 재시도 시도 (conservative)
                true
            }
        }
    }

    // ---- Private: MediaDataSource ----

    /**
     * ByteArray를 MediaDataSource로 감싸는 구현체
     * MediaPlayer가 메모리에서 직접 오디오 데이터를 읽을 수 있게 함 (API 23+)
     */
    private class ByteArrayMediaDataSource(
        private val data: ByteArray
    ) : MediaDataSource() {

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1

            val remaining = (data.size - position).toInt()
            val bytesToRead = minOf(size, remaining)
            System.arraycopy(data, position.toInt(), buffer, offset, bytesToRead)
            return bytesToRead
        }

        override fun getSize(): Long = data.size.toLong()

        override fun close() {
            // ByteArray는 GC가 처리하므로 별도 해제 불필요
        }
    }
}
