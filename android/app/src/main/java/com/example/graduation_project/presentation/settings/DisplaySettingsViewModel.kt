package com.example.graduation_project.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.local.DisplaySettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DisplaySettings(
    val fontScale: Float = DisplaySettingsStorage.SCALE_MEDIUM,
    val isHighContrast: Boolean = false
)

class DisplaySettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = DisplaySettingsStorage(application)

    private val _settings = MutableStateFlow(
        DisplaySettings(
            fontScale = storage.fontScale,
            isHighContrast = storage.isHighContrast
        )
    )
    val settings: StateFlow<DisplaySettings> = _settings.asStateFlow()

    fun setFontScale(scale: Float) {
        storage.fontScale = scale
        _settings.update { it.copy(fontScale = scale) }
    }

    fun setHighContrast(enabled: Boolean) {
        storage.isHighContrast = enabled
        _settings.update { it.copy(isHighContrast = enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return DisplaySettingsViewModel(application) as T
            }
        }
    }
}
