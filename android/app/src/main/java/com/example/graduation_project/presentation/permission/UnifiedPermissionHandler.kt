package com.example.graduation_project.presentation.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.presentation.health.openHealthConnectSettings

private const val PERMISSION_STATE_PREFS = "permission_state"
private const val KEY_PERMISSION_FLOW_COMPLETED = "permission_flow_completed"

// 선택 권한별 "마지막으로 확인됐을 때 허용 상태였는지" 이력.
// 재설치 등으로 OS 권한이 초기화된 경우(이전엔 true였는데 지금 실제로는 없는 경우)에만
// 조용히 다시 요청하고, 사용자가 원래 거부했던 권한은 매번 다시 묻지 않기 위해 사용.
private const val KEY_LOCATION_FOREGROUND_GRANTED = "location_foreground_granted"
private const val KEY_LOCATION_BACKGROUND_GRANTED = "location_background_granted"
private const val KEY_NOTIFICATION_GRANTED = "notification_granted"
private const val KEY_HEALTH_CONNECT_GRANTED = "health_connect_granted"

/**
 * 정확한 알람 권한이 없으면 시스템 설정으로 안내 (Android 12+)
 */
private fun requestExactAlarmPermissionIfNeeded(context: Context) {
    if (!PermissionChecker.hasExactAlarmPermission(context)) {
        PermissionChecker.openExactAlarmSettings(context)
    }
}

/**
 * 권한 요청 단계
 */
enum class PermissionStep {
    INTRO,           // 통합 안내 다이얼로그
    MICROPHONE,      // 마이크 권한 (필수)
    LOCATION_FOREGROUND,  // 포그라운드 위치 권한
    LOCATION_BACKGROUND,  // 백그라운드 위치 권한
    NOTIFICATION,    // 알림 권한
    HEALTH_CONNECT,  // Health Connect 권한
    COMPLETED        // 모든 권한 처리 완료
}

/**
 * 통합 권한 핸들러
 * 앱 시작 시 모든 권한을 순차적으로 요청
 */
