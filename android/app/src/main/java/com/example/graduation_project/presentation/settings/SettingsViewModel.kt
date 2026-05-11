package com.example.graduation_project.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userName: String = "",
    val age: Int? = null,
    val birthday: String? = null,
    val familyInfo: String? = null,
    val guardianEmail: String? = null,
    val location: String? = null,
    val occupation: String? = null,
    val hobbies: String? = null,
    val preferredTopics: String? = null,
    val voiceSpeed: Double = 1.0,
    val voiceTone: String = "warm",
    val conversationTime: String? = null,
    val preferredSleepHours: Int? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            when (val result = userRepository.getPreferences()) {
                is ApiResult.Success -> _uiState.update { applyPrefs(it, result.data).copy(isLoading = false) }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun savePreferences(preferences: UserPreferences) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = userRepository.updatePreferences(preferences)) {
                is ApiResult.Success -> _uiState.update {
                    applyPrefs(it, result.data).copy(isSaving = false, savedMessage = "저장되었습니다")
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = "저장에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    fun dismissSavedMessage() = _uiState.update { it.copy(savedMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private fun applyPrefs(state: SettingsUiState, prefs: UserPreferences): SettingsUiState = state.copy(
        userName = prefs.name ?: state.userName,
        age = prefs.age,
        birthday = prefs.birthday,
        familyInfo = prefs.familyInfo,
        guardianEmail = prefs.guardianEmail,
        location = prefs.location,
        occupation = prefs.occupation,
        hobbies = prefs.hobbies,
        preferredTopics = prefs.preferredTopics,
        voiceSpeed = prefs.voiceSettings?.voiceSpeed ?: 1.0,
        voiceTone = prefs.voiceSettings?.voiceTone ?: "warm",
        conversationTime = prefs.conversationTime,
        preferredSleepHours = prefs.preferredSleepHours
    )
}
