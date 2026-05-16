package com.example.graduation_project.presentation.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignupUiState(
    val loginId: String = "",
    val password: String = "",
    val name: String = "",
    val loginIdError: String? = null,
    val passwordError: String? = null,
    val nameError: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isSignupSuccess: Boolean = false
)

class SignupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuthRepository = AuthRepository(
        tokenStorage = TokenStorage(application)
    )

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun updateLoginId(value: String) {
        _uiState.update { it.copy(loginId = value, loginIdError = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null) }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, nameError = null) }
    }

    fun signup() {
        val state = _uiState.value
        val loginIdError = validateLoginId(state.loginId)
        val passwordError = validatePassword(state.password)
        val nameError = validateName(state.name)

        if (loginIdError != null || passwordError != null || nameError != null) {
            _uiState.update {
                it.copy(
                    loginIdError = loginIdError,
                    passwordError = passwordError,
                    nameError = nameError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = repository.signup(state.loginId, state.password, state.name)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSignupSuccess = true) }
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
            409 -> "이미 사용 중인 아이디입니다"
            400 -> "입력 형식을 확인해주세요"
            else -> exception.message
        }
        is ApiException.NetworkError -> "인터넷 연결을 확인해주세요"
        is ApiException.ServerError -> "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요"
        is ApiException.UnknownError -> "알 수 없는 오류가 발생했습니다"
    }

    private fun validateLoginId(value: String): String? = when {
        value.isBlank() -> "아이디를 입력해주세요"
        !value.matches(Regex("^[a-zA-Z0-9]{4,20}$")) -> "영문/숫자 4~20자로 입력해주세요"
        else -> null
    }

    private fun validatePassword(value: String): String? = when {
        value.isBlank() -> "비밀번호를 입력해주세요"
        !value.matches(Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) ->
            "8자 이상, 영문/숫자 조합으로 입력해주세요"
        else -> null
    }

    private fun validateName(value: String): String? = when {
        value.isBlank() -> "이름을 입력해주세요"
        value.length > 50 -> "이름은 50자 이하로 입력해주세요"
        else -> null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return SignupViewModel(application) as T
            }
        }
    }
}
