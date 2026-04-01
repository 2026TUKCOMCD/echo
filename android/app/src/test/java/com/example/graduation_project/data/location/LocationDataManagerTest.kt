package com.example.graduation_project.data.location

import android.location.Location
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.domain.health.StayPointDetector
import com.example.graduation_project.domain.model.LocationPoint
import com.example.graduation_project.domain.model.StayPoint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LocationDataManagerTest {

    private lateinit var locationManager: LocationManager
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var stayPointDetector: StayPointDetector
    private lateinit var manager: LocationDataManager

    @Before
    fun setUp() {
        locationManager = mockk()
        healthConnectManager = mockk()
        stayPointDetector = mockk()
        manager = LocationDataManager(locationManager, healthConnectManager, stayPointDetector)
    }

    @Test
    fun `현재 위치 null이면 null 반환`() = runTest {
        coEvery { locationManager.getCurrentLocation() } returns null

        assertNull(manager.collectLocationData())
    }

    @Test
    fun `운동 기록 없으면 visitedPlaces 비어있고 totalDistanceKm 0`() = runTest {
        val location = mockk<Location>().apply {
            every { latitude } returns 37.5665
            every { longitude } returns 126.9780
        }
        coEvery { locationManager.getCurrentLocation() } returns location
        coEvery { healthConnectManager.readExerciseSessionLocations() } returns emptyList()
        every { stayPointDetector.detect(emptyList()) } returns emptyList()

        val result = manager.collectLocationData()

        assertNotNull(result)
        assertEquals(emptyList<Any>(), result!!.visitedPlaces)
        assertEquals(0.0, result.totalDistanceKm ?: 0.0, 0.001)
    }

    @Test
    fun `StayPoint가 RawVisitedPlace로 정상 변환됨`() = runTest {
        val location = mockk<Location>().apply {
            every { latitude } returns 37.5665
            every { longitude } returns 126.9780
        }
        val stayPoint = StayPoint(
            latitude = 37.5172,
            longitude = 127.0473,
            startTime = Instant.parse("2024-01-01T05:00:00Z"),
            endTime = Instant.parse("2024-01-01T06:30:00Z"),
            durationMinutes = 90
        )
        coEvery { locationManager.getCurrentLocation() } returns location
        coEvery { healthConnectManager.readExerciseSessionLocations() } returns emptyList()
        every { stayPointDetector.detect(emptyList()) } returns listOf(stayPoint)

        val result = manager.collectLocationData()

        assertNotNull(result)
        assertEquals(1, result!!.visitedPlaces.size)
        assertEquals(37.5172, result.visitedPlaces[0].latitude, 0.0001)
        assertEquals(127.0473, result.visitedPlaces[0].longitude, 0.0001)
        assertEquals(90, result.visitedPlaces[0].stayDurationMinutes)
    }

    @Test
    fun `이동 거리 계산 - 동일 좌표 두 세션`() = runTest {
        val location = mockk<Location>().apply {
            every { latitude } returns 37.5665
            every { longitude } returns 126.9780
        }
        // 약 1km 떨어진 두 점
        val locations = listOf(
            LocationPoint(37.5665, 126.9780, Instant.now()),
            LocationPoint(37.5755, 126.9780, Instant.now())  // 약 1km 북쪽
        )
        coEvery { locationManager.getCurrentLocation() } returns location
        coEvery { healthConnectManager.readExerciseSessionLocations() } returns listOf(locations)
        every { stayPointDetector.detect(locations) } returns emptyList()

        val result = manager.collectLocationData()

        assertNotNull(result)
        // 약 1km ± 0.1km 범위 내
        assertEquals(1.0, result!!.totalDistanceKm ?: 0.0, 0.1)
    }
}
