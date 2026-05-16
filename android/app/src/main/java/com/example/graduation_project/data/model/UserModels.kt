package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val name: String,
    val age: Int? = null,
    val birthday: String? = null
)

@Serializable
data class UserPreferences(
    val userId: Long? = null,
    val name: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    val location: String? = null,
    val familyInfo: String? = null,
    val guardianEmail: String? = null,
    val occupation: String? = null,
    val hobbies: String? = null,
    val preferredTopics: String? = null,
    val voiceSettings: VoiceSettings? = null,
    val conversationTime: String? = null,
    val preferredSleepHours: Int? = null
)

@Serializable
data class OnboardingStatusResponse(
    val completed: Boolean
)

@Serializable
data class BirthdayUpdateRequest(val birthday: String?)

@Serializable
data class LocationUpdateRequest(val location: String?)

@Serializable
data class FamilyInfoUpdateRequest(val familyInfo: String?)

@Serializable
data class GuardianEmailUpdateRequest(val guardianEmail: String?)

@Serializable
data class OccupationUpdateRequest(val occupation: String?)

@Serializable
data class HobbiesUpdateRequest(val hobbies: String?)

@Serializable
data class PreferredTopicsUpdateRequest(val preferredTopics: String?)

@Serializable
data class VoiceSettingsUpdateRequest(val voiceSpeed: Double?, val voiceTone: String?)

@Serializable
data class ConversationTimeUpdateRequest(val conversationTime: String?)

@Serializable
data class PreferredSleepHoursUpdateRequest(val preferredSleepHours: Int?)

@Serializable
data class VoiceSettings(
    val voiceSpeed: Double = 1.0,
    val voiceTone: String = "warm"
)
