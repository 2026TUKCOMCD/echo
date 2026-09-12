package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.UserApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.model.*

class UserRepository(
    private val userApi: UserApi = ApiClient.userApi
) {

    suspend fun getPreferences(): ApiResult<UserPreferences> {
        return safeApiCall { userApi.getPreferences() }
    }

    suspend fun updatePreferences(preferences: UserPreferences): ApiResult<UserPreferences> {
        return safeApiCall { userApi.updatePreferences(preferences) }
    }

    suspend fun updateBirthday(birthday: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateBirthday(BirthdayUpdateRequest(birthday)) }

    suspend fun updateLocation(location: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateLocation(LocationUpdateRequest(location)) }

    suspend fun updateHomeLocation(latitude: Double, longitude: Double): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateHomeLocation(HomeLocationUpdateRequest(latitude, longitude)) }

    suspend fun previewHomeAddress(latitude: Double, longitude: Double): ApiResult<HomeAddressPreview> =
        safeApiCall { userApi.previewHomeAddress(latitude, longitude) }

    suspend fun updateFamilyInfo(familyInfo: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateFamilyInfo(FamilyInfoUpdateRequest(familyInfo)) }

    suspend fun updateGuardianEmail(guardianEmail: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateGuardianEmail(GuardianEmailUpdateRequest(guardianEmail)) }

    suspend fun updateOccupation(occupation: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateOccupation(OccupationUpdateRequest(occupation)) }

    suspend fun updateHobbies(hobbies: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateHobbies(HobbiesUpdateRequest(hobbies)) }

    suspend fun updatePreferredTopics(preferredTopics: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updatePreferredTopics(PreferredTopicsUpdateRequest(preferredTopics)) }

    suspend fun updateVoiceSettings(voiceSpeed: Double?, voiceTone: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateVoiceSettings(VoiceSettingsUpdateRequest(voiceSpeed, voiceTone)) }

    suspend fun updateConversationTime(conversationTime: String?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updateConversationTime(ConversationTimeUpdateRequest(conversationTime)) }

    suspend fun updatePreferredSleepHours(preferredSleepHours: Int?): ApiResult<UserPreferences> =
        safeApiCall { userApi.updatePreferredSleepHours(PreferredSleepHoursUpdateRequest(preferredSleepHours)) }

    suspend fun getOnboardingStatus(): ApiResult<OnboardingStatusResponse> {
        return safeApiCall { userApi.getOnboardingStatus() }
    }

    suspend fun resetConversationData(): ApiResult<Unit> =
        safeApiCall { userApi.resetConversationData() }

    suspend fun seedDemoConversationData(): ApiResult<Unit> =
        safeApiCall { userApi.seedDemoConversationData() }
}
