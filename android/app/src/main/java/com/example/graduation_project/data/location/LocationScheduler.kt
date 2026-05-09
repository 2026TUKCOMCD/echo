package com.example.graduation_project.data.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * 위치 수집 스케줄링 관리
 *
 * AlarmManager를 사용하여:
 * - 매일 아침 6시 위치 수집 서비스 시작
 * - 대화 종료 후 다음날 자동 재시작 예약
 */
object LocationScheduler {

    private const val TAG = "LocationScheduler"
    private const val MORNING_ALARM_REQUEST_CODE = 2001
    private const val MORNING_HOUR = 6
    private const val MORNING_MINUTE = 0

    /**
     * 매일 아침 6시 알람 스케줄링
     *
     * 현재 시간이 6시 이후면 다음날 6시에 예약.
     */
    fun scheduleMorningAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, MorningAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MORNING_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 다음 아침 6시 계산
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, MORNING_HOUR)
            set(Calendar.MINUTE, MORNING_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 현재 시간이 6시 이후면 다음날로
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

            Log.d(TAG, "아침 6시 알람 스케줄링 완료: ${calendar.time}")
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
     * - 현재 활동 시간대면 서비스 즉시 시작
     * - 아침 알람 스케줄링
     */
    fun enableLocationCollection(context: Context) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (hour in MORNING_HOUR..23) {
            LocationCollectionService.start(context)
        }

        scheduleMorningAlarm(context)
        Log.d(TAG, "위치 수집 활성화 완료")
    }
}
