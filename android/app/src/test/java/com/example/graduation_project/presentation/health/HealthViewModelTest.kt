package com.example.graduation_project.presentation.health

import android.app.Application
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.domain.health.HealthConnectAvailability
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * HealthViewModel 단위 테스트
 *
 * ## 테스트 대상
 * - checkPermissions() 상태 전이: Denied 상태가 NotRequested로 덮어씌워지는 버그 수정 검증
 *
 * ## 검증 시나리오
 * | 현재 상태    | allGranted | 기대 상태      |
 * |------------|------------|--------------|
 * | Denied     | false      | Denied (유지) |
 * | NotRequested | false    | NotRequested |
 * | Denied     | true       | Granted      |
 * | Granted    | false      | NotRequested |
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    @get:Rule
    val mainDispatcherRule = HealthMainDispatcherRule()

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockManager = mockk<HealthConnectManager>()

    @Before
    fun setUp() {
        // checkInitialState()에서 Available 반환, 초기 권한은 미부여
        every { mockManager.checkAvailability() } returns HealthConnectAvailability.Available
        coEvery { mockManager.checkGrantedPermissions() } returns false
    }

    private fun createViewModel(): HealthViewModel =
        HealthViewModel(mockApplication, mockManager)

    // ===== checkPermissions 상태 전이 테스트 =====

    @Test
    fun `checkPermissions - Denied 상태에서 권한 미부여 시 Denied 유지`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()                  // checkInitialState() 완료 → NotRequested

            vm.onPermissionsResult(emptySet())  // → Denied
            assertEquals(PermissionState.Denied, vm.uiState.value.permissionState)

            // onResume → refreshPermissions() 시뮬레이션 (allGranted=false)
            vm.refreshPermissions()
            advanceUntilIdle()

            assertEquals(PermissionState.Denied, vm.uiState.value.permissionState)
        }

    @Test
    fun `checkPermissions - NotRequested 상태에서 권한 미부여 시 NotRequested 유지`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()  // checkInitialState() 완료 → NotRequested

            assertEquals(PermissionState.NotRequested, vm.uiState.value.permissionState)

            vm.refreshPermissions()
            advanceUntilIdle()

            assertEquals(PermissionState.NotRequested, vm.uiState.value.permissionState)
        }

    @Test
    fun `checkPermissions - Denied 상태에서 권한 부여 시 Granted 전환`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onPermissionsResult(emptySet())  // → Denied
            assertEquals(PermissionState.Denied, vm.uiState.value.permissionState)

            coEvery { mockManager.checkGrantedPermissions() } returns true
            vm.refreshPermissions()
            advanceUntilIdle()

            assertEquals(PermissionState.Granted, vm.uiState.value.permissionState)
        }

    @Test
    fun `checkPermissions - Granted 상태에서 권한 취소 시 NotRequested 전환`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { mockManager.checkGrantedPermissions() } returns true
            val vm = createViewModel()
            advanceUntilIdle()  // checkInitialState() 완료 → Granted

            assertEquals(PermissionState.Granted, vm.uiState.value.permissionState)

            coEvery { mockManager.checkGrantedPermissions() } returns false
            vm.refreshPermissions()
            advanceUntilIdle()

            assertEquals(PermissionState.NotRequested, vm.uiState.value.permissionState)
        }

    // ===== Health Connect 미지원 환경 테스트 =====

    @Test
    fun `Health Connect 미지원 환경에서 refreshPermissions 호출 시 아무 변화 없음`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { mockManager.checkAvailability() } returns HealthConnectAvailability.NotSupported
            val vm = createViewModel()
            advanceUntilIdle()

            val stateBefore = vm.uiState.value.permissionState
            vm.refreshPermissions()
            advanceUntilIdle()

            assertEquals(stateBefore, vm.uiState.value.permissionState)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HealthMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description?) = Dispatchers.resetMain()
}
