package com.example.graduation_project.presentation.permission

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.graduation_project.domain.permission.PermissionState

/**
 * 위치 권한 상태를 관리하는 ViewModel
 */
class LocationPermissionViewModel : ViewModel() {

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    /**
     * 현재 권한 상태 확인 및 업데이트
     */
    fun checkPermissionStatus(context: Context) {
        _permissionState.value = checkLocationPermission(context)
    }

    /**
     * 권한 요청 결과 처리
     */
    fun onPermissionResult(state: PermissionState) {
        _permissionState.value = state
        _showPermissionDialog.value = false

        when (state) {
            is PermissionState.PermanentlyDenied -> {
                _showSettingsDialog.value = true
            }
            else -> Unit
        }
    }

    /**
     * 권한 요청 다이얼로그 표시
     */
    fun showPermissionRequestDialog() {
        _showPermissionDialog.value = true
    }

    /**
     * 권한 요청 다이얼로그 닫기
     */
    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    /**
     * 설정 다이얼로그 닫기
     */
    fun dismissSettingsDialog() {
        _showSettingsDialog.value = false
    }

    /**
     * 권한이 부여되었는지 확인
     */
    fun isPermissionGranted(): Boolean {
        return _permissionState.value is PermissionState.Granted
    }

    /**
     * 권한 요청이 필요한지 확인
     */
    fun needsPermissionRequest(): Boolean {
        return _permissionState.value is PermissionState.NotRequested ||
            _permissionState.value is PermissionState.Denied
    }
}
