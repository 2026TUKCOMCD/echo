package com.example.graduation_project.presentation.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.voice.AudioRecordManager
import com.example.graduation_project.domain.voice.AudioRecordException
import com.example.graduation_project.domain.voice.AudioRecordListener
import com.example.graduation_project.domain.voice.AudioRecordState
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 음성 녹음 및 VAD 상태 관리 ViewModel
 *
 * [T2.2-4] AudioRecordManager 연동
 * - VoiceRecordingManager → AudioRecordManager로 교체
 * - 파일 기반 녹음 결과 제공
 * - 세분화된 상태 관리 (Preparing, Processing 등)
 */
class VoiceRecordingViewModel(
    context: Context,
    config: VadConfig = VadConfig()
) : ViewModel() {

    private val audioRecordManager = AudioRecordManager(context.applicationContext, config)

    private val _uiState = MutableStateFlow(VoiceRecordingState())
    val uiState: StateFlow<VoiceRecordingState> = _uiState.asStateFlow()

    /** 녹음 완료 이벤트 (one-shot event) - File 참조 포함 */
    private val _recordingCompleteEvent = MutableSharedFlow<File>()
    val recordingCompleteEvent: SharedFlow<File> = _recordingCompleteEvent.asSharedFlow()

    /** 음성 종료 이벤트 (one-shot event) - WAV 데이터 포함 (하위 호환) */
    private val _speechEndEvent = MutableSharedFlow<ByteArray>()
    val speechEndEvent: SharedFlow<ByteArray> = _speechEndEvent.asSharedFlow()

    init {
        setupAudioRecordListener()
        observeAudioRecordState()
    }

    private fun setupAudioRecordListener() {
        audioRecordManager.setListener(object : AudioRecordListener {
            override fun onReady() {
                _uiState.update {
                    it.copy(isPreparing = false, isRecording = true)
                }
            }

            override fun onRecordingStart() {
                _uiState.update {
                    it.copy(isSpeechDetected = true)
                }
            }

            override fun onRecordingComplete(audioFile: File) {
                _uiState.update {
                    it.copy(
                        isSpeechDetected = false,
                        isProcessing = false,
                        lastAudioFile = audioFile,
                        lastRecordedAudio = audioFile.readBytes()
                    )
                }
                viewModelScope.launch {
                    _recordingCompleteEvent.emit(audioFile)
                    _speechEndEvent.emit(audioFile.readBytes())
                }
            }

            override fun onError(exception: AudioRecordException) {
                val vadException = when (exception) {
                    is AudioRecordException.VadError -> exception.vadException
                    else -> VadException.UnknownError(
                        message = exception.message,
                        cause = exception.cause
                    )
                }
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        isSpeechDetected = false,
                        isPreparing = false,
                        isProcessing = false,
                        error = vadException
                    )
                }
            }
        })
    }

    private fun observeAudioRecordState() {
        viewModelScope.launch {
            audioRecordManager.state.collect { state ->
                when (state) {
                    is AudioRecordState.Preparing -> {
                        _uiState.update {
                            it.copy(isPreparing = true, isRecording = false)
                        }
                    }
                    is AudioRecordState.Listening -> {
                        _uiState.update {
                            it.copy(
                                isPreparing = false,
                                isRecording = true,
                                isSpeechDetected = false
                            )
                        }
                    }
                    is AudioRecordState.Recording -> {
                        _uiState.update {
                            it.copy(isRecording = true, isSpeechDetected = true)
                        }
                    }
                    is AudioRecordState.Processing -> {
                        _uiState.update {
                            it.copy(isProcessing = true, isSpeechDetected = false)
                        }
                    }
                    is AudioRecordState.Idle -> {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                isSpeechDetected = false,
                                isPreparing = false,
                                isProcessing = false
                            )
                        }
                    }
                    else -> { /* Completed, Error는 리스너에서 처리 */ }
                }
            }
        }
    }

    /**
     * 음성 녹음 시작
     */
    fun startRecording() {
        _uiState.update { it.copy(error = null) }
        audioRecordManager.start()
    }

    /**
     * 음성 녹음 중지
     */
    fun stopRecording() {
        audioRecordManager.stop()
    }

    /**
     * 서버 전송 완료 후 임시 파일 정리 및 다음 발화 대기
     */
    fun onAudioSent() {
        audioRecordManager.deleteLastAudioFile()
        audioRecordManager.resumeListening()
    }

    /**
     * 에러 상태 초기화
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecordManager.release()
    }

    /**
     * ViewModel Factory
     * Hilt 미도입 상태이므로 Factory 패턴 사용
     */
    class Factory(
        private val context: Context,
        private val config: VadConfig = VadConfig()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VoiceRecordingViewModel::class.java)) {
                return VoiceRecordingViewModel(context, config) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
