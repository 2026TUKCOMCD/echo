package com.example.graduation_project.presentation.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.model.VoiceSettings
import com.example.graduation_project.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ONBOARDING_STEPS = 10

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

data class OnboardingUiState(
    val currentStep: Int = 0,
    val birthday: String = "",
    val familyInfo: String = "",
    val guardianEmail: String = "",
    val location: String = "",
    val occupation: String = "",
    val hobbies: String = "",
    val preferredTopics: String = "",
    val voiceSpeed: Double = 1.0,
    val voiceTone: String = "warm",
    val conversationTime: String = "",
    val alarmEnabled: Boolean = true,
    val preferredSleepHours: String = "",
    val fieldError: String? = null,
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

class OnboardingViewModel(
    application: Application,
    private val userRepository: UserRepository = UserRepository()
) : AndroidViewModel(application) {

    private val alarmStorage = ConversationAlarmStorage(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun updateBirthday(value: String) = _uiState.update { it.copy(birthday = value, fieldError = null) }
    fun updateFamilyInfo(value: String) = _uiState.update { it.copy(familyInfo = value, fieldError = null) }
    fun updateGuardianEmail(value: String) = _uiState.update { it.copy(guardianEmail = value, fieldError = null) }
    fun updateLocation(value: String) = _uiState.update { it.copy(location = value, fieldError = null) }
    fun updateOccupation(value: String) = _uiState.update { it.copy(occupation = value, fieldError = null) }
    fun updateHobbies(value: String) = _uiState.update { it.copy(hobbies = value, fieldError = null) }
    fun updatePreferredTopics(value: String) = _uiState.update { it.copy(preferredTopics = value, fieldError = null) }
    fun updateVoiceSpeed(value: Double) = _uiState.update { it.copy(voiceSpeed = value) }
    fun updateVoiceTone(value: String) = _uiState.update { it.copy(voiceTone = value) }
    fun updateConversationTime(value: String) = _uiState.update { it.copy(conversationTime = value, fieldError = null) }
    fun updateAlarmEnabled(value: Boolean) = _uiState.update { it.copy(alarmEnabled = value) }
    fun updatePreferredSleepHours(value: String) = _uiState.update { it.copy(preferredSleepHours = value, fieldError = null) }

    fun next() {
        val state = _uiState.value
        val error = validateStep(state)
        if (error != null) {
            _uiState.update { it.copy(fieldError = error) }
            return
        }
        if (state.currentStep < ONBOARDING_STEPS - 1) {
            _uiState.update { it.copy(currentStep = state.currentStep + 1, fieldError = null) }
        } else {
            submit()
        }
    }

    fun previous() {
        val step = _uiState.value.currentStep
        if (step > 0) _uiState.update { it.copy(currentStep = step - 1, fieldError = null) }
    }

    fun skip() {
        val step = _uiState.value.currentStep
        if (step < ONBOARDING_STEPS - 1) {
            _uiState.update { it.copy(currentStep = step + 1, fieldError = null) }
        } else {
            submit()
        }
    }

    private fun validateStep(state: OnboardingUiState): String? = when (state.currentStep) {
        0 -> if (state.birthday.isBlank()) "생년월일을 입력해주세요" else null
        1 -> if (state.location.isBlank()) "거주 지역을 입력해주세요" else null
        3 -> if (state.guardianEmail.isNotBlank() && !EMAIL_REGEX.matches(state.guardianEmail))
            "올바른 이메일 형식을 입력해주세요" else null
        9 -> if (state.preferredSleepHours.isNotBlank()) {
            val h = state.preferredSleepHours.toIntOrNull()
            when {
                h == null -> "숫자를 입력해주세요"
                h !in 1..24 -> "1~24 사이의 숫자를 입력해주세요"
                else -> null
            }
        } else null
        else -> null
    }

    private fun submit() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val preferences = UserPreferences(
                birthday = state.birthday.ifBlank { null },
                familyInfo = state.familyInfo.ifBlank { null },
                guardianEmail = state.guardianEmail.ifBlank { null },
                location = state.location.ifBlank { null },
                occupation = state.occupation.ifBlank { null },
                hobbies = state.hobbies.ifBlank { null },
                preferredTopics = state.preferredTopics.ifBlank { null },
                voiceSettings = VoiceSettings(voiceSpeed = state.voiceSpeed, voiceTone = state.voiceTone),
                conversationTime = state.conversationTime.ifBlank { null },
                preferredSleepHours = state.preferredSleepHours.toIntOrNull()
            )
            when (userRepository.updatePreferences(preferences)) {
                is ApiResult.Success -> {
                    // 알람 설정 로컬 저장 및 스케줄링
                    val time = state.conversationTime.ifBlank { null }
                    alarmStorage.saveConversationTime(time)
                    alarmStorage.setAlarmEnabled(state.alarmEnabled)

                    if (state.alarmEnabled && time != null) {
                        ConversationAlarmScheduler.scheduleAlarm(getApplication(), time)
                    }

                    _uiState.update { it.copy(isLoading = false, isCompleted = true) }
                }
                is ApiResult.Error -> {
                    // 저장 오류 발생 시, 네트워크 타임아웃 등으로 서버에 이미 저장됐을 수 있으므로 상태 재확인
                    when (val statusResult = userRepository.getOnboardingStatus()) {
                        is ApiResult.Success -> if (statusResult.data.completed) {
                            val time = state.conversationTime.ifBlank { null }
                            alarmStorage.saveConversationTime(time)
                            alarmStorage.setAlarmEnabled(state.alarmEnabled)
                            if (state.alarmEnabled && time != null) {
                                ConversationAlarmScheduler.scheduleAlarm(getApplication(), time)
                            }
                            _uiState.update { it.copy(isLoading = false, isCompleted = true) }
                        } else {
                            _uiState.update { it.copy(isLoading = false, errorMessage = "저장에 실패했습니다. 다시 시도해주세요.") }
                        }
                        is ApiResult.Error -> _uiState.update {
                            it.copy(isLoading = false, errorMessage = "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요.")
                        }
                    }
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return OnboardingViewModel(application) as T
            }
        }
    }
}
