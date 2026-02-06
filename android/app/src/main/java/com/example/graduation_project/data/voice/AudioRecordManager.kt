package com.example.graduation_project.data.voice

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.graduation_project.domain.voice.AudioRecordException
import com.example.graduation_project.domain.voice.AudioRecordListener
import com.example.graduation_project.domain.voice.AudioRecordState
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import com.example.graduation_project.domain.voice.VadListener
import com.example.graduation_project.domain.voice.VadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * [T2.2-4] AudioRecordManager
 *
 * VoiceRecordingManager를 래핑하여 다음 기능을 제공:
 * 1. VAD 기반 자동 녹음 시작/종료 (VoiceRecordingManager에 위임)
 * 2. WAV 파일 저장 (AudioFileManager에 위임)
 * 3. AudioRecordState 기반 상태 관리
 * 4. 파일 생명주기 관리 (생성, 정리)
 *
 * 데이터 흐름:
 * VoiceRecordingManager.onSpeechEnd(wavData)
 *   → AudioFileManager.saveWavFile(wavData)
 *   → AudioRecordState.Completed(file)
 */
class AudioRecordManager @VisibleForTesting internal constructor(
    private val voiceRecordingManager: VoiceRecordingManager,
    private val audioFileManager: AudioFileManager
) {
    constructor(context: Context, config: VadConfig = VadConfig()) : this(
        VoiceRecordingManager(context.applicationContext, config),
        AudioFileManager(context.applicationContext)
    )

    private val _state = MutableStateFlow<AudioRecordState>(AudioRecordState.Idle)
    val state: StateFlow<AudioRecordState> = _state.asStateFlow()

    private var listener: AudioRecordListener? = null
    private var scope: CoroutineScope? = null

    /** 마지막으로 저장된 오디오 파일 */
    private var _lastAudioFile: File? = null
    val lastAudioFile: File? get() = _lastAudioFile

    init {
        setupVadListener()
    }

    /**
     * AudioRecordListener 설정
     */
    fun setListener(listener: AudioRecordListener) {
        this.listener = listener
    }

    /**
     * AudioRecordListener 제거
     */
    fun removeListener() {
        this.listener = null
    }

    /**
     * 녹음 시작 (VAD 기반 자동 감지)
     *
     * 흐름: Idle → Preparing → Listening → Recording → Processing → Completed
     */
    fun start() {
        if (_state.value is AudioRecordState.Preparing ||
            _state.value is AudioRecordState.Listening ||
            _state.value is AudioRecordState.Recording ||
            _state.value is AudioRecordState.Processing
        ) {
            return // 이미 활성 상태
        }

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        _state.value = AudioRecordState.Preparing

        // 시작 전에 오래된 파일 정리
        scope?.launch(Dispatchers.IO) {
            audioFileManager.cleanupOldFiles()
            audioFileManager.enforceFileLimit()
        }

        observeVadState()
        voiceRecordingManager.start()
    }

    /**
     * 녹음 중지
     */
    fun stop() {
        voiceRecordingManager.stop()
        _state.value = AudioRecordState.Idle

        scope?.cancel()
        scope = null
    }

    /**
     * 리소스 해제
     */
    fun release() {
        stop()
        voiceRecordingManager.release()
        listener = null
    }

    /**
     * 마지막 녹음 파일 삭제
     * (서버 전송 완료 후 호출)
     */
    fun deleteLastAudioFile() {
        _lastAudioFile?.let { audioFileManager.deleteFile(it) }
        _lastAudioFile = null
    }

    /**
     * 모든 임시 오디오 파일 삭제
     */
    fun clearAudioFiles() {
        audioFileManager.clearAll()
        _lastAudioFile = null
    }

    /**
     * Completed/Error 상태에서 다시 녹음 시작
     * (서버 전송 후 다음 발화 대기 시 호출)
     */
    fun resumeListening() {
        if (_state.value is AudioRecordState.Completed ||
            _state.value is AudioRecordState.Error
        ) {
            stop()
            start()
        }
    }

    private fun setupVadListener() {
        voiceRecordingManager.setVadListener(object : VadListener {
            override fun onSpeechStart() {
                _state.value = AudioRecordState.Recording
                listener?.onRecordingStart()
            }

            override fun onSpeechEnd(wavData: ByteArray) {
                _state.value = AudioRecordState.Processing

                scope?.launch(Dispatchers.IO) {
                    try {
                        val file = audioFileManager.saveWavFile(wavData)
                        _lastAudioFile = file

                        launch(Dispatchers.Main) {
                            _state.value = AudioRecordState.Completed(file)
                            listener?.onRecordingComplete(file)
                        }
                    } catch (e: AudioRecordException) {
                        launch(Dispatchers.Main) {
                            _state.value = AudioRecordState.Error(e)
                            listener?.onError(e)
                        }
                    } catch (e: Exception) {
                        val exception = AudioRecordException.UnknownError(cause = e)
                        launch(Dispatchers.Main) {
                            _state.value = AudioRecordState.Error(exception)
                            listener?.onError(exception)
                        }
                    }
                }
            }

            override fun onError(exception: VadException) {
                val wrappedException = AudioRecordException.VadError(exception)
                _state.value = AudioRecordState.Error(wrappedException)
                listener?.onError(wrappedException)
            }
        })
    }

    private fun observeVadState() {
        scope?.launch {
            voiceRecordingManager.vadState.collect { vadState ->
                when (vadState) {
                    is VadState.Listening -> {
                        if (_state.value is AudioRecordState.Preparing) {
                            _state.value = AudioRecordState.Listening
                            listener?.onReady()
                        }
                    }
                    else -> { /* VadListener에서 처리 */ }
                }
            }
        }
    }
}
