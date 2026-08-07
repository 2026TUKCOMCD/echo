package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

/**
 * 서버 DiaryResponse에 대응하는 일기 DTO
 *
 * - status=FAILED면 content는 null이거나 이전 성공본 (failureReason에 실패 사유)
 * - date는 "yyyy-MM-dd" (Asia/Seoul 기준)
 */
@Serializable
data class Diary(
    val id: Long,
    val date: String,
    val title: String? = null,
    val content: String? = null,
    val status: String = "SUCCESS",
    val failureReason: String? = null,
    val weather: String? = null,
    val mood: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