@Composable
fun UnifiedPermissionHandler(
    onAllPermissionsHandled: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permPrefs = context.getSharedPreferences(PERMISSION_STATE_PREFS, Context.MODE_PRIVATE)

    // 계정별로 완료 여부를 구분 저장 (기기 공유·로그아웃 후 새 계정 가입 시
    // 이전 계정의 완료 플래그를 그대로 물려받아 권한 카드가 스킵되는 것을 방지)
    val currentUserId = remember { TokenStorage(context).getCurrentUserId() }
    val permissionFlowKey = remember(currentUserId) {
        if (currentUserId != null) "${KEY_PERMISSION_FLOW_COMPLETED}_$currentUserId" else KEY_PERMISSION_FLOW_COMPLETED
    }
    val locationForegroundGrantedKey = remember(currentUserId) {
        if (currentUserId != null) "${KEY_LOCATION_FOREGROUND_GRANTED}_$currentUserId" else KEY_LOCATION_FOREGROUND_GRANTED
    }
    val locationBackgroundGrantedKey = remember(currentUserId) {
        if (currentUserId != null) "${KEY_LOCATION_BACKGROUND_GRANTED}_$currentUserId" else KEY_LOCATION_BACKGROUND_GRANTED
    }
    val notificationGrantedKey = remember(currentUserId) {
        if (currentUserId != null) "${KEY_NOTIFICATION_GRANTED}_$currentUserId" else KEY_NOTIFICATION_GRANTED
    }
    val healthConnectGrantedKey = remember(currentUserId) {
        if (currentUserId != null) "${KEY_HEALTH_CONNECT_GRANTED}_$currentUserId" else KEY_HEALTH_CONNECT_GRANTED
    }

    // 이전에 권한 플로우를 완료한 적 있는지 (SharedPreferences에 계정별로 영구 저장)
    val previouslyCompleted = permPrefs.getBoolean(permissionFlowKey, false)

    // 완료 이력이 있으면 안내 다이얼로그(INTRO)는 건너뛰되, 각 권한 단계는 그대로 거쳐가며
    // 실제 OS 권한 상태를 다시 검증한다 (재설치로 권한이 초기화된 경우를 감지하기 위함).
    // OS 권한 상태를 매번 진실의 원천으로 재확인하므로, 앱 업데이트/재설치를 별도로
    // 감지하는 로직(버전 코드 비교 등)은 필요 없다.
    var currentStep by remember {
        mutableStateOf(if (previouslyCompleted) PermissionStep.MICROPHONE else PermissionStep.INTRO)
    }

    // 거부된 권한 설정 다이얼로그 표시 상태
    var showMicSettingsDialog by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var showHealthConnectSettingsDialog by remember { mutableStateOf(false) }

    // COMPLETED 단계에 도달했을 때만 true로 바뀜 (전체 플로우 스킵 용도로는 더 이상 쓰지 않음)
    var hasCompletedOnboarding by remember { mutableStateOf(false) }

    // 마이크 권한 보유 여부 (Compose 상태로 보관해, ON_RESUME 재확인 시 recomposition 유발)
    var hasMicPermission by remember {
        mutableStateOf(PermissionChecker.hasMicrophonePermission(context))
    }

    // 설정 앱에서 권한을 허용하고 돌아온 경우를 감지하기 위해 ON_RESUME마다 재확인
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = PermissionChecker.hasMicrophonePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 마이크 권한 요청 런처
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentStep = PermissionStep.LOCATION_FOREGROUND
        } else {
            // 마이크는 필수이므로 설정 안내
            showMicSettingsDialog = true
        }
    }

    // 포그라운드 위치 권한 요청 런처
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permPrefs.edit().putBoolean(locationForegroundGrantedKey, granted).apply()
        if (granted) {
            currentStep = PermissionStep.LOCATION_BACKGROUND
        } else {
            // 위치는 선택이므로 다음으로 진행
            currentStep = PermissionStep.NOTIFICATION
        }
    }

    // 백그라운드 위치 권한 요청 런처
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permPrefs.edit().putBoolean(locationBackgroundGrantedKey, granted).apply()
        if (granted) {
            LocationScheduler.enableLocationCollection(context)
        }
        currentStep = PermissionStep.NOTIFICATION
    }

    // 알림 권한 요청 런처 (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permPrefs.edit().putBoolean(notificationGrantedKey, granted).apply()
        // 알람 예약은 알림 권한과 별개이므로, 알림 거부 여부와 무관하게 정확한 알람 권한 요청
        requestExactAlarmPermissionIfNeeded(context)
        if (granted) {
            currentStep = PermissionStep.HEALTH_CONNECT
        } else {
            showNotificationSettingsDialog = true
        }
    }

    // Health Connect 권한 요청 런처
    val healthConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val allGranted = PermissionChecker.getHealthConnectPermissions().all { grantResults[it] == true }
        permPrefs.edit().putBoolean(healthConnectGrantedKey, allGranted).apply()
        currentStep = PermissionStep.COMPLETED
        hasCompletedOnboarding = true
        permPrefs.edit().putBoolean(permissionFlowKey, true).apply()
        onAllPermissionsHandled()
    }

    // 권한 단계별 처리
    LaunchedEffect(currentStep) {
        when (currentStep) {
            PermissionStep.MICROPHONE -> {
                if (PermissionChecker.hasMicrophonePermission(context)) {
                    currentStep = PermissionStep.LOCATION_FOREGROUND
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            PermissionStep.LOCATION_FOREGROUND -> {
                if (PermissionChecker.hasForegroundLocationPermission(context)) {
                    permPrefs.edit().putBoolean(locationForegroundGrantedKey, true).apply()
                    currentStep = PermissionStep.LOCATION_BACKGROUND
                } else {
                    // 최초 플로우이거나, 이전엔 허용했었는데 지금은 사라진 경우(재설치 등)만 재요청.
                    // 사용자가 원래 거부했던 선택 권한은 매번 다시 묻지 않는다.
                    val wasGrantedBefore = permPrefs.getBoolean(locationForegroundGrantedKey, false)
                    if (!previouslyCompleted || wasGrantedBefore) {
                        foregroundLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        currentStep = PermissionStep.NOTIFICATION
                    }
                }
            }
            PermissionStep.LOCATION_BACKGROUND -> {
                if (PermissionChecker.hasBackgroundLocationPermission(context)) {
                    permPrefs.edit().putBoolean(locationBackgroundGrantedKey, true).apply()
                    LocationScheduler.enableLocationCollection(context)
                    currentStep = PermissionStep.NOTIFICATION
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val wasGrantedBefore = permPrefs.getBoolean(locationBackgroundGrantedKey, false)
                    if (!previouslyCompleted || wasGrantedBefore) {
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        currentStep = PermissionStep.NOTIFICATION
                    }
                } else {
                    LocationScheduler.enableLocationCollection(context)
                    currentStep = PermissionStep.NOTIFICATION
                }
            }
            PermissionStep.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (PermissionChecker.hasNotificationPermission(context)) {
                        permPrefs.edit().putBoolean(notificationGrantedKey, true).apply()
                        requestExactAlarmPermissionIfNeeded(context)
                        currentStep = PermissionStep.HEALTH_CONNECT
                    } else {
                        val wasGrantedBefore = permPrefs.getBoolean(notificationGrantedKey, false)
                        if (!previouslyCompleted || wasGrantedBefore) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            requestExactAlarmPermissionIfNeeded(context)
                            currentStep = PermissionStep.HEALTH_CONNECT
                        }
                    }
                } else {
                    requestExactAlarmPermissionIfNeeded(context)
                    currentStep = PermissionStep.HEALTH_CONNECT
                }
            }
            PermissionStep.HEALTH_CONNECT -> {
                if (PermissionChecker.isHealthConnectAvailable(context)) {
                    val alreadyGranted = try {
                        HealthConnectManager(context).checkGrantedPermissions()
                    } catch (e: Exception) {
                        false
                    }
                    if (alreadyGranted) {
                        permPrefs.edit().putBoolean(healthConnectGrantedKey, true).apply()
                        currentStep = PermissionStep.COMPLETED
                        hasCompletedOnboarding = true
                        permPrefs.edit().putBoolean(permissionFlowKey, true).apply()
                        onAllPermissionsHandled()
                    } else {
                        val wasGrantedBefore = permPrefs.getBoolean(healthConnectGrantedKey, false)
                        if (!previouslyCompleted || wasGrantedBefore) {
                            healthConnectLauncher.launch(PermissionChecker.getHealthConnectPermissions().toTypedArray())
                        } else {
                            currentStep = PermissionStep.COMPLETED
                            hasCompletedOnboarding = true
                            permPrefs.edit().putBoolean(permissionFlowKey, true).apply()
                            onAllPermissionsHandled()
                        }
                    }
                } else {
                    currentStep = PermissionStep.COMPLETED
                    hasCompletedOnboarding = true
                    permPrefs.edit().putBoolean(permissionFlowKey, true).apply()
                    onAllPermissionsHandled()
                }
            }
            else -> { /* INTRO, COMPLETED - 별도 처리 */ }
        }
    }

    // 이미 온보딩 완료 또는 모든 필수 권한 있음
    if (hasCompletedOnboarding || currentStep == PermissionStep.COMPLETED) {
        // 마이크 권한 체크 (필수) - Compose 상태를 읽어, ON_RESUME 재확인 시 recomposition되도록 함
        if (!hasMicPermission) {
            MicrophonePermissionSettingsDialog(
                onOpenSettings = { PermissionChecker.openAppSettings(context) }
            )
        } else {
            content()
        }
        return
    }

    // 통합 안내 다이얼로그
    if (currentStep == PermissionStep.INTRO) {
        AllPermissionsIntroDialog(
            onStartPermissions = {
                currentStep = PermissionStep.MICROPHONE
            }
        )
    }

    // 마이크 설정 안내 다이얼로그 (필수 권한 거부 시)
    if (showMicSettingsDialog) {
        MicrophonePermissionSettingsDialog(
            onOpenSettings = {
                PermissionChecker.openAppSettings(context)
                showMicSettingsDialog = false
            }
        )
    }

    // 위치 설정 안내 다이얼로그 (거부 시)
    if (showLocationSettingsDialog) {
        BackgroundLocationSettingsDialog(
            onDismiss = {
                showLocationSettingsDialog = false
                currentStep = PermissionStep.NOTIFICATION
            },
            onOpenSettings = {
                PermissionChecker.openAppSettings(context)
                showLocationSettingsDialog = false
            }
        )
    }

    // 알림 설정 안내 다이얼로그 (거부 시)
    if (showNotificationSettingsDialog) {
        NotificationPermissionSettingsDialog(
            onDismiss = {
                showNotificationSettingsDialog = false
                currentStep = PermissionStep.HEALTH_CONNECT
            },
            onOpenSettings = {
                PermissionChecker.openAppSettings(context)
                showNotificationSettingsDialog = false
                // 설정에서 돌아온 뒤 흐름이 멈추지 않도록 다음 단계로 진행
                currentStep = PermissionStep.HEALTH_CONNECT
            }
        )
    }

    // Health Connect 설정 안내 다이얼로그 (거부 시)
    if (showHealthConnectSettingsDialog) {
        HealthConnectPermissionSettingsDialog(
            onDismiss = {
                showHealthConnectSettingsDialog = false
                currentStep = PermissionStep.COMPLETED
                hasCompletedOnboarding = true
                permPrefs.edit().putBoolean(permissionFlowKey, true).apply()
            },
            onOpenSettings = {
                openHealthConnectSettings(context)
                showHealthConnectSettingsDialog = false
            }
        )
    }
}

/**
 * 권한 체크 유틸리티 객체
 */
object PermissionChecker {
    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasForegroundLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasForegroundLocationPermission(context)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isHealthConnectAvailable(context: Context): Boolean {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    fun getHealthConnectPermissions(): Set<String> {
        return setOf(
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_DISTANCE"
        )
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 정확한 알람 권한 확인 (Android 12+)
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 정확한 알람 권한 설정 화면 열기 (Android 12+)
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
