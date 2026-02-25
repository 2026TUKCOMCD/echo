package com.example.graduation_project.data.health

import com.example.graduation_project.domain.health.HealthConnectAvailability
import com.example.graduation_project.domain.health.IHealthRepository
import com.example.graduation_project.domain.health.SleepSummary

/**
 * IHealthRepository 구현체.
 * HealthConnectManager에 실제 데이터 접근을 위임.
 */
class HealthConnectRepositoryImpl(
    private val manager: HealthConnectManager
) : IHealthRepository {

    override fun getAvailability(): HealthConnectAvailability = manager.checkAvailability()

    override suspend fun hasPermissions(): Boolean = manager.checkGrantedPermissions()

    override suspend fun readYesterdaySleep(): SleepSummary = manager.readYesterdaySleep()

    override suspend fun readTodaySteps(): Int? = manager.readTodaySteps()

    override suspend fun readLatestExercise(): Pair<Double?, String?> = manager.readLatestExercise()
}
