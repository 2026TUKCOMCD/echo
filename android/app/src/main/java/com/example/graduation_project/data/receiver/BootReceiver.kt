package com.example.graduation_project.data.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationScheduler

/**
 * 부팅 완료 시 필요한 서비스/알람 자동 시작
 *
 * 핸드폰이 재부팅되면 다음 작업을 수행:
 * - 위치 수집 서비스 시작 (권한이 있을 때만)
 * - 대화 알람 재스케줄링
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "부팅 완료 - 서비스 및 알람 재시작")

        // 1. 위치 수집 관련 처리
        scheduleLocationCollection(context)

        // 2. 대화 알람 재스케줄링
        ConversationAlarmScheduler.rescheduleFromStorage(context)
    }

    private fun scheduleLocationCollection(context: Context) {
        // 위치 권한 체크
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            Log.w(TAG, "위치 권한 없음 - 위치 서비스 시작 건너뜀")
            return
        }

        if (!hasBackgroundPermission) {
            Log.w(TAG, "백그라운드 위치 권한 없음 - 위치 서비스 시작 건너뜀")
            return
        }

        // 아침 6시 알람 스케줄링
        LocationScheduler.scheduleMorningAlarm(context)

        // 테스트용: 시간 제한 없이 항상 시작
        Log.d(TAG, "위치 수집 서비스 즉시 시작")
        LocationCollectionService.start(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
