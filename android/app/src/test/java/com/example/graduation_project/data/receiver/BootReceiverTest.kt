package com.example.graduation_project.data.receiver

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.data.location.LocationScheduler.PrerequisiteResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 재부팅·앱 업데이트 뒤 복구 - 로그아웃할 때 끈 위치 수집이 로그인 없이 다시 켜지면 안 된다.
 * (로그아웃해도 위치 권한은 남아 있어, 권한만 보면 다시 켜짐)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class BootReceiverTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val receiver = BootReceiver()
    private val defaultIsLoggedIn = LocationScheduler.isLoggedIn

    @Before
    fun setUp() {
        mockkObject(LocationScheduler)
        every { LocationScheduler.enableLocationCollection(any()) } returns true
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit
        mockkObject(ConversationAlarmScheduler)
        every { ConversationAlarmScheduler.rescheduleFromStorage(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        LocationScheduler.isLoggedIn = defaultIsLoggedIn
    }

    @Test
    fun `로그아웃 상태로 재부팅하면 위치 수집과 아침 알람을 다시 켜지 않는다`() {
        every { LocationScheduler.checkPrerequisites(any()) } returns PrerequisiteResult.ALL_SATISFIED
        LocationScheduler.isLoggedIn = { false }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(exactly = 0) { LocationScheduler.enableLocationCollection(any()) }
        verify(exactly = 0) { LocationScheduler.scheduleMorningAlarm(any()) }
        // 대화 알람 복구는 그대로 (로그아웃할 때 저장값을 지워 되살릴 알람이 없음)
        verify { ConversationAlarmScheduler.rescheduleFromStorage(any()) }
    }

    @Test
    fun `앱 업데이트 뒤에도 로그아웃 상태면 위치 수집을 다시 켜지 않는다`() {
        every { LocationScheduler.checkPrerequisites(any()) } returns PrerequisiteResult.ALL_SATISFIED
        LocationScheduler.isLoggedIn = { false }

        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        verify(exactly = 0) { LocationScheduler.enableLocationCollection(any()) }
        verify(exactly = 0) { LocationScheduler.scheduleMorningAlarm(any()) }
    }

    @Test
    fun `로그인 상태로 재부팅하면 조건이 맞을 때 위치 수집을 다시 켠다`() {
        every { LocationScheduler.checkPrerequisites(any()) } returns PrerequisiteResult.ALL_SATISFIED
        LocationScheduler.isLoggedIn = { true }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify { LocationScheduler.enableLocationCollection(any()) }
    }

    @Test
    fun `로그인 상태로 재부팅했지만 권한이 부족하면 아침 알람만 예약한다`() {
        every { LocationScheduler.checkPrerequisites(any()) } returns PrerequisiteResult.NO_BACKGROUND_LOCATION
        LocationScheduler.isLoggedIn = { true }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(exactly = 0) { LocationScheduler.enableLocationCollection(any()) }
        verify { LocationScheduler.scheduleMorningAlarm(any()) }
    }
}
