package com.example.graduation_project.data.location

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
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
     * - 필수 조건(FINE 권한, 백그라운드 권한, 배터리 최적화 해제) 확인
     * - 위치 수집 시간 범위 내이면 즉시 서비스 시작
     * - 범위 밖이면 다음날 알람만 스케줄링
     */
    fun enableLocationCollection(context: Context): Boolean {
        // 필수 조건 확인
        val prerequisiteResult = checkPrerequisites(context)
        if (prerequisiteResult != PrerequisiteResult.ALL_SATISFIED) {
            Log.w(TAG, "위치 수집 필수 조건 미충족: $prerequisiteResult - 서비스 시작 안 함")
            // 알람은 예약 (다음날 아침에 조건 다시 체크)
            scheduleMorningAlarm(context)
            return false
        }

        val locationStorage = LocationCollectionStorage(context)
        val alarmStorage = ConversationAlarmStorage(context)

        // 위치 수집 시작 시간 가져오기 (기본값: 06:00)
        val startHour = locationStorage.getStartHour()
        val startMinute = locationStorage.getStartMinute()

        // 대화 시간 가져오기 (기본값: 21:00)
        val conversationTime = alarmStorage.getConversationTime() ?: "21:00"
        val endHour = try { conversationTime.split(":")[0].toInt() } catch (e: Exception) { 21 }
        val endMinute = try { conversationTime.split(":")[1].toInt() } catch (e: Exception) { 0 }

        // 현재 시간 확인
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        // 위치 수집 시간 범위 내인지 확인
        val isInCollectionTimeRange = currentMinutes in startMinutes until endMinutes

        if (isInCollectionTimeRange) {
            // 시간 범위 내 → 즉시 서비스 시작
            Log.d(TAG, "위치 수집 시간 범위 내 ($startHour:$startMinute ~ $endHour:$endMinute) - 즉시 시작")
            LocationCollectionService.start(context)
        } else {
            // 시간 범위 밖 → 내일 아침부터 수집
            Log.d(TAG, "위치 수집 시간 범위 밖 - 내일 아침부터 수집 시작 예정")
        }

        scheduleMorningAlarm(context)
        Log.d(TAG, "위치 수집 활성화 완료")
        return true
    }

    /**
     * 시간 범위 내이면 서비스 시작 (필수 조건 체크 없이)
     * 이미 checkPrerequisites()로 조건 체크를 완료한 후 호출할 것
     */
    fun startServiceIfInTimeRange(context: Context) {
        val locationStorage = LocationCollectionStorage(context)
        val alarmStorage = ConversationAlarmStorage(context)

        // 위치 수집 시작 시간 가져오기 (기본값: 06:00)
        val startHour = locationStorage.getStartHour()
        val startMinute = locationStorage.getStartMinute()

        // 대화 시간 가져오기 (기본값: 21:00)
        val conversationTime = alarmStorage.getConversationTime() ?: "21:00"
        val endHour = try { conversationTime.split(":")[0].toInt() } catch (e: Exception) { 21 }
        val endMinute = try { conversationTime.split(":")[1].toInt() } catch (e: Exception) { 0 }

        // 현재 시간 확인
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        // 위치 수집 시간 범위 내인지 확인
        val isInCollectionTimeRange = currentMinutes in startMinutes until endMinutes

        if (isInCollectionTimeRange) {
            // 시간 범위 내 → 즉시 서비스 시작
            Log.d(TAG, "위치 수집 시간 범위 내 ($startHour:$startMinute ~ $endHour:$endMinute) - 즉시 시작")
            LocationCollectionService.start(context)
        } else {
            // 시간 범위 밖 → 서비스 시작 안 함
            Log.d(TAG, "위치 수집 시간 범위 밖 - 서비스 시작 안 함")
        }
    }

    /**
     * 위치 수집 서비스 시작 필수 조건 확인
     */
    fun checkPrerequisites(context: Context): PrerequisiteResult {
        // 1. FINE 위치 권한 확인
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            return if (hasCoarseLocation) {
                PrerequisiteResult.COARSE_LOCATION_ONLY
            } else {
                PrerequisiteResult.NO_LOCATION_PERMISSION
            }
        }

        // 2. 백그라운드 위치 권한 확인 (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackgroundLocation) {
                return PrerequisiteResult.NO_BACKGROUND_LOCATION
            }
        }

        // 3. 배터리 최적화 해제 확인
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            return PrerequisiteResult.BATTERY_OPTIMIZATION_ENABLED
        }

        return PrerequisiteResult.ALL_SATISFIED
    }

    /**
     * 위치 수집 필수 조건 결과
     */
    enum class PrerequisiteResult {
        ALL_SATISFIED,              // 모든 조건 충족
        NO_LOCATION_PERMISSION,     // 위치 권한 없음
        COARSE_LOCATION_ONLY,       // 대략적 위치만 허용 (정밀 위치 필요)
        NO_BACKGROUND_LOCATION,     // 백그라운드 위치 권한 없음
        BATTERY_OPTIMIZATION_ENABLED // 배터리 최적화 해제 안 됨
    }
}
