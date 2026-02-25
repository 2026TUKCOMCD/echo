package com.example.graduation_project.domain.usecase

import com.example.graduation_project.data.model.HealthData
import com.example.graduation_project.domain.health.HealthConnectAvailability
import com.example.graduation_project.domain.health.IHealthRepository
import com.example.graduation_project.domain.health.SleepSummary

/**
 * Health Connect에서 건강 데이터를 읽어 HealthData DTO로 반환하는 UseCase.
 *
 * - 가용성/권한 미충족 시 HealthData(all null) 반환 (graceful degradation)
 * - 각 데이터는 독립적으로 읽기 — 하나 실패해도 나머지는 계속 시도
 * - 이상치 제거(sanitize): 앱에서 전처리 후 서버에 정제된 값만 전달
 */
class GetHealthDataUseCase(
    private val repository: IHealthRepository
) {
    suspend operator fun invoke(): HealthData {
        if (repository.getAvailability() !is HealthConnectAvailability.Available) return HealthData()
        if (!runCatching { repository.hasPermissions() }.getOrDefault(false)) return HealthData()

        val sleep = runCatching { repository.readYesterdaySleep() }.getOrDefault(SleepSummary(null, null))
        val steps = runCatching { repository.readTodaySteps() }.getOrNull().sanitizeSteps()
        val exercise = runCatching { repository.readLatestExercise() }.getOrDefault(Pair(null, null))

        return HealthData(
            sleepDuration = sleep.minutes.sanitizeSleep(),
            sleepStartTime = sleep.startTime,
            steps = steps,
            exerciseDistance = exercise.first.sanitizeDistance(),
            exerciseActivity = exercise.second
        )
    }

    // 이상치 제거: 유효 범위 벗어나면 null (서버에서 "데이터 없음"으로 평가)
    private fun Int?.sanitizeSleep(): Int? = this?.takeIf { it in 30..720 }       // 30분 ~ 12시간
    private fun Int?.sanitizeSteps(): Int? = this?.takeIf { it in 1..50_000 }     // 최대 5만보
    private fun Double?.sanitizeDistance(): Double? = this?.takeIf { it in 0.01..100.0 } // 최대 100km
}
