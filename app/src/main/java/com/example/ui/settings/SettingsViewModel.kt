package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.AppSettings
import com.example.data.entity.NotificationTarget
import com.example.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: AppRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val toastMessage = MutableStateFlow<String?>(null)
    val toastMessageFlow: StateFlow<String?> = toastMessage

    fun updateEnableSms(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(enableSms = enabled))
        }
    }

    fun updateEnableWhatsapp(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(enableWhatsapp = enabled))
        }
    }

    fun updateNotificationTarget(target: NotificationTarget) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(notificationTarget = target))
        }
    }

    fun updateEnableDeparture(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(enableDeparture = enabled))
        }
    }

    fun updateAbsenceTemplate(text: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(absenceTemplate = text))
        }
    }

    fun updateArrivalTemplate(text: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(arrivalTemplate = text))
        }
    }

    fun updateDepartureTemplate(text: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(departureTemplate = text))
        }
    }

    fun saveAll() {
        toastMessage.value = "تنظیمات با موفقیت ذخیره شدند."
    }

    fun clearToast() {
        toastMessage.value = null
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
