package com.example.graduation_project.presentation.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.domain.health.HealthConnectAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class PermissionState {
    data object NotRequested : PermissionState()
    data object Granted : PermissionState()
    data object Denied : PermissionState()
}

data class HealthUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.NotSupported,
    val permissionState: PermissionState = PermissionState.NotRequested,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isHealthConnectReady: Boolean
        get() = availability is HealthConnectAvailability.Available
                && permissionState is PermissionState.Granted
}

class HealthViewModel(
    application: Application,
    private val healthConnectManager: HealthConnectManager = HealthConnectManager(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        checkInitialState()
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val availability = healthConnectManager.checkAvailability()
            if (availability is HealthConnectAvailability.Available) {
                try {
                    val allGranted = healthConnectManager.checkGrantedPermissions()
                    _uiState.update {
                        it.copy(
                            availability = availability,
                            permissionState = if (allGranted) PermissionState.Granted else PermissionState.NotRequested
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            availability = availability,
                            errorMessage = "건강 데이터 권한을 확인할 수 없습니다"
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(availability = availability) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun checkPermissions() {
        try {
            val allGranted = healthConnectManager.checkGrantedPermissions()
            _uiState.update {
                it.copy(permissionState = if (allGranted) PermissionState.Granted else PermissionState.NotRequested)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "건강 데이터 권한을 확인할 수 없습니다") }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        val allGranted = grantedPermissions.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
        _uiState.update {
            it.copy(
                permissionState = if (allGranted) PermissionState.Granted else PermissionState.Denied
            )
        }
    }

    fun refreshPermissions() {
        if (_uiState.value.availability !is HealthConnectAvailability.Available) return
        viewModelScope.launch {
            checkPermissions()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return HealthViewModel(application) as T
            }
        }
    }
}
