package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UserApi {

    @GET("/api/users/me")
    suspend fun getUser(): User

    @PUT("/api/users/me")
    suspend fun updateUser(@Body user: User): User

    @GET("/api/users/me/preferences")
    suspend fun getPreferences(): UserPreferences

    @PUT("/api/users/me/preferences")
    suspend fun updatePreferences(@Body preferences: UserPreferences): UserPreferences

    @PUT("/api/users/me/preferences/birthday")
    suspend fun updateBirthday(@Body request: BirthdayUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/location")
    suspend fun updateLocation(@Body request: LocationUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/family-info")
    suspend fun updateFamilyInfo(@Body request: FamilyInfoUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/guardian-email")
    suspend fun updateGuardianEmail(@Body request: GuardianEmailUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/occupation")
    suspend fun updateOccupation(@Body request: OccupationUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/hobbies")
    suspend fun updateHobbies(@Body request: HobbiesUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/preferred-topics")
    suspend fun updatePreferredTopics(@Body request: PreferredTopicsUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/voice-settings")
    suspend fun updateVoiceSettings(@Body request: VoiceSettingsUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/conversation-time")
    suspend fun updateConversationTime(@Body request: ConversationTimeUpdateRequest): UserPreferences

    @PUT("/api/users/me/preferences/preferred-sleep-hours")
    suspend fun updatePreferredSleepHours(@Body request: PreferredSleepHoursUpdateRequest): UserPreferences

    @GET("/api/users/me/onboarding-status")
    suspend fun getOnboardingStatus(): OnboardingStatusResponse
}
