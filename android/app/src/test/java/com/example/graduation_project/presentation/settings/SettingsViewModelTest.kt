package com.example.graduation_project.presentation.settings

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.repository.UserRepository
import com.example.graduation_project.presentation.permission.PermissionChecker
import com.example.graduation_project.util.DeviceUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class SettingsViewModelTest {

    private lateinit var context: Application
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var shadowPowerManager: ShadowPowerManager
    private lateinit var mockUserRepository: UserRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Dispatchers.setMain(testDispatcher)

        // ShadowPowerManager 설정
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowPowerManager = shadowOf(powerManager)

        // UserRepository mock 설정
        mockUserRepository = mockk()
        coEvery { mockUserRepository.getPreferences() } returns ApiResult.Success(UserPreferences())

        // 기본 mock 설정
        mockkObject(PermissionChecker)
        mockkObject(LocationScheduler)
        mockkObject(LocationCollectionService)
        mockkObject(DeviceUtil)

        // 기본값 설정
        every { PermissionChecker.hasForegroundLocationPermission(any()) } returns false
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns false
        every { PermissionChecker.hasNotificationPermission(any()) } returns true
        every { LocationCollectionService.isRunning } returns false
        every { LocationScheduler.enableLocationCollection(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ===== 삼성 기기 배터리 설정 다이얼로그 (2개) =====

    @Test
    fun `배터리최적화_해제요청시_삼성기기면_삼성다이얼로그_이벤트_발생`() = runTest {
        // Given: 삼성 기기, 배터리 최적화 해제 안 됨, 백그라운드 권한 새로 허용
        every { DeviceUtil.isSamsungDevice() } returns true
        setBatteryOptimizationIgnored(false)

        // 이전 상태: 백그라운드 권한 없음
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns false

        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 백그라운드 권한이 새로 허용됨
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns true
        viewModel.refreshPermissionStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 삼성 다이얼로그 이벤트 발생
        assertTrue(
            "삼성 기기에서 삼성 다이얼로그 이벤트가 발생해야 함",
            viewModel.uiState.value.shouldShowSamsungBatteryDialog
        )
    }

    @Test
    fun `배터리최적화_해제요청시_삼성기기_아니면_삼성다이얼로그_이벤트_미발생`() = runTest {
        // Given: 삼성 기기 아님, 배터리 최적화 해제 안 됨, 백그라운드 권한 새로 허용
        every { DeviceUtil.isSamsungDevice() } returns false
        setBatteryOptimizationIgnored(false)

        // 이전 상태: 백그라운드 권한 없음
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns false

        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 백그라운드 권한이 새로 허용됨
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns true
        viewModel.refreshPermissionStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 삼성 다이얼로그 이벤트 미발생
        assertFalse(
            "삼성 기기가 아니면 삼성 다이얼로그 이벤트가 발생하지 않아야 함",
            viewModel.uiState.value.shouldShowSamsungBatteryDialog
        )
    }

    // ===== 헬퍼 메서드 =====

    private fun setBatteryOptimizationIgnored(ignored: Boolean) {
        shadowPowerManager.setIgnoringBatteryOptimizations(context.packageName, ignored)
    }
}
