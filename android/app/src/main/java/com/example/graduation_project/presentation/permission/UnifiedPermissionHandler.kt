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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.presentation.health.openHealthConnectSettings

private const val APP_STATE_PREFS = "app_state"
private const val PERMISSION_STATE_PREFS = "permission_state"
private const val KEY_PERMISSION_FLOW_COMPLETED = "permission_flow_completed"
private const val KEY_NEEDS_PERMISSION_RECHECK = "needs_permission_recheck"

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
    val appStatePrefs = context.getSharedPreferences(APP_STATE_PREFS, Context.MODE_PRIVATE)
    val permPrefs = context.getSharedPreferences(PERMISSION_STATE_PREFS, Context.MODE_PRIVATE)

    // 현재 권한 요청 단계
    var currentStep by remember { mutableStateOf(PermissionStep.INTRO) }

    // 거부된 권한 설정 다이얼로그 표시 상태
    var showMicSettingsDialog by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var showHealthConnectSettingsDialog by remember { mutableStateOf(false) }

    // 앱 업데이트/재설치(버전 코드 변경) 시 권한 재확인 필요 여부
    val needsRecheck = appStatePrefs.getBoolean(KEY_NEEDS_PERMISSION_RECHECK, false)
    // 이전에 권한 플로우를 완료한 적 있는지 (SharedPreferences에 영구 저장)
    val previouslyCompleted = permPrefs.getBoolean(KEY_PERMISSION_FLOW_COMPLETED, false)

    var hasCompletedOnboarding by remember {
        mutableStateOf(previouslyCompleted && !needsRecheck)
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
        if (granted) {
            LocationScheduler.enableLocationCollection(context)
        }
        currentStep = PermissionStep.NOTIFICATION
    }

    // 알림 권한 요청 런처 (Android 13+)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 알림 권한 허용 시 정확한 알람 권한도 요청
        if (granted && !PermissionChecker.hasExactAlarmPermission(context)) {
            PermissionChecker.openExactAlarmSettings(context)
        }
        currentStep = PermissionStep.HEALTH_CONNECT
    }

    // Health Connect 권한 요청 런처
    val healthConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        currentStep = PermissionStep.COMPLETED
        hasCompletedOnboarding = true
        permPrefs.edit().putBoolean(KEY_PERMISSION_FLOW_COMPLETED, true).apply()
        appStatePrefs.edit().putBoolean(KEY_NEEDS_PERMISSION_RECHECK, false).apply()
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
                    currentStep = PermissionStep.LOCATION_BACKGROUND
                } else {
                    foregroundLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
            PermissionStep.LOCATION_BACKGROUND -> {
                if (PermissionChecker.hasBackgroundLocationPermission(context)) {
                    LocationScheduler.enableLocationCollection(context)
                    currentStep = PermissionStep.NOTIFICATION
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    LocationScheduler.enableLocationCollection(context)
                    currentStep = PermissionStep.NOTIFICATION
                }
            }
            PermissionStep.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (PermissionChecker.hasNotificationPermission(context)) {
                        currentStep = PermissionStep.HEALTH_CONNECT
                    } else {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    currentStep = PermissionStep.HEALTH_CONNECT
                }
            }
            PermissionStep.HEALTH_CONNECT -> {
                if (PermissionChecker.isHealthConnectAvailable(context)) {
                    val permissions = PermissionChecker.getHealthConnectPermissions()
                    if (permissions.isNotEmpty()) {
                        healthConnectLauncher.launch(permissions.toTypedArray())
                    } else {
                        currentStep = PermissionStep.COMPLETED
                        hasCompletedOnboarding = true
                        permPrefs.edit().putBoolean(KEY_PERMISSION_FLOW_COMPLETED, true).apply()
                        appStatePrefs.edit().putBoolean(KEY_NEEDS_PERMISSION_RECHECK, false).apply()
                        onAllPermissionsHandled()
                    }
                } else {
                    currentStep = PermissionStep.COMPLETED
                    hasCompletedOnboarding = true
                    permPrefs.edit().putBoolean(KEY_PERMISSION_FLOW_COMPLETED, true).apply()
                    appStatePrefs.edit().putBoolean(KEY_NEEDS_PERMISSION_RECHECK, false).apply()
                    onAllPermissionsHandled()
                }
            }
            else -> { /* INTRO, COMPLETED - 별도 처리 */ }
        }
    }

    // 이미 온보딩 완료 또는 모든 필수 권한 있음
    if (hasCompletedOnboarding || currentStep == PermissionStep.COMPLETED) {
        // 마이크 권한 체크 (필수)
        if (!PermissionChecker.hasMicrophonePermission(context)) {
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
                permPrefs.edit().putBoolean(KEY_PERMISSION_FLOW_COMPLETED, true).apply()
                appStatePrefs.edit().putBoolean(KEY_NEEDS_PERMISSION_RECHECK, false).apply()
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
