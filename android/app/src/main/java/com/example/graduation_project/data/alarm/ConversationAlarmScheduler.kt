package com.example.graduation_project.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * 대화 알람 스케줄링 관리
 *
 * AlarmManager를 사용하여 매일 설정된 시간에 알람 발송
 */
object ConversationAlarmScheduler {

    private const val TAG = "ConversationAlarmScheduler"
    const val ALARM_REQUEST_CODE = 3001

    /**
     * 알람 스케줄링
     *
     * @param context Context
     * @param time "HH:mm" 형식의 시간
     */
    fun scheduleAlarm(context: Context, time: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val parts = time.split(":")
        if (parts.size != 2) {
            Log.e(TAG, "잘못된 시간 형식: $time")
            return
        }

        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val intent = Intent(context, ConversationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 다음 알람 시간 계산
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

            Log.d(TAG, "대화 알람 스케줄링 완료: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "알람 스케줄링 실패 - 권한 오류", e)
        }
    }

    /**
     * 알람 취소
     */
    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ConversationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "대화 알람 취소됨")
    }

    /**
     * 저장된 설정으로 알람 재스케줄링 (부팅 시 사용)
     */
    fun rescheduleFromStorage(context: Context) {
        val storage = ConversationAlarmStorage(context)

        if (!storage.isAlarmEnabled()) {
            Log.d(TAG, "알람 비활성화 상태 - 재스케줄링 건너뜀")
            return
        }

        val time = storage.getConversationTime()
        if (time == null) {
            Log.d(TAG, "저장된 대화 시간 없음 - 재스케줄링 건너뜀")
            return
        }

        scheduleAlarm(context, time)
        Log.d(TAG, "부팅 후 대화 알람 재스케줄링 완료: $time")
    }
}
