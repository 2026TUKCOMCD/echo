package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

/**
 * 루틴 방문 장소(회사/병원 등 반복 방문 장소) 관련 모델.
 * 서버는 정확한 상호명/주소를 저장하지 않고 좌표+카테고리 라벨+루틴 요일/시간대만 저장한다
 * (최소 수집 원칙). previewAddress는 저장된 값이 아니라 조회 시점에 그때그때 역지오코딩한 결과.
 */
@Serializable
enum class RoutinePlaceStatus { SUGGESTED, CONFIRMED, DISMISSED }

@Serializable
data class RoutinePlaceResponse(
    val id: Long,
    val status: RoutinePlaceStatus,
    val category: String? = null,
    val routineDays: List<String> = emptyList(),
    val routineTimeRangeStart: String? = null,
    val routineTimeRangeEnd: String? = null,
    val occurrenceCount: Int? = null,
    val previewAddress: String? = null,
    val lastDetectedAt: String? = null,
    val confirmedAt: String? = null
)

@Serializable
data class RoutinePlaceConfirmRequest(val category: String)

@Serializable
data class ConsentResponse(val consented: Boolean, val consentedAt: String? = null)

@Serializable
data class ConsentUpdateRequest(val consented: Boolean)
