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
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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
        // 디버그: 위치 수집 시도 시 상태 로그
        logLocationDebugInfo()

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

            // 타임아웃 설정 (30초)
            val location = withTimeout(LOCATION_TIMEOUT_MS) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).await()
            }

            if (location != null) {
                locationStorageManager.saveLocation(location.latitude, location.longitude)
                locationCollectionStorage.saveLastCollectionTime(System.currentTimeMillis())
                Log.d(TAG, "✅ 위치 수집 완료: lat=${location.latitude}, lon=${location.longitude}")
            } else {
                // 위치가 null인 경우 상세 원인 분석
                logLocationNullReason()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "❌ 위치 수집 타임아웃 (${LOCATION_TIMEOUT_MS / 1000}초 초과)")
            logLocationNullReason()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 위치 권한 오류 - 권한이 거부되었거나 취소됨", e)
        } catch (e: IllegalStateException) {
            // Google Play Services 문제
            Log.e(TAG, "❌ Google Play Services 오류 - ${e.message}", e)
            Log.w(TAG, "  → Google Play Services 업데이트가 필요하거나 사용 불가 상태")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 위치 수집 실패 - ${e.javaClass.simpleName}: ${e.message}", e)
            // 추가 힌트
            logExceptionHints(e)
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
     * 위치 수집 디버그 정보 로그
     * 10분마다 위치 수집 시도할 때 현재 상태를 상세히 기록
     */
    private fun logLocationDebugInfo() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            // 1. GPS 상태
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

            // 2. 네트워크 위치 상태
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            // 3. 위치 서비스 전체 on/off (Android P+)
            val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                isGpsEnabled || isNetworkEnabled
            }

            // 4. 권한 상태
            val hasFineLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Android 9 이하는 백그라운드 권한 불필요
            }

            // 5. 배터리 최적화 상태
            val isBatteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)

            // 6. 마지막 수집 시간
            val lastCollectionTime = locationCollectionStorage.getLastCollectionTime()
            val elapsedMinutes = if (lastCollectionTime > 0) {
                (System.currentTimeMillis() - lastCollectionTime) / 60000
            } else {
                -1L
            }

            Log.d(TAG, """
                |========== 위치 수집 디버그 정보 ==========
                |[위치 서비스]
                |  - 위치 서비스 활성화: $isLocationEnabled
                |  - GPS 활성화: $isGpsEnabled
                |  - 네트워크 위치 활성화: $isNetworkEnabled
                |[권한 상태]
                |  - 정밀 위치(FINE): $hasFineLocation
                |  - 대략 위치(COARSE): $hasCoarseLocation
                |  - 백그라운드 위치: $hasBackgroundLocation
                |[배터리]
                |  - 배터리 최적화 제외: $isBatteryOptimizationIgnored
                |[수집 정보]
                |  - 마지막 수집: ${if (elapsedMinutes >= 0) "${elapsedMinutes}분 전" else "없음"}
                |============================================
            """.trimMargin())

            // 문제 발견 시 경고 로그
            if (!isLocationEnabled) {
                Log.w(TAG, "⚠️ 위치 서비스가 꺼져 있습니다!")
            }
            if (!isGpsEnabled && !isNetworkEnabled) {
                Log.w(TAG, "⚠️ GPS와 네트워크 위치 모두 꺼져 있습니다!")
            }
            if (!hasFineLocation) {
                Log.w(TAG, "⚠️ 정밀 위치 권한이 없습니다!")
            }
            if (!hasBackgroundLocation) {
                Log.w(TAG, "⚠️ 백그라운드 위치 권한이 없습니다!")
            }
            if (!isBatteryOptimizationIgnored) {
                Log.w(TAG, "⚠️ 배터리 최적화가 활성화되어 있습니다!")
            }

        } catch (e: Exception) {
            Log.e(TAG, "디버그 정보 로그 실패", e)
        }
    }

    /**
     * 위치가 null인 경우 상세 원인 분석 로그
     */
    private fun logLocationNullReason() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            // 비행기 모드 체크
            val isAirplaneModeOn = Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0

            val reason = when {
                isAirplaneModeOn -> "비행기 모드가 켜져 있음 (설정에서 해제 필요)"
                !isGpsEnabled && !isNetworkEnabled -> "GPS/네트워크 위치 모두 꺼져 있음 (사용자가 위치 설정에서 끔)"
                !isGpsEnabled -> "GPS만 꺼져 있음 (실내이거나 GPS 비활성화)"
                else -> "일시적 위치 확인 불가 (실내/건물 내부/위성 신호 약함)"
            }

            Log.w(TAG, """
                |❌ 위치를 가져올 수 없음 - 원인 분석:
                |  - 비행기 모드: $isAirplaneModeOn
                |  - GPS 활성화: $isGpsEnabled
                |  - 네트워크 위치: $isNetworkEnabled
                |  - 판단된 원인: $reason
            """.trimMargin())

            // 삼성 기기 힌트
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val isBatteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
                if (isBatteryOptimizationIgnored) {
                    Log.w(TAG, "💡 삼성 기기 힌트: 배터리 최적화는 해제됐지만 수집 안 되면 '설정 → 배터리 → 백그라운드 사용 제한'에서 이 앱을 제외해주세요")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "위치 null 원인 분석 실패", e)
        }
    }

    /**
     * 예외 발생 시 추가 힌트 로그
     */
    private fun logExceptionHints(e: Exception) {
        val message = e.message ?: ""

        when {
            message.contains("UNAVAILABLE", ignoreCase = true) ||
            message.contains("unavailable", ignoreCase = true) -> {
                Log.w(TAG, "💡 힌트: 위치 서비스를 사용할 수 없습니다. 기기 재시작 또는 위치 설정 확인 필요")
            }
            message.contains("GooglePlayServices", ignoreCase = true) ||
            message.contains("play services", ignoreCase = true) -> {
                Log.w(TAG, "💡 힌트: Google Play Services 문제. Play Store에서 업데이트 필요")
            }
            message.contains("timeout", ignoreCase = true) -> {
                Log.w(TAG, "💡 힌트: 위치 조회 시간 초과. 실외로 이동하면 GPS 신호가 좋아집니다 (Wi-Fi 켜면 더 빠름)")
            }
            message.contains("denied", ignoreCase = true) ||
            message.contains("permission", ignoreCase = true) -> {
                Log.w(TAG, "💡 힌트: 권한 문제. 앱 설정에서 위치 권한 '항상 허용'으로 변경 필요")
            }
        }
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
        private const val LOCATION_TIMEOUT_MS = 30 * 1000L          // 위치 조회 타임아웃 30초

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
