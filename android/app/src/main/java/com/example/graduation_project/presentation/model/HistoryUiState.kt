package com.example.graduation_project.presentation.model

data class ConversationSummary(
    val conversationId: String,
    val date: String,       // "2025년 3월 19일 (수)"
    val timeRange: String,  // "오전 10:30 ~ 10:45"
    val durationMin: Int,
    val previewText: String
)
