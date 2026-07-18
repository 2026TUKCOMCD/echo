package com.example.graduation_project.presentation.home

import android.app.Application
import android.os.Build
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.location.LocationManager
import com.example.graduation_project.data.location.LocationStorageManager
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.repository.UserRepository
import com.example.graduation_project.data.repository.WeatherRepository
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 다른 기기에서 로그인했을 때(로컬 알람 저장소가 비어있는 상태) 홈 화면 진입만으로도
 * 대화 알람이 서버 시간 기준으로 동기화되는지 검증.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class HomeViewModelTest {

    private lateinit var context: Application
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockWeatherRepository: WeatherRepository
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockLocationStorageManager: LocationStorageManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Dispatchers.setMain(testDispatcher)

        mockUserRepository = mockk()
        mockWeatherRepository = mockk()
        mockLocationManager = mockk()
        mockLocationStorageManager = mockk()

        coEvery { mockLocationStorageManager.getTodayLocations() } returns emptyList()
        coEvery { mockLocationManager.getCurrentLocation() } returns null

        // Robolectric JVM에는 AndroidKeyStore가 없어 실제 AppDatabase(SQLCipher 비밀번호 생성)를
        // 만들 수 없음 → 싱글톤을 목으로 대체해 Keystore 접근 차단 (SettingsViewModelTest와 동일 패턴)
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns mockk(relaxed = true)

        mockkObject(ConversationAlarmScheduler)
        every { ConversationAlarmScheduler.scheduleAlarm(any(), any()) } just Runs
        every { ConversationAlarmScheduler.cancelAlarm(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        application = context,
        userRepository = mockUserRepository,
        weatherRepository = mockWeatherRepository,
        locationManager = mockLocationManager,
        locationStorageManager = mockLocationStorageManager
    )

    @Test
    fun `홈_진입시_서버_대화시간으로_로컬_알람을_동기화한다`() = runTest {
        // Given: 다른 기기에서 로그인한 상황 가정 - 로컬 알람 저장소는 비어있고 서버에만 대화 시간이 있음
        coEvery { mockUserRepository.getPreferences() } returns
            ApiResult.Success(UserPreferences(conversationTime = "21:30"))

        // When: 홈 화면 진입 (ViewModel 생성)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 로컬 저장소에도 반영되고 알람이 예약되어야 함 (설정 화면을 열지 않아도)
        assertEquals("21:30", ConversationAlarmStorage(context).getConversationTime())
        verify { ConversationAlarmScheduler.scheduleAlarm(any(), "21:30") }
    }

    @Test
    fun `서버_대화시간이_없으면_로컬_알람을_취소한다`() = runTest {
        // Given: 서버에 대화 시간이 설정되어 있지 않음
        coEvery { mockUserRepository.getPreferences() } returns
            ApiResult.Success(UserPreferences(conversationTime = null))

        // When: 홈 화면 진입
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 로컬에 남아있을 수 있는 알람을 취소해 서버 상태와 어긋나지 않게 함
        verify { ConversationAlarmScheduler.cancelAlarm(any()) }
    }
}
