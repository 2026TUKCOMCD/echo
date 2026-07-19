package com.example.graduation_project.data.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class ConversationAlarmSchedulerTest {

    private lateinit var context: Application
    private lateinit var shadowAlarmManager: ShadowAlarmManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
    }

    @Test
    fun `cancelAndClear_알람취소_및_저장소를_모두_정리한다`() {
        // Given: 알람 설정 저장 + 알람 예약 (로그인 상태 재현)
        val storage = ConversationAlarmStorage(context)
        storage.saveConversationTime("21:00")
        ConversationAlarmScheduler.scheduleAlarm(context, "21:00")
        assertTrue("알람이 예약되어야 함", shadowAlarmManager.scheduledAlarms.isNotEmpty())

        // When: 로그아웃 정리
        ConversationAlarmScheduler.cancelAndClear(context)

        // Then: 알람 취소 + 저장소 클리어 (로그아웃한 사용자에게 알람이 울리지 않아야 함)
        assertTrue("알람이 취소되어야 함", shadowAlarmManager.scheduledAlarms.isEmpty())
        assertNull("대화 시간이 삭제되어야 함", ConversationAlarmStorage(context).getConversationTime())
    }
}
