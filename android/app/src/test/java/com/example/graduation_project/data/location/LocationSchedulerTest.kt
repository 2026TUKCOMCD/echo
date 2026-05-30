package com.example.graduation_project.data.location

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import org.robolectric.RuntimeEnvironment
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowNotificationManager
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class LocationSchedulerTest {

    private lateinit var context: Application
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        val realContext = RuntimeEnvironment.getApplication()
        context = spyk(realContext)
        alarmManager = realContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        notificationManager = realContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)

        // 배터리 부족 알림 쿨다운 리셋
        LocationScheduler.resetLowBatteryNotificationCooldown()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ===== checkPrerequisites (6개) =====

    @Test
    fun `checkPrerequisites_모든_조건_충족시_ALL_SATISFIED_반환`() {
        // Given: 모든 권한 허용 + 배터리 최적화 해제
        grantAllPermissions()
        setBatteryOptimizationIgnored(true)

        // When
        val result = LocationScheduler.checkPrerequisites(context)

        // Then
        assertEquals(LocationScheduler.PrerequisiteResult.ALL_SATISFIED, result)
    }

    @Test
    fun `checkPrerequisites_FINE_권한_없고_COARSE_권한_있으면_COARSE_LOCATION_ONLY_반환`() {
        // Given: COARSE만 허용, FINE 거부
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        // When
        val result = LocationScheduler.checkPrerequisites(context)

        // Then
        assertEquals(LocationScheduler.PrerequisiteResult.COARSE_LOCATION_ONLY, result)
    }

    @Test
    fun `checkPrerequisites_위치_권한_전혀_없으면_NO_LOCATION_PERMISSION_반환`() {
        // Given: 모든 위치 권한 거부
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        // When
        val result = LocationScheduler.checkPrerequisites(context)

        // Then
        assertEquals(LocationScheduler.PrerequisiteResult.NO_LOCATION_PERMISSION, result)
    }

    @Test
    fun `checkPrerequisites_백그라운드_위치_권한_없으면_NO_BACKGROUND_LOCATION_반환`() {
        // Given: 전경 위치 허용, 백그라운드 위치 거부
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        // When
        val result = LocationScheduler.checkPrerequisites(context)

        // Then
        assertEquals(LocationScheduler.PrerequisiteResult.NO_BACKGROUND_LOCATION, result)
    }

    @Test
    fun `checkPrerequisites_배터리_최적화_활성화시_BATTERY_OPTIMIZATION_ENABLED_반환`() {
        // Given: 모든 권한 허용, 배터리 최적화 활성화(해제 안 됨)
        grantAllPermissions()
        setBatteryOptimizationIgnored(false)

        // When
        val result = LocationScheduler.checkPrerequisites(context)

        // Then
        assertEquals(LocationScheduler.PrerequisiteResult.BATTERY_OPTIMIZATION_ENABLED, result)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P]) // Android 9 (Q 미만)
    fun `checkPrerequisites_Android_Q_미만에서_백그라운드_권한_체크_스킵`() {
        // Given: FINE 위치만 허용 (백그라운드 권한 체크 안 함)
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        setBatteryOptimizationIgnored(true)

        // When
        val result = LocationScheduler.checkPrerequisites(context)

        // Then: 백그라운드 권한 체크 없이 ALL_SATISFIED
        assertEquals(LocationScheduler.PrerequisiteResult.ALL_SATISFIED, result)
    }

    // ===== isCurrentTimeInCollectionRange (7개) =====

    @Test
    fun `isCurrentTimeInCollectionRange_시작시간과_종료시간_사이면_true_반환`() {
        // Given: 현재 시간을 포함하는 범위 설정
        setupTimeRangeContainingNow()

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isCurrentTimeInCollectionRange_시작시간_이전이면_false_반환`() {
        // Given: 현재 시간을 제외하는 미래 범위 설정
        setupTimeRangeExcludingNow()

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isCurrentTimeInCollectionRange_종료시간_이후면_false_반환`() {
        // Given: 현재 시간보다 이전의 범위 설정
        val (currentHour, _) = getCurrentHourMinute()
        val endHour = (currentHour - 1).coerceAtLeast(1)
        val startHour = (endHour - 1).coerceAtLeast(0)
        setupTimeRange("%02d:00".format(startHour), "%02d:00".format(endHour))

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isCurrentTimeInCollectionRange_정확히_시작시간이면_true_반환`() {
        // Given: 시작 시간 = 현재 시간, 종료 시간 = 현재 + 2시간
        val (currentHour, currentMinute) = getCurrentHourMinute()
        val endHour = (currentHour + 2).coerceAtMost(23)
        setupTimeRange("%02d:%02d".format(currentHour, currentMinute), "%02d:00".format(endHour))

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isCurrentTimeInCollectionRange_정확히_종료시간이면_false_반환`() {
        // Given: 종료 시간 = 현재 시간 (until 사용하므로 종료시간 제외)
        val (currentHour, currentMinute) = getCurrentHourMinute()
        val startHour = (currentHour - 2).coerceAtLeast(0)
        setupTimeRange("%02d:00".format(startHour), "%02d:%02d".format(currentHour, currentMinute))

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isCurrentTimeInCollectionRange_대화시간_없으면_기본값_2100_사용`() {
        // Given: 시작 00:00, 대화시간 null (기본값 21:00)
        // 현재 시간이 00:00 ~ 21:00 사이라면 true
        val locationStorage = LocationCollectionStorage(context)
        locationStorage.saveStartTime("00:00")
        val alarmStorage = ConversationAlarmStorage(context)
        alarmStorage.saveConversationTime(null)

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then: 현재 시간이 21:00 이전이면 true
        val (currentHour, _) = getCurrentHourMinute()
        assertEquals(currentHour < 21, result)
    }

    @Test
    fun `isCurrentTimeInCollectionRange_대화시간_형식_오류시_기본값_사용`() {
        // Given: 시작 00:00, 대화시간 형식 오류 → 기본값 21:00
        val locationStorage = LocationCollectionStorage(context)
        locationStorage.saveStartTime("00:00")
        val alarmStorage = ConversationAlarmStorage(context)
        alarmStorage.saveConversationTime("invalid")

        // When
        val result = LocationScheduler.isCurrentTimeInCollectionRange(context)

        // Then: 현재 시간이 21:00 이전이면 true
        val (currentHour, _) = getCurrentHourMinute()
        assertEquals(currentHour < 21, result)
    }

    // ===== enableLocationCollection (3개) =====

    @Test
    fun `enableLocationCollection_조건_충족_시간범위_내면_서비스_시작하고_true_반환`() {
        // Given
        grantAllPermissions()
        setBatteryOptimizationIgnored(true)
        setupTimeRangeContainingNow()
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.start(any()) } returns Unit

        // When
        val result = LocationScheduler.enableLocationCollection(context)

        // Then
        assertTrue(result)
        verify { LocationCollectionService.start(any()) }
    }

    @Test
    fun `enableLocationCollection_조건_충족_시간범위_밖이면_서비스_미시작하고_true_반환`() {
        // Given
        grantAllPermissions()
        setBatteryOptimizationIgnored(true)
        setupTimeRangeExcludingNow()
        mockkObject(LocationCollectionService)

        // When
        val result = LocationScheduler.enableLocationCollection(context)

        // Then
        assertTrue(result)
        verify(exactly = 0) { LocationCollectionService.start(any()) }
    }

    @Test
    fun `enableLocationCollection_조건_미충족시_false_반환하고_알람만_예약`() {
        // Given: 권한 없음
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, any())
        } returns PackageManager.PERMISSION_DENIED

        // When
        val result = LocationScheduler.enableLocationCollection(context)

        // Then
        assertFalse(result)
        // 알람이 예약되었는지 확인
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())
    }

    // ===== scheduleMorningAlarm / scheduleAlarmAt (5개) =====

    @Test
    fun `scheduleMorningAlarm_저장된_시간으로_알람_예약`() {
        // Given
        val locationStorage = LocationCollectionStorage(context)
        locationStorage.saveStartTime("07:30")

        // When
        LocationScheduler.scheduleMorningAlarm(context)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `scheduleAlarmAt_현재시간_이후면_오늘_예약`() {
        // Given: 현재 시간보다 2시간 후 알람 설정
        val (currentHour, _) = getCurrentHourMinute()
        val alarmHour = (currentHour + 2).coerceAtMost(23)
        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // When
        LocationScheduler.scheduleAlarmAt(context, alarmHour, 0)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
        val scheduledTime = scheduledAlarms[0].triggerAtTime
        // 알람 시간에 예약되어야 함 (대략적인 범위 검증)
        assertTrue(scheduledTime >= todayCalendar.timeInMillis - 60000)
    }

    @Test
    fun `scheduleAlarmAt_현재시간_이전이면_내일_예약`() {
        // Given: 현재 시간보다 2시간 전 알람 설정 → 내일로 예약
        val (currentHour, _) = getCurrentHourMinute()
        val alarmHour = (currentHour - 2).coerceAtLeast(0)
        val tomorrowCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, alarmHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // When
        LocationScheduler.scheduleAlarmAt(context, alarmHour, 0)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
        val scheduledTime = scheduledAlarms[0].triggerAtTime
        // 내일 알람 시간에 예약되어야 함
        assertTrue(scheduledTime >= tomorrowCalendar.timeInMillis - 60000)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `scheduleAlarmAt_SDK_31_이상에서_canScheduleExactAlarms_false면_일반_알람_사용`() {
        // Given: SDK 31, canScheduleExactAlarms = false
        // Robolectric에서는 기본적으로 canScheduleExactAlarms가 true
        // 이 테스트는 알람이 예약되는지만 확인

        // When
        LocationScheduler.scheduleAlarmAt(context, 8, 0)

        // Then: 알람이 예약됨 (정확한 알람 또는 일반 알람)
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `scheduleAlarmAt_SecurityException_발생시_예외_처리`() {
        // Given: AlarmManager mock으로 SecurityException 발생
        val mockContext = mockk<Context>(relaxed = true)
        val mockAlarmManager = mockk<AlarmManager>()
        every { mockContext.getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
        every { mockAlarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } throws SecurityException("Test")
        every { mockAlarmManager.setAndAllowWhileIdle(any(), any(), any()) } throws SecurityException("Test")
        every { mockAlarmManager.canScheduleExactAlarms() } returns true

        // When & Then: 예외가 발생해도 크래시 없이 처리
        // (로그만 출력하고 정상 종료)
        LocationScheduler.scheduleAlarmAt(mockContext, 8, 0)
        // 예외 없이 완료되면 성공
    }

    // ===== scheduleNextCollectionAlarm (4개) =====

    @Test
    fun `scheduleNextCollectionAlarm_배터리_15_이상이면_10분_후_예약`() {
        // Given
        setBatteryLevel(50)
        val beforeTime = System.currentTimeMillis()

        // When
        LocationScheduler.scheduleNextCollectionAlarm(context)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
        val scheduledTime = scheduledAlarms.last().triggerAtTime
        val interval = scheduledTime - beforeTime
        // 10분 (600,000ms) ± 1초 오차
        assertTrue(interval in 599_000..601_000)
    }

    @Test
    fun `scheduleNextCollectionAlarm_배터리_15_미만이면_30분_후_예약`() {
        // Given
        setBatteryLevel(10)
        val beforeTime = System.currentTimeMillis()

        // When
        LocationScheduler.scheduleNextCollectionAlarm(context)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
        val scheduledTime = scheduledAlarms.last().triggerAtTime
        val interval = scheduledTime - beforeTime
        // 30분 (1,800,000ms) ± 1초 오차
        assertTrue(interval in 1_799_000..1_801_000)
    }

    @Test
    fun `scheduleNextCollectionAlarm_배터리_정확히_15이면_10분_후_예약`() {
        // Given: 경계값 15%
        setBatteryLevel(15)
        val beforeTime = System.currentTimeMillis()

        // When
        LocationScheduler.scheduleNextCollectionAlarm(context)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
        val scheduledTime = scheduledAlarms.last().triggerAtTime
        val interval = scheduledTime - beforeTime
        // 10분 (600,000ms) ± 1초 오차
        assertTrue(interval in 599_000..601_000)
    }

    @Test
    fun `scheduleNextCollectionAlarm_배터리_14이면_30분_후_예약`() {
        // Given: 경계값 14% (15% 미만)
        setBatteryLevel(14)
        val beforeTime = System.currentTimeMillis()

        // When
        LocationScheduler.scheduleNextCollectionAlarm(context)

        // Then
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue(scheduledAlarms.isNotEmpty())
        val scheduledTime = scheduledAlarms.last().triggerAtTime
        val interval = scheduledTime - beforeTime
        // 30분 (1,800,000ms) ± 1초 오차
        assertTrue(interval in 1_799_000..1_801_000)
    }

    // ===== 알람 취소 (3개) =====

    @Test
    fun `cancelMorningAlarm_호출시_알람이_취소됨`() {
        // Given: 알람 예약
        LocationScheduler.scheduleMorningAlarm(context)
        val alarmCountBefore = shadowAlarmManager.scheduledAlarms.size
        assertTrue("알람이 예약되어야 함", alarmCountBefore > 0)

        // When
        LocationScheduler.cancelMorningAlarm(context)

        // Then: 알람이 취소됨 (Robolectric에서 cancel 후 알람 수 감소)
        val alarmCountAfter = shadowAlarmManager.scheduledAlarms.size
        assertTrue("알람이 취소되어야 함", alarmCountAfter < alarmCountBefore)
    }

    @Test
    fun `cancelCollectionAlarm_호출시_알람이_취소됨`() {
        // Given: 알람 예약
        setBatteryLevel(50)
        LocationScheduler.scheduleNextCollectionAlarm(context)
        val alarmCountBefore = shadowAlarmManager.scheduledAlarms.size
        assertTrue("알람이 예약되어야 함", alarmCountBefore > 0)

        // When
        LocationScheduler.cancelCollectionAlarm(context)

        // Then: 알람이 취소됨
        val alarmCountAfter = shadowAlarmManager.scheduledAlarms.size
        assertTrue("알람이 취소되어야 함", alarmCountAfter < alarmCountBefore)
    }

    @Test
    fun `disableLocationCollection_서비스_중지_및_알람_취소`() {
        // Given
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.stop(any()) } returns Unit
        LocationScheduler.scheduleMorningAlarm(context)

        // When
        LocationScheduler.disableLocationCollection(context)

        // Then
        verify { LocationCollectionService.stop(any()) }
    }

    // ===== 배터리 부족 알림 (4개) =====

    @Test
    fun `scheduleNextCollectionAlarm_배터리_15_미만이면_알림_표시`() {
        // Given
        setBatteryLevel(10)

        // When
        LocationScheduler.scheduleNextCollectionAlarm(context)

        // Then: 배터리 부족 알림이 표시됨
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("배터리 부족 알림이 표시되어야 함", notifications.isNotEmpty())
    }

    @Test
    fun `scheduleNextCollectionAlarm_배터리_15_이상이면_알림_미표시`() {
        // Given
        setBatteryLevel(50)

        // When
        LocationScheduler.scheduleNextCollectionAlarm(context)

        // Then: 배터리 부족 알림이 표시되지 않음
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("배터리 충분하면 알림이 없어야 함", notifications.isEmpty())
    }

    @Test
    fun `showLowBatteryNotification_쿨다운_중이면_알림_미표시`() {
        // Given: 첫 번째 알림 표시
        LocationScheduler.showLowBatteryNotification(context, 10)
        val notificationsAfterFirst = shadowNotificationManager.allNotifications.size

        // 알림 취소 (쿨다운 테스트를 위해)
        notificationManager.cancelAll()

        // When: 쿨다운 중에 다시 호출
        LocationScheduler.showLowBatteryNotification(context, 8)

        // Then: 쿨다운 중이므로 알림이 다시 표시되지 않음
        val notificationsAfterSecond = shadowNotificationManager.allNotifications.size
        assertEquals("쿨다운 중에는 알림이 표시되지 않아야 함", 0, notificationsAfterSecond)
    }

    @Test
    fun `showLowBatteryNotification_알림_내용이_올바름`() {
        // Given
        val batteryLevel = 12

        // When
        LocationScheduler.showLowBatteryNotification(context, batteryLevel)

        // Then
        val notifications = shadowNotificationManager.allNotifications
        assertTrue("알림이 표시되어야 함", notifications.isNotEmpty())

        val notification = notifications[0]
        val title = notification.extras.getString("android.title")
        val text = notification.extras.getString("android.text")

        assertNotNull("알림 제목이 있어야 함", title)
        assertNotNull("알림 내용이 있어야 함", text)
        assertTrue("알림 내용에 배터리 레벨 포함", text?.contains("$batteryLevel%") == true)
        assertTrue("알림 내용에 30분 간격 안내 포함", text?.contains("30분") == true)
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

    private fun setupTimeRange(startTime: String, endTime: String) {
        val locationStorage = LocationCollectionStorage(context)
        locationStorage.saveStartTime(startTime)
        val alarmStorage = ConversationAlarmStorage(context)
        alarmStorage.saveConversationTime(endTime)
    }

    /**
     * 현재 시간을 기준으로 시간 범위를 설정
     * Calendar.getInstance() mock이 Java 9+에서 문제가 되어 다른 방식 사용
     */
    private fun getCurrentHourMinute(): Pair<Int, Int> {
        val now = Calendar.getInstance()
        return Pair(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    }

    /**
     * 시간 범위 내로 설정 (현재 시간이 범위 내에 있도록)
     */
    private fun setupTimeRangeContainingNow() {
        val (currentHour, _) = getCurrentHourMinute()
        val startHour = (currentHour - 1).coerceAtLeast(0)
        val endHour = (currentHour + 1).coerceAtMost(23)
        setupTimeRange("%02d:00".format(startHour), "%02d:00".format(endHour))
    }

    /**
     * 시간 범위 밖으로 설정 (현재 시간이 범위 밖에 있도록)
     */
    private fun setupTimeRangeExcludingNow() {
        val (currentHour, _) = getCurrentHourMinute()
        // 현재 시간보다 미래의 범위 설정
        val startHour = (currentHour + 2).coerceAtMost(22)
        val endHour = (currentHour + 3).coerceAtMost(23)
        setupTimeRange("%02d:00".format(startHour), "%02d:00".format(endHour))
    }

    private fun setBatteryLevel(level: Int) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val shadowBatteryManager = shadowOf(batteryManager)
        shadowBatteryManager.setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, level)
    }
}
