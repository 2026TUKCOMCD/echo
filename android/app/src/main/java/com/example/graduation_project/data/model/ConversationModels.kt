package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationResponse(
    val sessionId: String? = null,
    val message: String? = null,
    val audioUrl: String? = null
)

@Serializable
data class MessageRequest(
    val text: String
)

@Serializable
data class HealthData(
    val sleepDuration: Int? = null,
    val steps: Int? = null,
    val exerciseDistance: Double? = null,
    val exerciseActivity: String? = null
)
