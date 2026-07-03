package com.example.graduation_project.data.alarm

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationScheduler
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class ConversationAlarmReceiverTest {

    private lateinit var context: Application
    private lateinit var receiver: ConversationAlarmReceiver

    @Before
    fun setUp() {
        val realContext = RuntimeEnvironment.getApplication()
        context = spyk(realContext)
        receiver = ConversationAlarmReceiver()

        mockkObject(LocationCollectionService)
        every { LocationCollectionService.stop(any()) } returns Unit
        mockkObject(LocationScheduler)
        every { LocationScheduler.scheduleMorningAlarm(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onReceive_위치권한_있으면_서비스종료_및_다음날알람_재예약`() {
        // Given: 위치 권한 있음
        grantLocationPermission()

        // When
        receiver.onReceive(context, Intent())

        // Then
        verify { LocationCollectionService.stop(any()) }
        verify { LocationScheduler.scheduleMorningAlarm(any()) }
    }

    @Test
    fun `onReceive_위치권한_없어도_다음날알람_재예약됨`() {
        // Given: 위치 권한 없음 (사용자가 낮에 권한을 낮춘 경우를 재현)
        denyLocationPermission()

        // When
        receiver.onReceive(context, Intent())

        // Then: 권한이 없어도 서비스 종료 시도 + 다음날 알람은 반드시 재예약되어야 함
        verify { LocationCollectionService.stop(any()) }
        verify { LocationScheduler.scheduleMorningAlarm(any()) }
    }

    private fun grantLocationPermission() {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
    }

    private fun denyLocationPermission() {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
    }
}
