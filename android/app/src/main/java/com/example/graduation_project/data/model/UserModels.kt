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
    val birthday: String? = null,               // 서버: LocalDate → "yyyy-MM-dd"
    val location: String? = null,
    val familyInfo: String? = null,             // renamed: familyRelation → familyInfo
    val occupation: String? = null,
    val hobbies: String? = null,                // renamed: hobby → hobbies
    val preferredTopics: String? = null,        // type changed: List<String> → String
    val voiceSettings: VoiceSettings? = null,
    val conversationTime: String? = null,       // 서버: LocalTime → "HH:mm"
    val preferredSleepHours: Int? = null
)

@Serializable
data class VoiceSettings(
    val voiceSpeed: Double = 1.0,
    val voiceTone: String = "warm"
)
