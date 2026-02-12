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
     */
    fun play(base64AudioData: String) {
        // 이전 재생 중이면 중지
        if (_state.value is AudioPlayState.Preparing ||
            _state.value is AudioPlayState.Playing
        ) {
            stop()
        }

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        _state.value = AudioPlayState.Preparing

        // Base64 디코딩 (IO thread) → MediaDataSource 재생 (Main thread)
        scope?.launch(Dispatchers.IO) {
            try {
                val audioBytes = try {
                    Base64.decode(base64AudioData, Base64.DEFAULT)
                } catch (e: IllegalArgumentException) {
                    throw AudioPlayException.DecodeError(cause = e)
                }

                launch(Dispatchers.Main) {
                    startPlayback(audioBytes)
                }
            } catch (e: AudioPlayException) {
                launch(Dispatchers.Main) {
                    _state.value = AudioPlayState.Error(e)
                    listener?.onError(e)
                }
            } catch (e: Exception) {
                val exception = AudioPlayException.UnknownError(cause = e)
                launch(Dispatchers.Main) {
                    _state.value = AudioPlayState.Error(exception)
                    listener?.onError(exception)
                }
            }
        }
    }

    /**
     * 재생 중지
     */
    fun stop() {
        releaseMediaPlayer()

        _state.value = AudioPlayState.Idle

        scope?.cancel()
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

    private fun startPlayback(audioBytes: ByteArray) {
        try {
            val dataSource = ByteArrayMediaDataSource(audioBytes)

            val player = MediaPlayer().apply {
                setDataSource(dataSource)

                setOnCompletionListener {
                    _state.value = AudioPlayState.Completed
                    listener?.onPlaybackComplete()
                    releaseMediaPlayer()
                }

                setOnErrorListener { _, what, extra ->
                    val exception = AudioPlayException.PlaybackError(
                        message = "MediaPlayer 에러 (what=$what, extra=$extra)"
                    )
                    _state.value = AudioPlayState.Error(exception)
                    listener?.onError(exception)
                    releaseMediaPlayer()
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
            _state.value = AudioPlayState.Error(exception)
            listener?.onError(exception)
            releaseMediaPlayer()
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
