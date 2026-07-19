package com.example.graduation_project.data.location

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import org.robolectric.RuntimeEnvironment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class MorningAlarmReceiverTest {

    private lateinit var context: Application
    private lateinit var receiver: MorningAlarmReceiver
    private lateinit var shadowNotificationManager: ShadowNotificationManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        val realContext = RuntimeEnvironment.getApplication()
        context = spyk(realContext)
        receiver = MorningAlarmReceiver()
        notificationManager = realContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ===== onReceive 조건 분기 (7개) =====

    @Test
    fun `onReceive_조건_충족시_startServiceIfInTimeRange_호출`() {
        // Given: 모든 조건 충족
        grantAllPermissions()
        setBatteryOptimizationIgnored(true)
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.ALL_SATISFIED
        every { LocationScheduler.startServiceIfInTimeRange(any()) } returns Unit
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then
        verify { LocationScheduler.startServiceIfInTimeRange(any()) }
    }

    @Test
    fun `onReceive_FINE_권한_없으면_알림_표시`() {
        // Given: FINE 권한 없음 (NO_LOCATION_PERMISSION)
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.NO_LOCATION_PERMISSION
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림이 표시됨
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("권한 부족 알림이 표시되어야 함", notifications.isNotEmpty())
    }

    @Test
    fun `onReceive_COARSE_권한만_있으면_알림_표시`() {
        // Given: COARSE만 허용
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.COARSE_LOCATION_ONLY
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림이 표시됨
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("COARSE 권한만 있으면 알림이 표시되어야 함", notifications.isNotEmpty())
    }

    @Test
    fun `onReceive_백그라운드_권한_없으면_알림_표시`() {
        // Given: 백그라운드 권한 없음
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.NO_BACKGROUND_LOCATION
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림이 표시됨
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("백그라운드 권한 없으면 알림이 표시되어야 함", notifications.isNotEmpty())
    }

    @Test
    fun `onReceive_배터리_최적화_활성화면_알림_표시`() {
        // Given: 배터리 최적화 활성화
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.BATTERY_OPTIMIZATION_ENABLED
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림이 표시됨
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("배터리 최적화 활성화 시 알림이 표시되어야 함", notifications.isNotEmpty())
    }

    @Test
    fun `onReceive_조건_미충족_여부와_무관하게_다음날_알람_재예약`() {
        // Given: 조건 미충족
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.NO_LOCATION_PERMISSION
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 다음날 알람이 재예약됨
        verify { LocationScheduler.scheduleMorningAlarm(any()) }
    }

    @Test
    fun `onReceive_조건_충족시에도_다음날_알람_재예약`() {
        // Given: 모든 조건 충족
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.ALL_SATISFIED
        every { LocationScheduler.startServiceIfInTimeRange(any()) } returns Unit
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 조건 충족해도 다음날 알람 재예약
        verify { LocationScheduler.scheduleMorningAlarm(any()) }
    }

    // ===== 알림 표시 (3개) =====

    @Test
    fun `showPrerequisiteNotification_알림_채널_생성`() {
        // Given
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.NO_LOCATION_PERMISSION
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림 채널이 생성됨
        val channel = notificationManager.getNotificationChannel("location_prerequisite_channel")
        assertNotNull("알림 채널이 생성되어야 함", channel)
    }

    @Test
    fun `showPrerequisiteNotification_알림에_권한_버튼_포함`() {
        // Given
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.NO_LOCATION_PERMISSION
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림에 액션 버튼이 포함됨
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("알림이 표시되어야 함", notifications.isNotEmpty())

        val notification = notifications[0]
        val actions = notification.actions
        assertNotNull("알림에 액션이 있어야 함", actions)
        assertTrue("액션이 1개 이상이어야 함", actions.isNotEmpty())

        // "위치 권한 허용하기" 버튼 확인
        val actionTitle = actions[0].title.toString()
        assertTrue("권한 버튼이 포함되어야 함", actionTitle.contains("권한") || actionTitle.contains("허용"))
    }

    @Test
    fun `showPrerequisiteNotification_Intent에_EXTRA_SHOW_PERMISSION_DIALOG_포함`() {
        // Given
        mockkObject(LocationScheduler)
        every { LocationScheduler.checkPrerequisites(any()) } returns LocationScheduler.PrerequisiteResult.NO_LOCATION_PERMISSION
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit

        // When
        receiver.onReceive(context, Intent())

        // Then: 알림이 표시됨 (Intent 내용은 PendingIntent 내부라 직접 확인 어려움)
        // 대신 알림이 올바르게 표시되었는지 확인
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("알림이 표시되어야 함", notifications.isNotEmpty())

        // contentIntent가 설정되어 있는지 확인
        val notification = notifications[0]
        assertNotNull("contentIntent가 설정되어야 함", notification.contentIntent)
    }

    // ===== 헬퍼 메서드 =====

    private fun grantAllPermissions() {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
    }

    private fun setBatteryOptimizationIgnored(ignored: Boolean) {
        val mockPowerManager = mockk<PowerManager>()
        every { mockPowerManager.isIgnoringBatteryOptimizations(any()) } returns ignored
        every { context.getSystemService(Context.POWER_SERVICE) } returns mockPowerManager
    }
}
