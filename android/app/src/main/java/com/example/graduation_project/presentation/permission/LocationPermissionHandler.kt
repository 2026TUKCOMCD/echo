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
 * 위치 권한 처리를 위한 Composable
 *
 * @param onPermissionResult 권한 결과 콜백
 * @param content 권한 상태에 따른 UI 컨텐츠
 */
@Composable
fun LocationPermissionHandler(
    onPermissionResult: (PermissionState) -> Unit,
    content: @Composable (
        permissionState: PermissionState,
        requestPermission: () -> Unit,
        openSettings: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(checkLocationPermission(context)) }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val isGranted = fineLocationGranted || coarseLocationGranted

        permissionState = when {
            isGranted -> PermissionState.Granted
            !shouldShowLocationPermissionRationale(context) && hasRequestedOnce -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
        hasRequestedOnce = true
        onPermissionResult(permissionState)
    }

    val requestPermission: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val openSettings: () -> Unit = {
        openAppSettings(context)
    }

    LaunchedEffect(permissionState) {
        onPermissionResult(permissionState)
    }

    content(permissionState, requestPermission, openSettings)
}

/**
 * 현재 위치 권한 상태 확인
 */
fun checkLocationPermission(context: Context): PermissionState {
    val fineLocationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseLocationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return if (fineLocationGranted || coarseLocationGranted) {
        PermissionState.Granted
    } else {
        PermissionState.NotRequested
    }
}

/**
 * 정밀 위치 권한이 허용되었는지 확인
 */
fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 대략적 위치 권한이 허용되었는지 확인
 */
fun hasCoarseLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 위치 권한 rationale 표시 여부 확인 (Activity에서 호출해야 함)
 * Composable 외부에서 사용 시 Activity 컨텍스트 필요
 */
private fun shouldShowLocationPermissionRationale(context: Context): Boolean {
    return if (context is android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                context.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            true
        }
    } else {
        true
    }
}
