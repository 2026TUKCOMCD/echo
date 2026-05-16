package com.example.graduation_project.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.location.LocationManager
import com.example.graduation_project.data.model.WeatherResponse
import com.example.graduation_project.data.repository.UserRepository
import com.example.graduation_project.data.repository.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeUiState(
    val userName: String = "",
    val conversationTime: String? = null,
    val date: String = "",
    val weather: WeatherResponse? = null,
    val isLoading: Boolean = true
)

class HomeViewModel(
    application: Application,
    private val userRepository: UserRepository = UserRepository(),
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val locationManager: LocationManager = LocationManager(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState(date = formatToday()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userDeferred = async { loadUserPreferences() }
            val weatherDeferred = async { loadWeather() }
            userDeferred.await()
            weatherDeferred.await()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun loadUserPreferences() {
        when (val result = userRepository.getPreferences()) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    userName = result.data.name ?: "",
                    conversationTime = result.data.conversationTime
                )
            }
            is ApiResult.Error -> Unit
        }
    }

    private suspend fun loadWeather() {
        val location = locationManager.getCurrentLocation() ?: return
        when (val result = weatherRepository.getWeather(location.latitude, location.longitude)) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(weather = result.data)
            }
            is ApiResult.Error -> Unit
        }
    }

    companion object {
        fun Factory(application: Application) = viewModelFactory {
            initializer { HomeViewModel(application) }
        }

        private fun formatToday(): String {
            val formatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
            return LocalDate.now().format(formatter)
        }
    }
}
