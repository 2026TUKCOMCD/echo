package com.example.graduation_project.domain.health

/**
 * 수면 요약 도메인 모델.
 * 서버 낮잠/야간 수면 분류를 위해 수면 시작 시각을 함께 전달.
 * 세 필드 모두 주 수면 세션(범위 내 최장 세션) 하나를 가리킴 —
 * 시간과 시각이 같은 세션을 설명해야 서버 분류가 일관됨.
 *
 * @param minutes    주 수면 세션의 수면 시간 (분), 데이터 없으면 null
 * @param startTime  주 수면 세션 시작 시각 "HH:mm" (로컬 타임존), 데이터 없으면 null
 *                   서버: startTime < "18:00" → 낮잠, ≥ "18:00" → 야간 수면
 * @param wakeUpTime 주 수면 세션 종료 시각 "HH:mm" (로컬 타임존), 데이터 없으면 null
 */
data class SleepSummary(
    val minutes: Int?,
    val startTime: String?,
    val wakeUpTime: String?
)
