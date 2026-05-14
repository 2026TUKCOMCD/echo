package com.example.graduation_project.data.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.graduation_project.MainActivity
import com.example.graduation_project.R

/**
 * 대화 알람 수신 및 알림 표시
 *
 * AlarmManager에 의해 설정된 시간에 호출됨
 */
class ConversationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "대화 알람 수신")

        // 알림 표시
        showNotification(context)

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

    private fun showNotification(context: Context) {
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

        // 알림 생성
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("에코와 대화할 시간이에요")
            .setContentText("오늘 하루는 어떠셨나요? 에코가 기다리고 있어요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "대화 알림 표시 완료")
    }

    companion object {
        private const val TAG = "ConversationAlarmReceiver"
        private const val CHANNEL_ID = "conversation_alarm_channel"
        private const val NOTIFICATION_ID = 3002
        private const val NOTIFICATION_REQUEST_CODE = 3003

        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val NAVIGATE_TO_HOME = "home"
    }
}
