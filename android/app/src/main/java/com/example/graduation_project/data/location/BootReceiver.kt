package com.example.graduation_project.data.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 부팅 완료 시 위치 수집 서비스 자동 시작
 *
 * 핸드폰이 재부팅되면 LocationCollectionService를 자동으로 시작.
 * 위치 권한이 있을 때만 서비스 시작.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "부팅 완료 - 위치 수집 서비스 시작 확인")

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
            Log.w(TAG, "위치 권한 없음 - 서비스 시작 건너뜀")
            return
        }

        if (!hasBackgroundPermission) {
            Log.w(TAG, "백그라운드 위치 권한 없음 - 서비스 시작 건너뜀")
            return
        }

        // 아침 6시 알람 스케줄링
        LocationScheduler.scheduleMorningAlarm(context)

        // 현재 시간이 6시~자정 사이면 즉시 서비스 시작
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour in 6..23) {
            Log.d(TAG, "활동 시간대 - 위치 수집 서비스 즉시 시작")
            LocationCollectionService.start(context)
        } else {
            Log.d(TAG, "수면 시간대 - 아침 6시에 서비스 시작 예약됨")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
