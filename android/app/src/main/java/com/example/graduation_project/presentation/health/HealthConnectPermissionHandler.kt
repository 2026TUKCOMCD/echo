package com.example.graduation_project.presentation.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.domain.health.HealthConnectAvailability

/**
 * Health Connect 권한 처리를 위한 HOC (Higher-Order Composable)
 *
 * - 앱 시작 시 LaunchedEffect로 availability/permissionState에 따라 적절한 다이얼로그 표시
 * - onResume 시 DisposableEffect로 refreshPermissions() 호출
 * - content()는 권한 상태와 무관하게 항상 렌더링 (graceful degradation)
 */
@Composable
fun HealthConnectPermissionHandler(
    viewModel: HealthViewModel = viewModel(factory = HealthViewModel.Factory),
    content: @Composable () -> Unit
) {
    var showRationale by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    var showNotInstalled by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    // 앱 시작 시 자동 다이얼로그 표시
    LaunchedEffect(uiState.availability, uiState.permissionState, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        when {
            uiState.availability is HealthConnectAvailability.NotInstalled ->
                showNotInstalled = true
            uiState.availability is HealthConnectAvailability.Available
                    && uiState.permissionState is PermissionState.NotRequested ->
                showRationale = true
            uiState.availability is HealthConnectAvailability.Available
                    && uiState.permissionState is PermissionState.Denied ->
                showDenied = true
        }
    }

    // onResume 시 권한 재확인
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 다이얼로그 표시
    if (showRationale) {
        HealthPermissionRationaleDialog(
            onConfirm = {
                permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                showRationale = false
            },
            onDismiss = { showRationale = false }
        )
    }

    if (showDenied) {
        HealthPermissionDeniedDialog(
            onOpenSettings = {
                openHealthConnectSettings(context)
                showDenied = false
            },
            onDismiss = { showDenied = false }
        )
    }

    if (showNotInstalled) {
        val healthConnectManager = remember { HealthConnectManager(context) }
        HealthConnectNotInstalledDialog(
            onInstall = {
                healthConnectManager.openPlayStoreForHealthConnect()
                showNotInstalled = false
            },
            onDismiss = { showNotInstalled = false }
        )
    }

    // graceful degradation: 항상 content 렌더링
    content()
}
