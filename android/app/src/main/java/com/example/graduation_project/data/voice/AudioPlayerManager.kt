package com.example.graduation_project.data.voice

import android.content.Context
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [T2.3-1] AudioPlayerManager
 *
 * AI 응답 음성(Base64 MP3)을 디코딩하여 재생하는 관리자
 * 1. Base64 String → ByteArray 디코딩
 * 2. ByteArray → 임시 MP3 파일 저장
 * 3. MediaPlayer로 재생
 * 4. 재생 완료 후 임시 파일 정리
 *
 * 데이터 흐름:
 * play(base64AudioData)
 *   → Base64.decode()
 *   → saveTempMp3File(bytes)
 *   → MediaPlayer.setDataSource(file) → prepare() → start()
 *   → onCompletion → Completed → cleanup
 */
class AudioPlayerManager(context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<AudioPlayState>(AudioPlayState.Idle)
    val state: StateFlow<AudioPlayState> = _state.asStateFlow()

    private var listener: AudioPlayListener? = null
    private var scope: CoroutineScope? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentTempFile: File? = null

    companion object {
        private const val PLAYBACK_DIR_NAME = "audio_playback"
        private const val FILE_PREFIX = "echo_tts_"
        private const val FILE_EXTENSION = ".mp3"
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss_SSS"

        /** 보관 기간: 1시간 */
        private const val MAX_FILE_AGE_MS = 60 * 60 * 1000L

        /** 최대 파일 수 */
        private const val MAX_FILE_COUNT = 5
    }

    private val playbackDir: File
        get() = File(appContext.cacheDir, PLAYBACK_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }

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

        // 오래된 파일 정리 (IO thread)
        scope?.launch(Dispatchers.IO) {
            cleanupOldFiles()
            enforceFileLimit()
        }

        // Base64 디코딩 + 파일 저장 (IO thread) → 재생 (Main thread)
        scope?.launch(Dispatchers.IO) {
            try {
                // Step 1: Base64 디코딩
                val audioBytes = try {
                    Base64.decode(base64AudioData, Base64.DEFAULT)
                } catch (e: IllegalArgumentException) {
                    throw AudioPlayException.DecodeError(cause = e)
                }

                // Step 2: 임시 MP3 파일 저장
                val tempFile = try {
                    saveTempMp3File(audioBytes)
                } catch (e: IOException) {
                    throw AudioPlayException.FileSaveError(cause = e)
                }

                currentTempFile = tempFile

                // Step 3: MediaPlayer 재생 (Main thread)
                launch(Dispatchers.Main) {
                    startPlayback(tempFile)
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
        deleteTempFile()

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

    /**
     * 모든 임시 재생 파일 삭제
     */
    fun clearPlaybackFiles() {
        playbackDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) }
            ?.forEach { it.delete() }
        currentTempFile = null
    }

    // ---- Private: MediaPlayer ----

    private fun startPlayback(file: File) {
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)

                setOnCompletionListener {
                    _state.value = AudioPlayState.Completed
                    listener?.onPlaybackComplete()
                    releaseMediaPlayer()
                    deleteTempFile()
                }

                setOnErrorListener { _, what, extra ->
                    val exception = AudioPlayException.PlaybackError(
                        message = "MediaPlayer 에러 (what=$what, extra=$extra)"
                    )
                    _state.value = AudioPlayState.Error(exception)
                    listener?.onError(exception)
                    releaseMediaPlayer()
                    deleteTempFile()
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
            deleteTempFile()
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

    // ---- Private: 임시 파일 관리 ----

    private fun saveTempMp3File(audioBytes: ByteArray): File {
        val fileName = generateFileName()
        val file = File(playbackDir, fileName)

        FileOutputStream(file).use { fos ->
            fos.write(audioBytes)
            fos.flush()
        }

        return file
    }

    private fun deleteTempFile() {
        currentTempFile?.let {
            if (it.exists()) it.delete()
        }
        currentTempFile = null
    }

    private fun cleanupOldFiles() {
        val now = System.currentTimeMillis()
        playbackDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.extension == "mp3" }
            ?.filter { now - it.lastModified() > MAX_FILE_AGE_MS }
            ?.forEach { it.delete() }
    }

    private fun enforceFileLimit() {
        val files = playbackDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.extension == "mp3" }
            ?.sortedBy { it.lastModified() }
            ?: return

        if (files.size > MAX_FILE_COUNT) {
            files.take(files.size - MAX_FILE_COUNT).forEach { it.delete() }
        }
    }

    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "$FILE_PREFIX$timestamp$FILE_EXTENSION"
    }
}
