package com.example.graduation_project.data.location

import android.location.Location
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

// averageLocations()가 실제 android.location.Location 인스턴스를 생성/변경하므로(Location("averaged").apply { ... }),
// 순수 JVM 스텁(android.jar)이 아니라 실동작하는 Robolectric shadow가 필요하다. (프로젝트의 기존 패턴,
// 예: LocationSchedulerTest, LocationCollectionAlarmReceiverTest)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
@OptIn(ExperimentalCoroutinesApi::class)
class LocationManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockFusedClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager

    @Before
    fun setUp() {
        mockFusedClient = mockk()
        locationManager = LocationManager(mockFusedClient)
    }

    // ===== 1. 정상 조회 =====

    @Test
    fun `getCurrentLocation 성공 시 Location 반환`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val expected = mockk<Location>(relaxed = true)
            stubCurrentLocation(successResult = expected)
            stubLastLocation(successResult = null)

            val result = locationManager.getCurrentLocation()

            assertEquals(expected, result)
        }

    // ===== 2. 폴백 처리 =====

    @Test
    fun `getCurrentLocation null 시 getLastKnownLocation 폴백 호출`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fallback = mockk<Location>(relaxed = true)
            stubCurrentLocation(successResult = null)
            stubLastLocation(successResult = fallback)

            val result = locationManager.getCurrentLocation()

            assertEquals(fallback, result)
            verify(exactly = 1) { mockFusedClient.lastLocation }
        }

    @Test
    fun `getLastKnownLocation도 null 시 null 반환`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubCurrentLocation(successResult = null)
            stubLastLocation(successResult = null)

            val result = locationManager.getCurrentLocation()

            assertNull(result)
        }

    // ===== 3. 타임아웃 =====

    @Test
    fun `5초 타임아웃 시 getLastKnownLocation 폴백 호출`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fallback = mockk<Location>(relaxed = true)
            stubCurrentLocationHanging()
            stubLastLocation(successResult = fallback)

            val deferred = async { locationManager.getCurrentLocation() }
            advanceTimeBy(5_001L)
            advanceUntilIdle()

            assertEquals(fallback, deferred.await())
            verify(exactly = 1) { mockFusedClient.lastLocation }
        }

    // ===== 4. 집 등록용 다중 샘플 평균 =====

    @Test
    fun `getAveragedCurrentLocation 성공 시 위도경도 평균 반환`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val sample1 = realLocation(37.5665, 126.9780)
            val sample2 = realLocation(37.5666, 126.9781)
            val sample3 = realLocation(37.5664, 126.9779)
            stubCurrentLocationSequence(sample1, sample2, sample3)
            stubLastLocation(successResult = null)

            val result = locationManager.getAveragedCurrentLocation(sampleCount = 3, sampleDelayMs = 1_000L)

            assertEquals(37.5665, result?.latitude ?: 0.0, 0.0001)
            assertEquals(126.9780, result?.longitude ?: 0.0, 0.0001)
            verify(exactly = 3) { mockFusedClient.getCurrentLocation(any<Int>(), any<CancellationToken>()) }
        }

    @Test
    fun `getAveragedCurrentLocation 일부 샘플 실패해도 유효한 샘플만으로 평균`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val sample1 = realLocation(37.5665, 126.9780)
            val sample3 = realLocation(37.5667, 126.9782)
            stubCurrentLocationSequence(sample1, null, sample3)
            stubLastLocation(successResult = null)

            val result = locationManager.getAveragedCurrentLocation(sampleCount = 3, sampleDelayMs = 1_000L)

            assertEquals(37.5666, result?.latitude ?: 0.0, 0.0001)
        }

    @Test
    fun `getAveragedCurrentLocation 모든 샘플 실패 시 null 반환`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubCurrentLocationSequence(null, null, null)
            stubLastLocation(successResult = null)

            val result = locationManager.getAveragedCurrentLocation(sampleCount = 3, sampleDelayMs = 1_000L)

            assertNull(result)
        }

    @Test
    fun `getAveragedCurrentLocation 일부 샘플 실패해도 lastLocation 폴백을 쓰지 않는다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val sample1 = realLocation(37.5665, 126.9780)
            val sample3 = realLocation(37.5667, 126.9782)
            // lastLocation이 완전히 다른 곳(오래된 캐시)을 갖고 있어도 평균에 섞이면 안 된다
            val staleCachedLocation = realLocation(37.9000, 127.5000)
            stubCurrentLocationSequence(sample1, null, sample3)
            stubLastLocation(successResult = staleCachedLocation)

            val result = locationManager.getAveragedCurrentLocation(sampleCount = 3, sampleDelayMs = 1_000L)

            assertEquals(37.5666, result?.latitude ?: 0.0, 0.0001)
            verify(exactly = 0) { mockFusedClient.lastLocation }
        }

    // ===== 헬퍼 =====

    private fun realLocation(lat: Double, lng: Double): Location {
        val location = mockk<Location>(relaxed = true)
        every { location.latitude } returns lat
        every { location.longitude } returns lng
        return location
    }

    private fun stubCurrentLocationSequence(vararg results: Location?) {
        val tasks = results.map { result ->
            val task = mockk<Task<Location>>()
            every { task.addOnSuccessListener(any<OnSuccessListener<Location>>()) } answers {
                firstArg<OnSuccessListener<Location>>().onSuccess(result)
                task
            }
            every { task.addOnFailureListener(any()) } returns task
            task
        }
        every { mockFusedClient.getCurrentLocation(any<Int>(), any<CancellationToken>()) } returnsMany tasks
    }

    private fun stubCurrentLocation(successResult: Location?) {
        val task = mockk<Task<Location>>()
        every { mockFusedClient.getCurrentLocation(any<Int>(), any<CancellationToken>()) } returns task
        every { task.addOnSuccessListener(any<OnSuccessListener<Location>>()) } answers {
            firstArg<OnSuccessListener<Location>>().onSuccess(successResult)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
    }

    private fun stubCurrentLocationHanging() {
        val task = mockk<Task<Location>>()
        every { mockFusedClient.getCurrentLocation(any<Int>(), any<CancellationToken>()) } returns task
        every { task.addOnSuccessListener(any<OnSuccessListener<Location>>()) } returns task
        every { task.addOnFailureListener(any()) } returns task
    }

    private fun stubLastLocation(successResult: Location?) {
        val task = mockk<Task<Location>>()
        every { mockFusedClient.lastLocation } returns task
        every { task.addOnSuccessListener(any<OnSuccessListener<Location>>()) } answers {
            firstArg<OnSuccessListener<Location>>().onSuccess(successResult)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description?) = Dispatchers.resetMain()
}
