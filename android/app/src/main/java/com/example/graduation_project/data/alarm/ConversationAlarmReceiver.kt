package com.example.graduation_project.data.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.graduation_project.MainActivity
import com.example.graduation_project.R
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationScheduler
import java.util.Calendar

/**
 * 대화 알람 수신 및 알림 표시
 *
 * AlarmManager에 의해 설정된 시간에 호출됨
 */
class ConversationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "대화 알람 수신")

        // 위치 권한 확인
        val hasLocationPermission = hasBackgroundLocationPermission(context)

        if (hasLocationPermission) {
            // 위치 수집 서비스 종료 (대화 시간에 자동 종료)
            LocationCollectionService.stop(context)
            Log.d(TAG, "위치 수집 서비스 종료")

            // 다음날 위치 수집 알람 스케줄링
            LocationScheduler.scheduleMorningAlarm(context)
        }

        // 알림 표시 (권한 상태에 따라 다른 내용)
        showNotification(context, hasLocationPermission)

        // 다음날 알람 재스케줄링
        val storage = ConversationAlarmStorage(context)
        if (storage.isAlarmEnabled()) {
            val time = storage.getConversationTime()
            if (time != null) {
                ConversationAlarmScheduler.scheduleAlarm(context, time)
                Log.d(TAG, "다음날 대화 알람 재스케줄링 완료: $time")
            }
        }
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFineLocation
        }

        return hasFineLocation && hasBackgroundLocation
    }

    private fun showNotification(context: Context, hasLocationPermission: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ 알림 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "대화 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "매일 대화 시간을 알려드립니다"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 홈 화면으로 이동
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_HOME)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_REQUEST_CODE,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림 내용 (권한 상태에 따라 다름)
        val contentText: String
        val bigText: String

        if (hasLocationPermission) {
            contentText = "위치 기록이 완료되었어요. 에코가 기다리고 있어요."
            bigText = "오늘 하루 방문한 장소를 기록했어요.\n에코와 함께 오늘 하루를 이야기해 보세요!"
        } else {
            contentText = "오늘 하루는 어떠셨나요? 에코가 기다리고 있어요."
            bigText = "에코와 함께 오늘 하루를 이야기해 보세요!"
        }

        // 자정까지 남은 시간 계산 (대화 안 하면 자정에 자동 취소)
        val timeoutMs = calculateMillisUntilMidnight()

        // 알림 생성 (대화 시작 또는 자정에 자동 취소)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("에코와 대화할 시간이에요")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)  // 클릭해도 알림 유지 (대화 시작 시 취소)
            .setOngoing(true)      // 스와이프로 삭제 불가
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(timeoutMs)  // 자정에 자동 취소
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "대화 알림 표시 완료 (위치 권한: $hasLocationPermission, 자정까지: ${timeoutMs / 1000 / 60}분)")
    }

    /**
     * 자정까지 남은 시간(밀리초) 계산
     */
    private fun calculateMillisUntilMidnight(): Long {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return midnight.timeInMillis - now.timeInMillis
    }

    companion object {
        private const val TAG = "ConversationAlarmReceiver"
        private const val CHANNEL_ID = "conversation_alarm_channel"
        private const val FAREWELL_CHANNEL_ID = "conversation_farewell_channel"
        const val NOTIFICATION_ID = 3002
        private const val FAREWELL_NOTIFICATION_ID = 3004
        private const val NOTIFICATION_REQUEST_CODE = 3003

        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val NAVIGATE_TO_HOME = "home"

        private const val FAREWELL_TIMEOUT_MS = 3 * 60 * 1000L   // 3분

        /**
         * 대화 알림 취소 (대화 시작 시 호출)
         */
        fun cancelNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "대화 알림 취소")
        }

        /**
         * 대화 종료 알림 표시 (3분 후 자동 사라짐)
         */
        fun showFarewellNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Android 8.0+ 알림 채널 생성
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    FAREWELL_CHANNEL_ID,
                    "대화 종료 알림",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "대화 종료 후 인사 알림"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, FAREWELL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("오늘 대화도 즐거웠어요")
                .setContentText("내일 또 만나요!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(FAREWELL_TIMEOUT_MS)  // 3분 후 자동 사라짐
                .build()

            notificationManager.notify(FAREWELL_NOTIFICATION_ID, notification)
            Log.d(TAG, "대화 종료 알림 표시")
        }
    }
}
