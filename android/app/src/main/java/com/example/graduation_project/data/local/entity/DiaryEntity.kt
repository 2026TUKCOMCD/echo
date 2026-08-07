package com.example.graduation_project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일기 로컬 캐시 엔티티
 *
 * 서버(GET /api/diaries)에서 받은 일기를 캐시해 오프라인에서도 일기 탭을 열람 가능하게 함
 * - 하루 1개 일기이므로 날짜("yyyy-MM-dd", Asia/Seoul 기준)가 PK
 * - status=FAILED인 실패 기록도 캐시해 실패 사유를 표시 (디버깅 단계)
 */
@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey val date: String,   // "yyyy-MM-dd"
    val serverId: Long,
    val title: String?,
    val content: String?,
    val status: String,             // "SUCCESS" | "FAILED"
    val failureReason: String?,
    val weather: String?,
    val mood: String?,
    val updatedAt: String?
)
