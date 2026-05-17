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
     * - 대화 시간 전이면 즉시 서비스 시작 (권한 허용 직후 바로 수집 시작)
     * - 대화 시간 이후면 다음날 알람만 스케줄링
     */
    fun enableLocationCollection(context: Context) {
        val alarmStorage = ConversationAlarmStorage(context)

        // 대화 시간 가져오기 (기본값: 21:00)
        val conversationTime = alarmStorage.getConversationTime() ?: "21:00"
        val endHour = try { conversationTime.split(":")[0].toInt() } catch (e: Exception) { 9 }
        val endMinute = try { conversationTime.split(":")[1].toInt() } catch (e: Exception) { 0 }

        // 현재 시간 확인
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentMinutes = currentHour * 60 + currentMinute
        val endMinutes = endHour * 60 + endMinute

        // 대화 시간 전인지 확인 (대화 시간 전이면 아직 오늘 대화가 안 끝났으므로 수집 필요)
        val isBeforeConversationTime = currentMinutes < endMinutes

        if (isBeforeConversationTime) {
            // 대화 시간 전 → 즉시 서비스 시작 (권한 허용 직후 바로 수집)
            Log.d(TAG, "대화 시간($endHour:$endMinute) 전 - 위치 수집 즉시 시작")
            LocationCollectionService.start(context)
        } else {
            // 대화 시간 이후 → 오늘 대화는 이미 끝났으므로 내일부터 수집
            Log.d(TAG, "대화 시간($endHour:$endMinute) 이후 - 내일 아침부터 수집 시작 예정")
        }

        scheduleMorningAlarm(context)
        Log.d(TAG, "위치 수집 활성화 완료")
    }
}
