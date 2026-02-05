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
import com.example.graduation_project.domain.permission.PermissionState

/**
 * 마이크 권한 처리를 위한 Composable
 *
 * @param onPermissionResult 권한 결과 콜백
 * @param content 권한 상태에 따른 UI 컨텐츠
 */
@Composable
fun MicrophonePermissionHandler(
    onPermissionResult: (PermissionState) -> Unit,
    content: @Composable (
        permissionState: PermissionState,
        requestPermission: () -> Unit,
        openSettings: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(checkMicrophonePermission(context)) }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState = when {
            isGranted -> PermissionState.Granted
            !shouldShowPermissionRationale(context) && hasRequestedOnce -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
        hasRequestedOnce = true
        onPermissionResult(permissionState)
    }

    val requestPermission: () -> Unit = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
 * 현재 마이크 권한 상태 확인
 */
fun checkMicrophonePermission(context: Context): PermissionState {
    return if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        PermissionState.Granted
    } else {
        PermissionState.NotRequested
    }
}

/**
 * 권한 rationale 표시 여부 확인 (Activity에서 호출해야 함)
 * Composable 외부에서 사용 시 Activity 컨텍스트 필요
 */
private fun shouldShowPermissionRationale(context: Context): Boolean {
    return if (context is android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        } else {
            true
        }
    } else {
        true
    }
}

/**
 * 앱 설정 화면 열기
 */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * Android 13+ (API 33+) 대응 여부 확인
 */
fun isAndroid13OrAbove(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
