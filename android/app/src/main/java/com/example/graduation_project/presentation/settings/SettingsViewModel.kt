package com.example.graduation_project.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
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
    val alarmEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    application: Application,
    private val userRepository: UserRepository = UserRepository()
) : AndroidViewModel(application) {

    private val alarmStorage = ConversationAlarmStorage(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // 로컬 알람 설정 로드
        val alarmEnabled = alarmStorage.isAlarmEnabled()
        _uiState.update { it.copy(alarmEnabled = alarmEnabled) }

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
                is ApiResult.Success -> {
                    _uiState.update {
                        applyPrefs(it, result.data).copy(isSaving = false, savedMessage = "저장되었습니다")
                    }

                    // 대화 시간 로컬 저장 및 알람 스케줄링
                    val time = result.data.conversationTime
                    alarmStorage.saveConversationTime(time)
                    updateAlarmSchedule(time)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = "저장에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    fun setAlarmEnabled(enabled: Boolean) {
        alarmStorage.setAlarmEnabled(enabled)
        _uiState.update { it.copy(alarmEnabled = enabled) }

        var time = _uiState.value.conversationTime

        // 시간이 설정되지 않은 경우 기본 시간(09:00) 설정
        if (enabled && time.isNullOrBlank()) {
            time = DEFAULT_CONVERSATION_TIME
            alarmStorage.saveConversationTime(time)
            _uiState.update { it.copy(conversationTime = time) }

            // 서버에도 기본 시간 저장
            viewModelScope.launch {
                val currentState = _uiState.value
                val preferences = UserPreferences(
                    birthday = currentState.birthday,
                    familyInfo = currentState.familyInfo,
                    guardianEmail = currentState.guardianEmail,
                    location = currentState.location,
                    occupation = currentState.occupation,
                    hobbies = currentState.hobbies,
                    preferredTopics = currentState.preferredTopics,
                    conversationTime = time,
                    preferredSleepHours = currentState.preferredSleepHours
                )
                userRepository.updatePreferences(preferences)
            }
        }

        updateAlarmSchedule(time)

        val message = if (enabled) "알림이 설정되었습니다" else "알림이 해제되었습니다"
        _uiState.update { it.copy(savedMessage = message) }
    }

    companion object {
        private const val DEFAULT_CONVERSATION_TIME = "09:00"
    }

    private fun updateAlarmSchedule(time: String?) {
        val context = getApplication<Application>()
        val enabled = _uiState.value.alarmEnabled

        if (enabled && !time.isNullOrBlank()) {
            ConversationAlarmScheduler.scheduleAlarm(context, time)
        } else {
            ConversationAlarmScheduler.cancelAlarm(context)
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
