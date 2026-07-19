package com.example.graduation_project.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.location.LocationScheduler

/**
 * 부팅 완료 및 앱 업데이트 시 필요한 서비스/알람 자동 시작
 *
 * 다음 이벤트를 처리:
 * - BOOT_COMPLETED: 기기 재부팅
 * - MY_PACKAGE_REPLACED: 앱 업데이트 완료
 *
 * 두 경우 모두 AlarmManager 알람이 초기화되므로 재스케줄링 필요
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "부팅 완료 - 서비스 및 알람 재시작")
                handleRestart(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "앱 업데이트 완료 - 서비스 및 알람 재시작")
                handleRestart(context)
            }
        }
    }

    /**
     * 부팅/업데이트 후 공통 처리
     */
    private fun handleRestart(context: Context) {
        // 1. 위치 수집 관련 처리
        scheduleLocationCollection(context)

        // 2. 대화 알람 재스케줄링
        ConversationAlarmScheduler.rescheduleFromStorage(context)
    }

    private fun scheduleLocationCollection(context: Context) {
        // 필수 조건 체크 및 서비스 시작 (조건 미충족 시에도 알람은 스케줄링)
        val prerequisiteResult = LocationScheduler.checkPrerequisites(context)
        Log.d(TAG, "필수 조건 체크 결과: $prerequisiteResult")

        when (prerequisiteResult) {
            LocationScheduler.PrerequisiteResult.ALL_SATISFIED -> {
                // 모든 조건 충족 - enableLocationCollection()으로 시간 범위 체크 후 시작
                LocationScheduler.enableLocationCollection(context)
            }
            else -> {
                // 조건 미충족 - 알람만 스케줄링 (다음 아침에 조건 다시 체크)
                Log.w(TAG, "필수 조건 미충족: $prerequisiteResult - 알람만 스케줄링")
                LocationScheduler.scheduleMorningAlarm(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
