package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

// /api/conversations/start 응답 Model(DTO)
@Serializable
data class ConversationStartResponse(
    val message: String? = null,
    val audioData: String? = null,
    val timestamp: String? = null
)

// /api/conversations/message 응답 Model(DTO)
@Serializable
data class ConversationMessageResponse(
    val userMessage: String? = null,
    val aiResponse: String? = null,
    val audioData: String? = null,
    val timestamp: String? = null
)

// /api/conversations/end 응답 Model(DTO)
@Serializable
data class ConversationEndResponse(
    val endedAt: String? = null
)

// /api/conversations/tts-retry 응답 Model(DTO)
@Serializable
data class TtsRetryResponse(
    val audioData: String? = null
)

// 요청 Model(DTO) -> /start에 대한 DTO
@Serializable
data class HealthData(
    val sleepDurationMinutes: Int? = null,   // renamed: sleepDuration → sleepDurationMinutes
    val sleepStartTime: String? = null,      // "HH:mm" 형식, 서버 낮잠/야간 수면 분류용
    val wakeUpTime: String? = null,          // "HH:mm" 형식, 기상 시각
    val steps: Int? = null,
    val exerciseDistanceKm: Double? = null,  // renamed: exerciseDistance → exerciseDistanceKm
    val exerciseActivity: String? = null,
    val activityList: String? = null         // 활동 목록 (추후 구현)
)
