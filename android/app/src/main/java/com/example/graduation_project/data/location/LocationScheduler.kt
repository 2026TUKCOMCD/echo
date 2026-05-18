package com.example.graduation_project.data.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import java.util.Calendar

/**
 * 위치 수집 스케줄링 관리
 *
 * AlarmManager를 사용하여:
 * - 사용자가 설정한 시간에 위치 수집 서비스 시작 (기본: 아침 6시)
 * - 대화 종료 후 다음날 자동 재시작 예약
 */
object LocationScheduler {

    private const val TAG = "LocationScheduler"
    private const val MORNING_ALARM_REQUEST_CODE = 2001

    /**
     * 사용자 설정 시간에 알람 스케줄링
     *
     * 현재 시간이 설정 시간 이후면 다음날에 예약.
     */
    fun scheduleMorningAlarm(context: Context) {
        val storage = LocationCollectionStorage(context)
        val hour = storage.getStartHour()
        val minute = storage.getStartMinute()

        scheduleAlarmAt(context, hour, minute)
    }

    /**
     * 특정 시간에 알람 스케줄링
     */
    fun scheduleAlarmAt(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, MorningAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MORNING_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 설정된 시간 계산
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 현재 시간이 설정 시간 이후면 다음날로
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // 정확한 시간에 알람 설정
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // 정확한 알람 권한 없으면 일반 알람 사용
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "정확한 알람 권한 없음 - 일반 알람 사용")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            Log.d(TAG, "위치 수집 알람 스케줄링 완료: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "알람 스케줄링 실패 - 권한 오류", e)
        }
    }

    /**
     * 아침 알람 취소
     */
    fun cancelMorningAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, MorningAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MORNING_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "아침 알람 취소됨")
    }

    /**
     * 위치 수집 전체 비활성화
     * - 서비스 중지
     * - 알람 취소
     */
    fun disableLocationCollection(context: Context) {
        LocationCollectionService.stop(context)
        cancelMorningAlarm(context)
        Log.d(TAG, "위치 수집 비활성화 완료")
    }

    /**
     * 위치 수집 활성화
     * - 시작 시간 ~ 대화 시간 범위 내이면 즉시 서비스 시작
     * - 범위 밖이면 다음날 알람만 스케줄링
     */
    fun enableLocationCollection(context: Context) {
        if (isCurrentTimeInCollectionRange(context)) {
            Log.d(TAG, "수집 시간 범위 내 - 위치 수집 즉시 시작")
            LocationCollectionService.start(context)
        } else {
            Log.d(TAG, "수집 시간 범위 밖 - 다음 알람 시간부터 수집 시작 예정")
        }

        scheduleMorningAlarm(context)
        Log.d(TAG, "위치 수집 활성화 완료")
    }

    /**
     * 현재 시간이 위치 수집 범위(시작 시간 ~ 대화 시간) 내인지 확인
     */
    fun isCurrentTimeInCollectionRange(context: Context): Boolean {
        val locationStorage = LocationCollectionStorage(context)
        val alarmStorage = ConversationAlarmStorage(context)

        val startTime = locationStorage.getStartTime()
        val conversationTime = alarmStorage.getConversationTime() ?: "21:00"

        return try {
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            val currentMinutes = currentHour * 60 + currentMinute

            val startHour = startTime.split(":")[0].toInt()
            val startMinuteVal = startTime.split(":")[1].toInt()
            val startMinutes = startHour * 60 + startMinuteVal

            val endHour = conversationTime.split(":")[0].toInt()
            val endMinuteVal = conversationTime.split(":")[1].toInt()
            val endMinutes = endHour * 60 + endMinuteVal

            val isInRange = if (startMinutes <= endMinutes) {
                // 일반적인 경우: 06:00 ~ 21:00
                currentMinutes in startMinutes..endMinutes
            } else {
                // 자정을 넘기는 경우: 22:00 ~ 06:00
                currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }

            Log.d(TAG, "시간 범위 체크: now=$currentHour:$currentMinute, range=$startTime~$conversationTime, inRange=$isInRange")
            isInRange
        } catch (e: Exception) {
            Log.e(TAG, "시간 범위 체크 실패", e)
            false
        }
    }
}
