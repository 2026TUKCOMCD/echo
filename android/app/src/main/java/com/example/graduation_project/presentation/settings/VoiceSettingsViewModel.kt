package com.example.graduation_project.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.model.VoiceSettings
import com.example.graduation_project.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceSettingsUiState(
    val voiceSpeed: Float = 1.0f,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class VoiceSettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    private val repository = UserRepository()
    private var debounceJob: Job? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = repository.getPreferences()) {
                is ApiResult.Success -> {
                    val speed = result.data.voiceSettings?.voiceSpeed?.toFloat() ?: 1.0f
                    _uiState.update {
                        it.copy(
                            voiceSpeed = speed,
                            isLoading = false
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "설정을 불러올 수 없습니다"
                        )
                    }
                }
            }
        }
    }

    fun onSpeedChange(speed: Float) {
        // 0.1 단위로 반올림
        val rounded = (Math.round(speed * 10.0f) / 10.0f)
        _uiState.update { it.copy(voiceSpeed = rounded, isSaved = false) }

        // 기존 debounce 취소 후 0.5초 대기
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500L)
            saveSpeed(rounded)
        }
    }

    private suspend fun saveSpeed(speed: Float) {
        val preferences = UserPreferences(
            voiceSettings = VoiceSettings(voiceSpeed = speed.toDouble())
        )

        when (repository.updatePreferences(preferences)) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(isSaved = true) }
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(errorMessage = "저장에 실패했습니다")
                }
            }
        }
    }
}
