package com.example.graduation_project.data.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.graduation_project.MainActivity
import com.example.graduation_project.R
import com.example.graduation_project.data.local.AppDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.graduation_project.data.alarm.ConversationAlarmStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 백그라운드 GPS 위치 수집 서비스
 *
 * Foreground Service로 실행되어 10분마다 GPS 위치를 수집.
 * - 배터리 15% 미만: 30분 간격으로 조절
 * - 대화 종료 시 서비스 중지
 * - 다음날 아침 6시 AlarmManager로 자동 재시작
 */
class LocationCollectionService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationStorageManager: LocationStorageManager
    private lateinit var locationCollectionStorage: LocationCollectionStorage
    private lateinit var conversationAlarmStorage: ConversationAlarmStorage

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var collectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "LocationCollectionService 생성")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val database = AppDatabase.getInstance(this)
        locationStorageManager = LocationStorageManager(database.locationPointDao())
        locationCollectionStorage = LocationCollectionStorage(this)
        conversationAlarmStorage = ConversationAlarmStorage(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationCollectionService 시작")

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "서비스 중지 요청")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Foreground Service 시작
        startForegroundService()

        // 이미 수집 중이면 다시 시작하지 않음
        if (collectionJob?.isActive == true) {
            Log.d(TAG, "이미 위치 수집 중 - 중복 시작 무시")
            return START_STICKY
        }

        // 위치 수집 시작
        startLocationCollection()

        // 아침 인사 알림 표시
        showMorningGreetingNotification()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "LocationCollectionService 종료")
        collectionJob?.cancel()
    }

    private fun startForegroundService() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "위치 수집 서비스",
            NotificationManager.IMPORTANCE_DEFAULT  // 상태바에 아이콘 표시
        ).apply {
            description = "하루 동안 방문 장소를 기록합니다"
            setShowBadge(false)
            setSound(null, null)  // 소리 없음
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationCollectionService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 에코")
            .setContentText("오늘 방문 장소를 기록하고 있어요")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "중지", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationCollection() {
        collectionJob?.cancel()
        collectionJob = serviceScope.launch {
            Log.d(TAG, "위치 수집 루프 시작")

            // 시작 시 즉시 한 번 수집
            collectLocation()

            while (isActive) {
                val intervalMs = getCollectionInterval()
                Log.d(TAG, "다음 수집까지 ${intervalMs / 60000}분 대기")

                delay(intervalMs)

                // 시간 범위 체크 - 범위 밖이면 서비스 자동 중지
                if (!isCurrentTimeInRange()) {
                    Log.d(TAG, "수집 시간 범위 종료 - 서비스 자동 중지")
                    stopSelf()
                    return@launch
                }

                collectLocation()
            }
        }
    }

    /**
     * 현재 시간이 수집 범위(시작 시간 ~ 대화 시간) 내인지 확인
     */
    private fun isCurrentTimeInRange(): Boolean {
        return try {
            val startTimeStr = locationCollectionStorage.getStartTime()
            // 대화 시간이 설정되지 않은 경우 기본값 21:00 사용
            val endTimeStr = conversationAlarmStorage.getConversationTime() ?: DEFAULT_CONVERSATION_TIME

            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val now = LocalTime.now()
            val start = LocalTime.parse(startTimeStr, formatter)
            val end = LocalTime.parse(endTimeStr, formatter)

            val isInRange = if (start.isBefore(end) || start == end) {
                // 일반적인 경우: 06:00 ~ 21:00
                now in start..end
            } else {
                // 자정을 넘기는 경우: 22:00 ~ 06:00
                now >= start || now <= end
            }

            Log.d(TAG, "시간 범위 체크: now=$now, range=$startTimeStr~$endTimeStr, inRange=$isInRange")
            isInRange
        } catch (e: Exception) {
            Log.e(TAG, "시간 범위 체크 실패", e)
            true  // 오류 시 계속 수집
        }
    }

    private suspend fun collectLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "위치 권한 없음 - 서비스 중지")
            stopSelf()  // 권한 없으면 서비스 중지 (리소스 낭비 방지)
            return
        }

        try {
            val cancellationToken = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).await()

            if (location != null) {
                locationStorageManager.saveLocation(location.latitude, location.longitude)
                Log.d(TAG, "위치 수집 완료: lat=${location.latitude}, lon=${location.longitude}")
            } else {
                Log.w(TAG, "위치를 가져올 수 없음")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "위치 권한 오류", e)
        } catch (e: Exception) {
            Log.e(TAG, "위치 수집 실패", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            Log.d(TAG, "정밀 위치 권한(FINE) 없음 - 50m 체류 감지에 필수")
        }

        return hasFineLocation
    }

    /**
     * 배터리 상태에 따른 수집 간격 결정
     * - 정상: 10분
     * - 배터리 15% 미만: 30분
     */
    private fun getCollectionInterval(): Long {
        val batteryLevel = getBatteryLevel()
        return if (batteryLevel < 15) {
            Log.d(TAG, "배터리 부족 ($batteryLevel%) - 30분 간격으로 전환")
            INTERVAL_LOW_BATTERY_MS
        } else {
            INTERVAL_NORMAL_MS
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    /**
     * "좋은 아침이에요" 인사 알림 표시
     * 10분 후 자동으로 사라짐
     */
    private fun showMorningGreetingNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // 인사 알림용 채널 생성 (없으면)
        val greetingChannel = NotificationChannel(
            GREETING_CHANNEL_ID,
            "아침 인사",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "위치 수집 시작 알림"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(greetingChannel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, GREETING_CHANNEL_ID)
            .setContentTitle("☀️ 오늘도 행복한 하루 되세요!")
            .setContentText("조금 이따 대화 시간에 만나요!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter(GREETING_TIMEOUT_MS)  // 10분 후 자동 사라짐
            .build()

        notificationManager.notify(GREETING_NOTIFICATION_ID, notification)
        Log.d(TAG, "아침 인사 알림 표시")
    }

    companion object {
        private const val TAG = "LocationCollectionService"
        private const val CHANNEL_ID = "location_collection_channel"
        private const val GREETING_CHANNEL_ID = "location_greeting_channel"
        private const val NOTIFICATION_ID = 1001
        private const val GREETING_NOTIFICATION_ID = 1002

        private const val INTERVAL_NORMAL_MS = 10 * 60 * 1000L      // 10분
        private const val INTERVAL_LOW_BATTERY_MS = 30 * 60 * 1000L // 30분
        private const val GREETING_TIMEOUT_MS = 3 * 60 * 1000L      // 3분
        private const val DEFAULT_CONVERSATION_TIME = "21:00"       // 기본 대화 시간

        const val ACTION_STOP = "com.example.graduation_project.STOP_LOCATION_SERVICE"

        // 서비스 실행 상태
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * 서비스 시작
         */
        fun start(context: Context) {
            val intent = Intent(context, LocationCollectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 서비스 중지
         */
        fun stop(context: Context) {
            val intent = Intent(context, LocationCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
