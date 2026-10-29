package com.example.graduation_project.domain.usecase

import com.example.graduation_project.data.model.HealthData
import com.example.graduation_project.domain.health.HealthConnectAvailability
import com.example.graduation_project.domain.health.IHealthRepository
import com.example.graduation_project.domain.health.SleepSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Health Connect 권한은 데이터 종류마다 따로 켜고 끌 수 있으므로, 일부만 허용해도
 * 허용된 종류는 읽어야 한다 (예: 쓰지 않는 심박수만 거부해도 수면·걸음·운동은 전달).
 */
class GetHealthDataUseCaseTest {

    private val repository = mockk<IHealthRepository>()
    private val useCase = GetHealthDataUseCase(repository)

    @Before
    fun setUp() {
        every { repository.getAvailability() } returns HealthConnectAvailability.Available
        coEvery { repository.readYesterdaySleep() } returns SleepSummary(420, "23:00", "06:00")
        coEvery { repository.readTodaySteps() } returns 3_000
        coEvery { repository.readLatestExercise() } returns (1.5 to "걷기")
        coEvery { repository.readTodayActivityList() } returns "걷기"
    }

    @Test
    fun `권한을 모두 허용하면 모든 데이터를 읽는다`() = runTest {
        val data = useCase()

        assertEquals(420, data.sleepDurationMinutes)
        assertEquals("23:00", data.sleepStartTime)
        assertEquals(3_000, data.steps)
        assertEquals(1.5, data.exerciseDistanceKm!!, 0.0)
        assertEquals("걷기", data.activityList)
    }

    @Test
    fun `일부 권한이 없으면 그 종류만 비우고 허용된 데이터는 읽는다`() = runTest {
        // 수면 권한이 꺼져 있으면 Health Connect는 읽기에서 SecurityException을 던진다
        coEvery { repository.readYesterdaySleep() } throws SecurityException("READ_SLEEP 권한 없음")

        val data = useCase()

        assertNull(data.sleepDurationMinutes)
        assertNull(data.wakeUpTime)
        assertEquals(3_000, data.steps)
        assertEquals("걷기", data.exerciseActivity)
    }

    @Test
    fun `Health Connect를 쓸 수 없으면 아무것도 읽지 않는다`() = runTest {
        every { repository.getAvailability() } returns HealthConnectAvailability.NotInstalled

        assertEquals(HealthData(), useCase())
        coVerify(exactly = 0) { repository.readTodaySteps() }
    }
}
