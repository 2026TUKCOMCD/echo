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

// 요청 Model(DTO) -> /start에 대한 DTO
@Serializable
data class HealthData(
    val sleepDuration: Int? = null,
    val steps: Int? = null,
    val exerciseDistance: Double? = null,
    val exerciseActivity: String? = null
)
