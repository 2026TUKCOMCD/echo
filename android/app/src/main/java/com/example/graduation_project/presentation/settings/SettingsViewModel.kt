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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

        // 현재 시간이 위치 수집 범위 내이면 자동으로 서비스 시작
        val conversationTime = _uiState.value.conversationTime
        if (!conversationTime.isNullOrBlank() && isCurrentTimeInRange(time, conversationTime)) {
            if (!LocationCollectionService.isRunning && _uiState.value.hasBackgroundLocationPermission) {
                LocationCollectionService.start(context)
                _uiState.update { it.copy(isLocationCollectionRunning = true, savedMessage = "위치 수집이 자동으로 시작되었습니다") }
                return
            }
        }

        _uiState.update { it.copy(savedMessage = "위치 수집 시간이 설정되었습니다") }
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

                    // 현재 시간이 위치 수집 범위 내이면 자동으로 서비스 시작
                    val locationStartTime = _uiState.value.locationCollectionStartTime
                    if (!time.isNullOrBlank() && isCurrentTimeInRange(locationStartTime, time)) {
                        val context = getApplication<Application>()
                        if (!LocationCollectionService.isRunning && _uiState.value.hasBackgroundLocationPermission) {
                            LocationCollectionService.start(context)
                            _uiState.update { it.copy(isLocationCollectionRunning = true, savedMessage = "저장되었습니다. 위치 수집이 자동으로 시작되었습니다") }
                        }
                    }
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

    /**
     * 현재 시간이 startTime ~ endTime 범위 내인지 확인
     */
    private fun isCurrentTimeInRange(startTime: String, endTime: String): Boolean {
        return try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val now = LocalTime.now()
            val start = LocalTime.parse(startTime, formatter)
            val end = LocalTime.parse(endTime, formatter)

            if (start.isBefore(end) || start == end) {
                // 일반적인 경우: 06:00 ~ 09:00
                now in start..end
            } else {
                // 자정을 넘기는 경우: 22:00 ~ 06:00
                now >= start || now <= end
            }
        } catch (e: Exception) {
            false
        }
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
