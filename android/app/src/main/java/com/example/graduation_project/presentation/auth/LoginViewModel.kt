package com.example.graduation_project.presentation.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loginId: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isLoginSuccess: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuthRepository = AuthRepository(
        tokenStorage = TokenStorage(application)
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateLoginId(value: String) {
        _uiState.update { it.copy(loginId = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun login() {
        val state = _uiState.value
        if (state.loginId.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "아이디와 비밀번호를 입력해주세요") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = repository.login(state.loginId, state.password)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapErrorMessage(result.exception)
                        )
                    }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun mapErrorMessage(exception: ApiException): String = when (exception) {
        is ApiException.ClientError -> when (exception.code) {
            401 -> "아이디 또는 비밀번호가 올바르지 않습니다"
            else -> exception.message
        }
        is ApiException.NetworkError -> "인터넷 연결을 확인해주세요"
        is ApiException.ServerError -> "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요"
        is ApiException.UnknownError -> "알 수 없는 오류가 발생했습니다"
    }
}
