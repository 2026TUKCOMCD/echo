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
data class VoiceSettings(
    val voiceSpeed: Double = 1.0,
    val voiceTone: String = "warm"
)
