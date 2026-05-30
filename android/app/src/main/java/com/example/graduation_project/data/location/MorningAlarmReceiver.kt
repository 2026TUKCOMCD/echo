package com.example.graduation_project.data.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.graduation_project.MainActivity
import com.example.graduation_project.R

/**
 * 매일 아침 위치 수집 서비스 시작
 *
 * AlarmManager에 의해 사용자가 설정한 시간에 호출됨.
 * 대화 종료로 서비스가 중지된 후 다음날 자동 재시작.
 */
class MorningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "아침 알람 수신 - 위치 수집 서비스 시작 시도")

        // 필수 조건 체크
        val prerequisiteResult = LocationScheduler.checkPrerequisites(context)
        Log.d(TAG, "필수 조건 체크 결과: $prerequisiteResult")

        when (prerequisiteResult) {
            LocationScheduler.PrerequisiteResult.ALL_SATISFIED -> {
                // 모든 조건 충족 - 시간 범위 체크 후 서비스 시작 (조건 중복 체크 없음)
                Log.d(TAG, "모든 조건 충족 - 시간 범위 체크 후 서비스 시작")
                LocationScheduler.startServiceIfInTimeRange(context)
            }
            else -> {
                // 조건 미충족 - 알림으로 사용자에게 안내
                Log.w(TAG, "필수 조건 미충족: $prerequisiteResult - 알림 표시")
                showPrerequisiteNotification(context, prerequisiteResult)
            }
        }

        // 다음날 알람 재스케줄링 (조건 충족 여부와 무관하게 항상)
        LocationScheduler.scheduleMorningAlarm(context)
    }

    /**
     * 필수 조건 미충족 시 통합 알림 표시
     * 알림에 "위치 권한 허용하기" 버튼 추가
     */
    private fun showPrerequisiteNotification(
        context: Context,
        result: LocationScheduler.PrerequisiteResult
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // 알림 채널 생성
        val channel = NotificationChannel(
            CHANNEL_ID,
            "위치 수집 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "위치 수집 상태 알림"
        }
        notificationManager.createNotificationChannel(channel)

        // 알림 탭 또는 버튼 클릭 시 앱 열기 + 권한 다이얼로그 표시
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHOW_PERMISSION_DIALOG, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("📍 위치 수집이 되지 않고 있어요")
            .setContentText("위치 권한을 허용하시면 방문 장소를 기록할 수 있어요")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "위치 권한 허용하기", pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "필수 조건 미충족 알림 표시 (미충족 조건: $result)")
    }

    companion object {
        private const val TAG = "MorningAlarmReceiver"
        private const val CHANNEL_ID = "location_prerequisite_channel"
        private const val NOTIFICATION_ID = 2001

        const val EXTRA_SHOW_PERMISSION_DIALOG = "show_permission_dialog"
    }
}
