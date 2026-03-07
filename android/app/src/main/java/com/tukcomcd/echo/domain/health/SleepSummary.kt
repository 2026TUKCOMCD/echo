package com.tukcomcd.echo.domain.health

/**
 * 수면 요약 도메인 모델.
 * 서버 낮잠/야간 수면 분류를 위해 수면 시작 시각을 함께 전달.
 *
 * @param minutes    총 수면 시간 (분), 데이터 없으면 null
 * @param startTime  주 수면 세션 시작 시각 "HH:mm" (로컬 타임존), 데이터 없으면 null
 *                   서버: startTime < "18:00" → 낮잠, ≥ "18:00" → 야간 수면
 * @param wakeUpTime 주 수면 세션 종료 시각 "HH:mm" (로컬 타임존), 데이터 없으면 null
 */
data class SleepSummary(
    val minutes: Int?,
    val startTime: String?,
    val wakeUpTime: String?
)
