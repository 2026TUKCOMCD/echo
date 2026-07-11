package com.example.graduation_project.presentation.settings

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationCollectionStorage
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.repository.UserRepository
import com.example.graduation_project.presentation.permission.PermissionChecker
import com.example.graduation_project.util.DeviceUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        mockkObject(ConversationAlarmScheduler)

        // 기본값 설정
        every { PermissionChecker.hasForegroundLocationPermission(any()) } returns false
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns false
        every { PermissionChecker.hasNotificationPermission(any()) } returns true
        every { PermissionChecker.hasExactAlarmPermission(any()) } returns true
        every { LocationCollectionService.isRunning } returns false
        every { LocationScheduler.enableLocationCollection(any()) } returns true
        every { ConversationAlarmScheduler.scheduleAlarm(any(), any()) } just Runs
        every { ConversationAlarmScheduler.cancelAlarm(any()) } just Runs
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

    // ===== 대화 시간 저장 (isSaving 복귀) =====

    @Test
    fun `updateConversationTime_위치수집_자동시작_경로에서도_isSaving이_false로_복귀한다`() = runTest {
        // Given: 배경 위치 권한 있음 + 서비스 미실행 + 현재 시간이 수집 범위(00:00~23:59) 내
        every { PermissionChecker.hasBackgroundLocationPermission(any()) } returns true
        every { LocationCollectionService.start(any()) } just Runs
        LocationCollectionStorage(context).saveStartTime("00:00")
        coEvery { mockUserRepository.updateConversationTime(any()) } returns
            ApiResult.Success(UserPreferences(conversationTime = "23:59"))

        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 대화 시간 변경 (자동 시작 분기로 진입)
        viewModel.updateConversationTime("23:59")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 자동 시작 경로에서도 isSaving이 해제되고 응답 값이 UI에 반영되어야 함
        // (기존 버그: early return으로 isSaving=true가 남아 설정 화면 전체가 잠김)
        assertFalse("isSaving이 해제되어야 함", viewModel.uiState.value.isSaving)
        assertEquals("23:59", viewModel.uiState.value.conversationTime)
        assertTrue(viewModel.uiState.value.isLocationCollectionRunning)
    }

    // ===== 서버-로컬 알람 시간 동기화 =====

    @Test
    fun `loadSettings가_서버_대화시간과_로컬저장소가_다르면_동기화하고_재예약한다`() = runTest {
        // Given: 로컬에는 20:00으로 저장 + 알람 활성화, 서버는 21:30 반환 (다른 기기에서 변경한 상황)
        val alarmStorage = ConversationAlarmStorage(context)
        alarmStorage.saveConversationTime("20:00")
        alarmStorage.setAlarmEnabled(true)
        coEvery { mockUserRepository.getPreferences() } returns
            ApiResult.Success(UserPreferences(conversationTime = "21:30"))

        // When: ViewModel 생성 (init에서 loadSettings 호출)
        SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 로컬 저장소가 서버 값으로 동기화되고 알람이 재예약되어야 함
        assertEquals("21:30", alarmStorage.getConversationTime())
        verify { ConversationAlarmScheduler.scheduleAlarm(any(), "21:30") }
    }

    @Test
    fun `loadSettings가_서버_대화시간과_로컬저장소가_같아도_알람을_재예약한다`() = runTest {
        // Given: 로컬과 서버 모두 21:30 (강제 종료 등으로 AlarmManager 등록만 사라진 상황 가정)
        val alarmStorage = ConversationAlarmStorage(context)
        alarmStorage.saveConversationTime("21:30")
        alarmStorage.setAlarmEnabled(true)
        coEvery { mockUserRepository.getPreferences() } returns
            ApiResult.Success(UserPreferences(conversationTime = "21:30"))

        // When: ViewModel 생성 (init에서 loadSettings 호출)
        SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 시간이 같아도 설정 화면 진입만으로 끊긴 알람이 복구되어야 함
        verify { ConversationAlarmScheduler.scheduleAlarm(any(), "21:30") }
    }

    // ===== 알람 켜기 기본시간 서버 저장 =====

    @Test
    fun `알람켜기_기본시간_서버저장_실패시_롤백하고_오류를_표시한다`() = runTest {
        // Given: 대화 시간 미설정 + 서버 저장 실패 (오프라인 등)
        coEvery { mockUserRepository.updateConversationTime(any()) } returns
            ApiResult.Error(ApiException.NetworkError())

        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 알람 토글 ON
        viewModel.setAlarmEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 아무것도 켜지지 않고 오류 안내 (서버-로컬-UI 불일치로 인한 유령 알람 방지)
        assertFalse(viewModel.uiState.value.alarmEnabled)
        assertFalse(ConversationAlarmStorage(context).isAlarmEnabled())
        assertTrue(viewModel.uiState.value.errorMessage != null)
        verify(exactly = 0) { ConversationAlarmScheduler.scheduleAlarm(any(), any()) }
    }

    @Test
    fun `알람켜기_기본시간_서버저장_성공시_로컬저장_및_알람예약한다`() = runTest {
        // Given: 대화 시간 미설정 + 서버 저장 성공
        coEvery { mockUserRepository.updateConversationTime(any()) } returns
            ApiResult.Success(UserPreferences(conversationTime = "21:00"))

        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 알람 토글 ON
        viewModel.setAlarmEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 기본 시간(21:00)으로 로컬 저장 + 알람 예약
        assertTrue(viewModel.uiState.value.alarmEnabled)
        assertEquals("21:00", ConversationAlarmStorage(context).getConversationTime())
        assertTrue(ConversationAlarmStorage(context).isAlarmEnabled())
        verify { ConversationAlarmScheduler.scheduleAlarm(any(), "21:00") }
    }

    // ===== checkAndUpdateLocationCollectionStatus 자동 시작 (1개) =====

    @Test
    fun `checkAndUpdateLocationCollectionStatus가_범위내이고_미실행중이면_enableLocationCollection_호출`() {
        // Given: 대화 시간이 설정되어 있고(23:59), 수집 시작 시간(00:00)~대화 시간 사이가 사실상 하루 전체를
        // 덮도록 해서 실제 현재 시각과 무관하게 "범위 내"가 되도록 함. 서비스는 미실행 상태(기본 스텁).
        LocationCollectionStorage(context).saveStartTime("00:00")
        coEvery { mockUserRepository.getPreferences() } returns
            ApiResult.Success(UserPreferences(conversationTime = "23:59"))

        // When: ViewModel 생성 (init에서 loadSettings() → checkAndUpdateLocationCollectionStatus() 호출)
        SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 범위 내인데 서비스가 실행 중이 아니므로 자동 시작 시도
        verify { LocationScheduler.enableLocationCollection(any()) }
    }

    // ===== 정확한 알람 권한 (2개) =====

    @Test
    fun `loadSettings이_정확한_알람_권한_상태를_반영한다`() = runTest {
        // Given: 정확한 알람 권한 없음
        every { PermissionChecker.hasExactAlarmPermission(any()) } returns false

        // When: ViewModel 생성 (init에서 loadSettings 호출)
        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 정확한 알람 권한 없음 상태가 반영됨
        assertFalse(
            "정확한 알람 권한이 없으면 hasExactAlarmPermission이 false여야 함",
            viewModel.uiState.value.hasExactAlarmPermission
        )
    }

    @Test
    fun `refreshPermissionStatus가_정확한_알람_권한_최신_상태를_반영한다`() = runTest {
        // Given: 정확한 알람 권한 있음 상태로 시작
        every { PermissionChecker.hasExactAlarmPermission(any()) } returns true
        val viewModel = SettingsViewModel(context, mockUserRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 정확한 알람 권한이 해제된 뒤 재확인
        every { PermissionChecker.hasExactAlarmPermission(any()) } returns false
        viewModel.refreshPermissionStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 최신 상태(false)가 반영됨
        assertFalse(
            "정확한 알람 권한이 해제되면 refreshPermissionStatus 후 false로 갱신되어야 함",
            viewModel.uiState.value.hasExactAlarmPermission
        )
    }

    // ===== 헬퍼 메서드 =====

    private fun setBatteryOptimizationIgnored(ignored: Boolean) {
        shadowPowerManager.setIgnoringBatteryOptimizations(context.packageName, ignored)
    }
}
