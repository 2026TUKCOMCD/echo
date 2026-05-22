package com.example.graduation_project.data.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationCollectionService onStartCommand - action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "서비스 중지 요청")
                LocationScheduler.cancelCollectionAlarm(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_COLLECT -> {
                // AlarmManager에서 트리거된 위치 수집 요청
                Log.d(TAG, "알람에 의한 위치 수집 요청")
                performLocationCollection()
                return START_STICKY
            }
        }

        // 서비스 최초 시작 (MorningAlarmReceiver 또는 enableLocationCollection에서 호출)
        // Foreground Service 시작
        startForegroundService()

        // 즉시 첫 위치 수집 + 다음 알람 스케줄링
        performLocationCollection()
        LocationScheduler.scheduleNextCollectionAlarm(this)

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
        // 서비스 종료 시 알람도 취소
        LocationScheduler.cancelCollectionAlarm(this)
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

    /**
     * 위치 수집 실행 (알람 또는 서비스 시작 시 호출)
     * - 이전의 while 루프 + delay 방식에서 AlarmManager 기반으로 변경
     * - Doze 모드에서도 정확하게 동작
     */
    private fun performLocationCollection() {
        // 이미 수집 중이면 중복 실행 방지
        if (collectionJob?.isActive == true) {
            Log.d(TAG, "이미 위치 수집 중 - 중복 실행 무시")
            return
        }

        collectionJob = serviceScope.launch {
            collectLocation()
        }
    }

    private suspend fun collectLocation() {
        val permissionStatus = checkLocationPermissionStatus()
        if (permissionStatus != LocationPermissionStatus.FINE_GRANTED) {
            when (permissionStatus) {
                LocationPermissionStatus.COARSE_ONLY -> {
                    Log.w(TAG, "대략적 위치 권한만 허용됨 - 정확한 위치 필요")
                    showPreciseLocationRequiredNotification()
                }
                else -> {
                    Log.w(TAG, "위치 권한 없음 - 서비스 중지")
                }
            }
            stopSelf()
            return
        }

        // 최소 간격 체크 (중복 수집 방지)
        val elapsed = locationCollectionStorage.getElapsedSinceLastCollection()
        if (elapsed < MIN_COLLECTION_INTERVAL_MS) {
            Log.d(TAG, "최소 간격 미달 - 스킵 (${elapsed / 60000}분 경과, 최소 ${MIN_COLLECTION_INTERVAL_MS / 60000}분 필요)")
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
                locationCollectionStorage.saveLastCollectionTime(System.currentTimeMillis())
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

    /**
     * 위치 권한 상태 확인
     */
    private fun checkLocationPermissionStatus(): LocationPermissionStatus {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return when {
            hasFineLocation -> LocationPermissionStatus.FINE_GRANTED
            hasCoarseLocation -> LocationPermissionStatus.COARSE_ONLY
            else -> LocationPermissionStatus.DENIED
        }
    }

    /**
     * 정밀 위치 권한 필요 알림 표시
     */
    private fun showPreciseLocationRequiredNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 정확한 위치 권한 필요")
            .setContentText("방문 장소 기록을 위해 '정확한 위치'를 허용해주세요")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(PERMISSION_NOTIFICATION_ID, notification)
        Log.d(TAG, "정밀 위치 권한 필요 알림 표시")
    }

    /**
     * 위치 권한 상태
     */
    private enum class LocationPermissionStatus {
        FINE_GRANTED,   // 정밀 위치 허용됨
        COARSE_ONLY,    // 대략적 위치만 허용됨
        DENIED          // 권한 없음
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
        private const val PERMISSION_NOTIFICATION_ID = 1003

        private const val MIN_COLLECTION_INTERVAL_MS = 9 * 60 * 1000L // 최소 9분 (중복 수집 방지)
        private const val GREETING_TIMEOUT_MS = 3 * 60 * 1000L      // 3분

        const val ACTION_STOP = "com.example.graduation_project.STOP_LOCATION_SERVICE"
        const val ACTION_COLLECT = "com.example.graduation_project.COLLECT_LOCATION"

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
