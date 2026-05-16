package com.example.graduation_project.presentation.settings

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
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationCollectionStorage
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.repository.UserRepository
import com.example.graduation_project.presentation.permission.PermissionChecker
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
    // 위치 수집 설정
    val locationCollectionStartTime: String = "06:00",
    val isLocationCollectionRunning: Boolean = false,
    // 권한 상태
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasHealthConnectPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
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
    private val locationStorage = LocationCollectionStorage(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val context = getApplication<Application>()

        // 로컬 알람 설정 로드
        val alarmEnabled = alarmStorage.isAlarmEnabled()

        // 위치 수집 시간 로드
        val locationStartTime = locationStorage.getStartTime()

        // 권한 상태 확인
        val hasLocation = PermissionChecker.hasForegroundLocationPermission(context)
        val hasBackgroundLocation = PermissionChecker.hasBackgroundLocationPermission(context)
        val hasHealthConnect = PermissionChecker.isHealthConnectAvailable(context)
        val hasNotification = PermissionChecker.hasNotificationPermission(context)

        _uiState.update {
            it.copy(
                alarmEnabled = alarmEnabled,
                locationCollectionStartTime = locationStartTime,
                isLocationCollectionRunning = LocationCollectionService.isRunning,
                hasLocationPermission = hasLocation,
                hasBackgroundLocationPermission = hasBackgroundLocation,
                hasHealthConnectPermission = hasHealthConnect,
                hasNotificationPermission = hasNotification
            )
        }

        viewModelScope.launch {
            when (val result = userRepository.getPreferences()) {
                is ApiResult.Success -> _uiState.update { applyPrefs(it, result.data).copy(isLoading = false) }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 권한 상태 새로고침 (설정에서 돌아왔을 때 호출)
     * 위치 권한이 새로 허용되었으면 서비스 시작
     */
    fun refreshPermissionStatus() {
        val context = getApplication<Application>()
        val previousBackgroundPermission = _uiState.value.hasBackgroundLocationPermission
        val currentBackgroundPermission = PermissionChecker.hasBackgroundLocationPermission(context)

        _uiState.update {
            it.copy(
                isLocationCollectionRunning = LocationCollectionService.isRunning,
                hasLocationPermission = PermissionChecker.hasForegroundLocationPermission(context),
                hasBackgroundLocationPermission = currentBackgroundPermission,
                hasHealthConnectPermission = PermissionChecker.isHealthConnectAvailable(context),
                hasNotificationPermission = PermissionChecker.hasNotificationPermission(context)
            )
        }

        // 위치 권한이 새로 허용되었으면 서비스 시작
        if (!previousBackgroundPermission && currentBackgroundPermission) {
            LocationScheduler.enableLocationCollection(context)
        }
    }

    /**
     * 위치 수집 시작/중지 토글
     */
    fun toggleLocationCollection() {
        val context = getApplication<Application>()
        if (LocationCollectionService.isRunning) {
            LocationCollectionService.stop(context)
            _uiState.update { it.copy(isLocationCollectionRunning = false, savedMessage = "위치 수집이 중지되었습니다") }
        } else {
            LocationCollectionService.start(context)
            _uiState.update { it.copy(isLocationCollectionRunning = true, savedMessage = "위치 수집이 시작되었습니다") }
        }
    }

    /**
     * 위치 수집 시작 시간 설정
     */
    fun setLocationCollectionStartTime(time: String) {
        locationStorage.saveStartTime(time)
        _uiState.update { it.copy(locationCollectionStartTime = time) }

        // 알람 재스케줄링
        val context = getApplication<Application>()
        LocationScheduler.scheduleMorningAlarm(context)

        _uiState.update { it.copy(savedMessage = "위치 수집 시간이 설정되었습니다") }
    }

    fun updateBirthday(birthday: String?) = updateField { userRepository.updateBirthday(birthday) }
    fun updateLocation(location: String?) = updateField { userRepository.updateLocation(location) }
    fun updateFamilyInfo(familyInfo: String?) = updateField { userRepository.updateFamilyInfo(familyInfo) }
    fun updateGuardianEmail(guardianEmail: String?) = updateField { userRepository.updateGuardianEmail(guardianEmail) }
    fun updateOccupation(occupation: String?) = updateField { userRepository.updateOccupation(occupation) }
    fun updateHobbies(hobbies: String?) = updateField { userRepository.updateHobbies(hobbies) }
    fun updatePreferredTopics(preferredTopics: String?) = updateField { userRepository.updatePreferredTopics(preferredTopics) }
    fun updateVoiceSettings(voiceSpeed: Double, voiceTone: String) = updateField { userRepository.updateVoiceSettings(voiceSpeed, voiceTone) }
    fun updatePreferredSleepHours(hours: Int?) = updateField { userRepository.updatePreferredSleepHours(hours) }

    fun updateConversationTime(time: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = userRepository.updateConversationTime(time)) {
                is ApiResult.Success -> {
                    val savedTime = result.data.conversationTime
                    alarmStorage.saveConversationTime(savedTime)
                    updateAlarmSchedule(savedTime)
                    _uiState.update { applyPrefs(it, result.data).copy(isSaving = false, savedMessage = "저장되었습니다") }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = "저장에 실패했습니다. 다시 시도해주세요.")
                }
            }
        }
    }

    private fun updateField(apiCall: suspend () -> ApiResult<UserPreferences>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = apiCall()) {
                is ApiResult.Success -> _uiState.update {
                    applyPrefs(it, result.data).copy(isSaving = false, savedMessage = "저장되었습니다")
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
                userRepository.updateConversationTime(time)
            }
        }

        updateAlarmSchedule(time)

        val message = if (enabled) "알림이 설정되었습니다" else "알림이 해제되었습니다"
        _uiState.update { it.copy(savedMessage = message) }
    }

    companion object {
        private const val DEFAULT_CONVERSATION_TIME = "09:00"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return SettingsViewModel(application) as T
            }
        }
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
