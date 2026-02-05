package com.example.graduation_project.presentation.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.voice.VoiceRecordingManager
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import com.example.graduation_project.domain.voice.VadListener
import com.example.graduation_project.domain.voice.VadState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 음성 녹음 및 VAD 상태 관리 ViewModel
 */
class VoiceRecordingViewModel(
    context: Context,
    config: VadConfig = VadConfig()
) : ViewModel() {

    private val voiceRecordingManager = VoiceRecordingManager(context.applicationContext, config)

    private val _uiState = MutableStateFlow(VoiceRecordingState())
    val uiState: StateFlow<VoiceRecordingState> = _uiState.asStateFlow()

    /** 음성 종료 이벤트 (one-shot event) - WAV 데이터 포함 */
    private val _speechEndEvent = MutableSharedFlow<ByteArray>()
    val speechEndEvent: SharedFlow<ByteArray> = _speechEndEvent.asSharedFlow()

    init {
        setupVadListener()
        observeVadState()
    }

    private fun setupVadListener() {
        voiceRecordingManager.setVadListener(object : VadListener {
            override fun onSpeechStart() {
                _uiState.update { it.copy(isSpeechDetected = true) }
            }

            override fun onSpeechEnd(wavData: ByteArray) {
                _uiState.update {
                    it.copy(
                        isSpeechDetected = false,
                        lastRecordedAudio = wavData
                    )
                }
                viewModelScope.launch {
                    _speechEndEvent.emit(wavData)
                }
            }

            override fun onError(exception: VadException) {
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        isSpeechDetected = false,
                        error = exception
                    )
                }
            }
        })
    }

    private fun observeVadState() {
        viewModelScope.launch {
            voiceRecordingManager.vadState.collect { state ->
                when (state) {
                    is VadState.Listening -> {
                        _uiState.update {
                            it.copy(isRecording = true, isSpeechDetected = false)
                        }
                    }
                    is VadState.SpeechDetected -> {
                        _uiState.update {
                            it.copy(isRecording = true, isSpeechDetected = true)
                        }
                    }
                    is VadState.Stopped -> {
                        _uiState.update {
                            it.copy(isRecording = false, isSpeechDetected = false)
                        }
                    }
                    else -> { /* 다른 상태는 리스너에서 처리 */ }
                }
            }
        }
    }

    /**
     * 음성 녹음 시작
     */
    fun startRecording() {
        _uiState.update { it.copy(error = null) }
        voiceRecordingManager.start()
    }

    /**
     * 음성 녹음 중지
     */
    fun stopRecording() {
        voiceRecordingManager.stop()
    }

    /**
     * 에러 상태 초기화
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecordingManager.release()
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
