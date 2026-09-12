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
    val confirmedAt: String? = null,
    // 요일/시간대를 사용자가 직접 수정했는지 - true면 야간 재계산이 이 값을 덮어쓰지 않음
    val manualSchedule: Boolean = false
)

/**
 * 후보 확정 / 라벨·일정 수정 요청.
 * routineDays/routineTimeRange*는 선택 항목 - null(비움)이면 시스템이 감지한 값을 그대로 두고,
 * 채워 보내면 사용자가 직접 고친 것으로 간주해 이후 야간 재계산이 덮어쓰지 않는다.
 */
@Serializable
data class RoutinePlaceConfirmRequest(
    val category: String,
    val routineDays: List<String>? = null,
    val routineTimeRangeStart: String? = null,
    val routineTimeRangeEnd: String? = null
)

@Serializable
data class ConsentResponse(val consented: Boolean, val consentedAt: String? = null)

@Serializable
data class ConsentUpdateRequest(val consented: Boolean)
