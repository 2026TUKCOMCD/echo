package com.example.graduation_project.data.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 매일 아침 6시 위치 수집 서비스 시작
 *
 * AlarmManager에 의해 매일 아침 6시에 호출됨.
 * 대화 종료로 서비스가 중지된 후 다음날 자동 재시작.
 */
class MorningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "아침 6시 알람 수신 - 위치 수집 서비스 시작")

        // 위치 권한 체크
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission || !hasBackgroundPermission) {
            Log.w(TAG, "위치 권한 없음 - 서비스 시작 건너뜀")
            return
        }

        // 위치 수집 서비스 시작
        LocationCollectionService.start(context)

        // 다음날 알람 재스케줄링
        LocationScheduler.scheduleMorningAlarm(context)
    }

    companion object {
        private const val TAG = "MorningAlarmReceiver"
    }
}
