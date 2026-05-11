package com.example.graduation_project.presentation.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import com.example.graduation_project.domain.permission.PermissionState

/**
 * 백그라운드 위치 권한 상태
 */
sealed class BackgroundLocationState {
    /** 권한 요청 전 */
    object NotRequested : BackgroundLocationState()

    /** 전경 위치 권한 필요 (먼저 전경 권한을 받아야 함) */
    object NeedsForegroundFirst : BackgroundLocationState()

    /** 백그라운드 권한 허용됨 */
    object Granted : BackgroundLocationState()

    /** 백그라운드 권한 거부됨 */
    object Denied : BackgroundLocationState()

    /** 영구 거부됨 (설정에서 변경 필요) */
    object PermanentlyDenied : BackgroundLocationState()
}

/**
 * 백그라운드 위치 권한 처리를 위한 Composable
 *
 * Android 11+에서는 전경 위치 권한을 먼저 받은 후에만 백그라운드 권한 요청 가능
 *
 * @param onPermissionResult 권한 결과 콜백
 * @param content 권한 상태에 따른 UI 컨텐츠
 */
@Composable
fun BackgroundLocationPermissionHandler(
    onPermissionResult: (BackgroundLocationState) -> Unit,
    content: @Composable (
        state: BackgroundLocationState,
        requestForegroundPermission: () -> Unit,
        requestBackgroundPermission: () -> Unit,
        openSettings: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(checkBackgroundLocationState(context)) }
    var hasRequestedBackground by remember { mutableStateOf(false) }

    // 전경 위치 권한 요청 런처
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        state = if (fineGranted || coarseGranted) {
            // 전경 권한 받음 → 백그라운드 권한 상태 다시 확인
            checkBackgroundLocationState(context)
        } else {
            BackgroundLocationState.NeedsForegroundFirst
        }
        onPermissionResult(state)
    }

    // 백그라운드 위치 권한 요청 런처 (Android 10+)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        state = when {
            isGranted -> BackgroundLocationState.Granted
            !shouldShowBackgroundRationale(context) && hasRequestedBackground ->
                BackgroundLocationState.PermanentlyDenied
            else -> BackgroundLocationState.Denied
        }
        hasRequestedBackground = true
        onPermissionResult(state)
    }

    val requestForegroundPermission: () -> Unit = {
        foregroundPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val requestBackgroundPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val openSettings: () -> Unit = {
        openAppSettings(context)
    }

    LaunchedEffect(state) {
        onPermissionResult(state)
    }

    content(state, requestForegroundPermission, requestBackgroundPermission, openSettings)
}

/**
 * 백그라운드 위치 권한 상태 확인
 */
fun checkBackgroundLocationState(context: Context): BackgroundLocationState {
    // 먼저 전경 위치 권한 확인
    val hasForeground = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasForeground) {
        return BackgroundLocationState.NeedsForegroundFirst
    }

    // Android 9 이하는 전경 권한만 있으면 백그라운드도 허용
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return BackgroundLocationState.Granted
    }

    // Android 10+: 백그라운드 권한 별도 확인
    val hasBackground = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return if (hasBackground) {
        BackgroundLocationState.Granted
    } else {
        BackgroundLocationState.NotRequested
    }
}

/**
 * 백그라운드 위치 권한이 허용되었는지 확인
 */
fun hasBackgroundLocationPermission(context: Context): Boolean {
    return checkBackgroundLocationState(context) == BackgroundLocationState.Granted
}

/**
 * 백그라운드 권한 rationale 표시 여부 확인
 */
private fun shouldShowBackgroundRationale(context: Context): Boolean {
    return if (context is android.app.Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        true
    }
}
