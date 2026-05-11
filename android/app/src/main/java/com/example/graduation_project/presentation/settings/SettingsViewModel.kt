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

data class SettingsUiState(
    val userName: String = "",
    val age: Int? = null,
    val birthday: String? = null,
    val voiceSpeed: Float = 1.0f,
    val preferredTopics: String = "",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            when (val result = userRepository.getPreferences()) {
                is ApiResult.Success -> {
                    val prefs = result.data
                    _uiState.update {
                        it.copy(
                            userName = prefs.name ?: "",
                            age = prefs.age,
                            birthday = prefs.birthday,
                            voiceSpeed = prefs.voiceSettings?.voiceSpeed?.toFloat() ?: 1.0f,
                            preferredTopics = prefs.preferredTopics ?: "",
                            isLoading = false
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onSpeedChange(speed: Float) {
        val rounded = (Math.round(speed * 10.0f) / 10.0f)
        _uiState.update { it.copy(voiceSpeed = rounded, isSaved = false) }

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500L)
            saveVoiceSpeed(rounded)
        }
    }

    private suspend fun saveVoiceSpeed(speed: Float) {
        val preferences = UserPreferences(
            voiceSettings = VoiceSettings(voiceSpeed = speed.toDouble())
        )
        when (userRepository.updatePreferences(preferences)) {
            is ApiResult.Success -> _uiState.update { it.copy(isSaved = true) }
            is ApiResult.Error -> _uiState.update { it.copy(errorMessage = "저장에 실패했습니다") }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
