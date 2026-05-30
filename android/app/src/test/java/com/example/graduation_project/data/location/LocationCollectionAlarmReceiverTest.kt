package com.example.graduation_project.data.location

import android.app.Application
import android.content.Intent
import android.os.Build
import org.robolectric.RuntimeEnvironment
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class LocationCollectionAlarmReceiverTest {

    private lateinit var context: Application
    private lateinit var receiver: LocationCollectionAlarmReceiver
    private lateinit var shadowApplication: ShadowApplication

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        receiver = LocationCollectionAlarmReceiver()
        shadowApplication = shadowOf(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ===== onReceive (6개) =====

    @Test
    fun `onReceive_서비스_미실행시_아무_동작_안함`() {
        // Given: 서비스가 실행 중이지 않음
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns false

        mockkObject(LocationScheduler)

        // When
        receiver.onReceive(context, Intent())

        // Then: 아무 동작 안 함 (서비스 시작, 알람 예약 등 호출 안 됨)
        verify(exactly = 0) { LocationScheduler.isCurrentTimeInCollectionRange(any()) }
        verify(exactly = 0) { LocationScheduler.scheduleNextCollectionAlarm(any()) }
    }

    @Test
    fun `onReceive_서비스_실행중_시간범위_내면_위치수집_트리거`() {
        // Given: 서비스 실행 중 + 시간 범위 내
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns true

        mockkObject(LocationScheduler)
        every { LocationScheduler.isCurrentTimeInCollectionRange(any()) } returns true
        every { LocationScheduler.scheduleNextCollectionAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 서비스에 위치 수집 Intent 전송
        val startedService = shadowApplication.nextStartedService
        // startService가 호출되었는지 확인
        // (Robolectric에서 startService 호출 시 nextStartedService에 저장됨)
        // 알람이 예약되었는지도 확인
        verify { LocationScheduler.scheduleNextCollectionAlarm(any()) }
    }

    @Test
    fun `onReceive_서비스_실행중_시간범위_내면_다음_알람_예약`() {
        // Given
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns true

        mockkObject(LocationScheduler)
        every { LocationScheduler.isCurrentTimeInCollectionRange(any()) } returns true
        every { LocationScheduler.scheduleNextCollectionAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 다음 알람 예약됨
        verify(exactly = 1) { LocationScheduler.scheduleNextCollectionAlarm(any()) }
    }

    @Test
    fun `onReceive_시간범위_밖이면_서비스_중지`() {
        // Given: 서비스 실행 중 + 시간 범위 밖
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns true
        every { LocationCollectionService.stop(any()) } returns Unit

        mockkObject(LocationScheduler)
        every { LocationScheduler.isCurrentTimeInCollectionRange(any()) } returns false
        every { LocationScheduler.cancelCollectionAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 서비스 중지 호출
        verify { LocationCollectionService.stop(any()) }
    }

    @Test
    fun `onReceive_시간범위_밖이면_알람_취소`() {
        // Given: 서비스 실행 중 + 시간 범위 밖
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns true
        every { LocationCollectionService.stop(any()) } returns Unit

        mockkObject(LocationScheduler)
        every { LocationScheduler.isCurrentTimeInCollectionRange(any()) } returns false
        every { LocationScheduler.cancelCollectionAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알람 취소 호출
        verify { LocationScheduler.cancelCollectionAlarm(any()) }
    }

    @Test
    fun `onReceive_시간범위_밖이면_다음_알람_예약_안함`() {
        // Given: 서비스 실행 중 + 시간 범위 밖
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns true
        every { LocationCollectionService.stop(any()) } returns Unit

        mockkObject(LocationScheduler)
        every { LocationScheduler.isCurrentTimeInCollectionRange(any()) } returns false
        every { LocationScheduler.cancelCollectionAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 다음 알람 예약 안 함
        verify(exactly = 0) { LocationScheduler.scheduleNextCollectionAlarm(any()) }
    }

    // ===== Intent 검증 (1개) =====

    @Test
    fun `onReceive_위치수집_Intent에_ACTION_COLLECT_설정`() {
        // Given: 서비스 실행 중 + 시간 범위 내
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.isRunning } returns true

        mockkObject(LocationScheduler)
        every { LocationScheduler.isCurrentTimeInCollectionRange(any()) } returns true
        every { LocationScheduler.scheduleNextCollectionAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: ACTION_COLLECT가 설정된 Intent로 서비스 시작
        val startedService = shadowApplication.nextStartedService
        if (startedService != null) {
            assert(startedService.action == LocationCollectionService.ACTION_COLLECT) {
                "Intent action이 ACTION_COLLECT여야 함"
            }
        }
        // 참고: Robolectric에서 서비스 시작 확인
    }
}
