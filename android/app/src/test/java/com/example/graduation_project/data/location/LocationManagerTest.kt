package com.example.graduation_project.data.location

import android.location.Location
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

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
            val expected = mockk<Location>()
            stubCurrentLocation(successResult = expected)
            stubLastLocation(successResult = null)

            val result = locationManager.getCurrentLocation()

            assertEquals(expected, result)
        }

    // ===== 2. 폴백 처리 =====

    @Test
    fun `getCurrentLocation null 시 getLastKnownLocation 폴백 호출`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fallback = mockk<Location>()
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
            val fallback = mockk<Location>()
            stubCurrentLocationHanging()
            stubLastLocation(successResult = fallback)

            val deferred = async { locationManager.getCurrentLocation() }
            advanceTimeBy(5_001L)
            advanceUntilIdle()

            assertEquals(fallback, deferred.await())
            verify(exactly = 1) { mockFusedClient.lastLocation }
        }

    // ===== 헬퍼 =====

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
