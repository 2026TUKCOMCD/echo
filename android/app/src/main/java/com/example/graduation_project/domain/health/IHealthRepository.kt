package com.example.graduation_project.domain.health

/**
 * Health Connect 데이터 접근 추상화 인터페이스.
 * domain 레이어가 data 레이어를 직접 참조하지 않도록 의존성 역전.
 */
interface IHealthRepository {

    /** Health Connect SDK 가용성 확인 (동기, 비suspend) */
    fun getAvailability(): HealthConnectAvailability

    /** 모든 필수 권한이 부여되었는지 확인 */
    suspend fun hasPermissions(): Boolean

    /**
     * 어제 정오(12:00) → 오늘 정오(12:00) 범위의 수면 데이터 읽기.
     * @return SleepSummary(minutes, startTime), 데이터 없으면 SleepSummary(null, null)
     */
    suspend fun readYesterdaySleep(): SleepSummary

    /**
     * 오늘 자정(00:00) → 현재 범위의 걸음 수 합산.
     * @return 걸음 수, 데이터 없으면 null
     */
    suspend fun readTodaySteps(): Int?

    /**
     * 최근 24시간 내 가장 최근 운동 세션 읽기.
     * @return Pair(거리km, 운동종류한국어), 데이터 없으면 Pair(null, null)
     */
    suspend fun readLatestExercise(): Pair<Double?, String?>
}
