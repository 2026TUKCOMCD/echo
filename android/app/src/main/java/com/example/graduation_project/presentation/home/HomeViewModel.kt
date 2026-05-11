package com.example.graduation_project.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserName()
    }

    private fun loadUserName() {
        viewModelScope.launch {
            when (val result = userRepository.getPreferences()) {
                is ApiResult.Success -> {
                    _uiState.value = HomeUiState(
                        userName = result.data.name ?: "",
                        isLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = HomeUiState(isLoading = false)
                }
            }
        }
    }
}
