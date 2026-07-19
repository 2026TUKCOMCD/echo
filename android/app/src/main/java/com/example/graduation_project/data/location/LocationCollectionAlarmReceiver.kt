package com.example.graduation_project.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 주기적 위치 수집 알람 수신자
 *
 * AlarmManager.setExactAndAllowWhileIdle()로 10분마다 트리거됨.
 * Doze 모드에서도 정확하게 동작하여 백그라운드 위치 수집 신뢰성 보장.
 */
class LocationCollectionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "위치 수집 알람 수신")

        // 서비스가 실행 중인지 확인
        if (!LocationCollectionService.isRunning) {
            Log.d(TAG, "서비스가 실행 중이지 않음 - 알람 무시")
            return
        }

        // 시간 범위 체크
        if (!LocationScheduler.isCurrentTimeInCollectionRange(context)) {
            Log.d(TAG, "수집 시간 범위 종료 - 서비스 중지 및 알람 취소")
            LocationCollectionService.stop(context)
            LocationScheduler.cancelCollectionAlarm(context)
            return
        }

        // 위치 수집 트리거 (서비스에 Intent 전송)
        val collectIntent = Intent(context, LocationCollectionService::class.java).apply {
            action = LocationCollectionService.ACTION_COLLECT
        }
        context.startService(collectIntent)

        // 다음 수집 알람 스케줄링
        LocationScheduler.scheduleNextCollectionAlarm(context)
    }

    companion object {
        private const val TAG = "LocationCollectionAlarm"
    }
}
