package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auth.SupabaseAuthManager
import com.example.auth.TeacherProfile
import com.example.data.entity.AppSettings
import com.example.data.entity.NotificationTarget
import com.example.data.entity.SchoolClass
import com.example.repository.AppRepository
import com.example.sms.SimInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: AppRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val schoolClasses: StateFlow<List<SchoolClass>> = repository.allSchoolClassesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val toastMessage = MutableStateFlow<String?>(null)
    val toastMessageFlow: StateFlow<String?> = toastMessage

    private val _teachersList = MutableStateFlow<List<TeacherProfile>>(emptyList())
    val teachersList: StateFlow<List<TeacherProfile>> = _teachersList.asStateFlow()

    private val _isLoadingTeachers = MutableStateFlow(false)
    val isLoadingTeachers: StateFlow<Boolean> = _isLoadingTeachers.asStateFlow()

    fun loadTeachers(authManager: SupabaseAuthManager, schoolCode: String) {
        if (schoolCode.isBlank()) return
        viewModelScope.launch {
            _isLoadingTeachers.value = true
            val res = authManager.fetchTeachersForManager(schoolCode)
            res.onSuccess { list ->
                _teachersList.value = list
            }.onFailure { err ->
                toastMessage.value = err.localizedMessage ?: "خطا در دریافت لیست معلمان"
            }
            _isLoadingTeachers.value = false
        }
    }

    fun toggleTeacherApproval(authManager: SupabaseAuthManager, teacherId: String, currentStatus: Boolean, schoolCode: String) {
        viewModelScope.launch {
            _isLoadingTeachers.value = true
            val res = authManager.updateTeacherApproval(teacherId, !currentStatus)
            res.onSuccess {
                toastMessage.value = if (!currentStatus) "معلم با موفقیت تأیید شد." else "معلم غیرفعال شد."
                loadTeachers(authManager, schoolCode)
            }.onFailure { err ->
                toastMessage.value = err.localizedMessage ?: "خطا در تغییر وضعیت معلم"
                _isLoadingTeachers.value = false
            }
        }
    }

    fun deleteTeacherRequest(authManager: SupabaseAuthManager, teacherId: String, schoolCode: String) {
        viewModelScope.launch {
            _isLoadingTeachers.value = true
            val res = authManager.deleteTeacher(teacherId)
            res.onSuccess {
                toastMessage.value = "درخواست معلم با موفقیت حذف شد."
                loadTeachers(authManager, schoolCode)
            }.onFailure { err ->
                toastMessage.value = err.localizedMessage ?: "خطا در حذف درخواست معلم"
                _isLoadingTeachers.value = false
            }
        }
    }

    fun updateEnableSms(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(enableSms = enabled))
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

    fun updateSelectedSubId(subId: Int) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(selectedSubId = subId))
        }
    }

    fun updatePacingDelayMs(delayMs: Long) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(pacingDelayMs = delayMs))
        }
    }

    fun updateAutoGenerateStudentCode(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(autoGenerateStudentCode = enabled))
        }
    }

    fun updateEnableCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(enableCloudSync = enabled))
        }
    }

    fun addSchoolClass(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val currentList = repository.getAllSchoolClasses()
            if (currentList.any { it.name.trim().equals(name.trim(), ignoreCase = true) }) {
                toastMessage.value = "صنفی با این نام قبلاً ثبت شده است."
                return@launch
            }
            val maxSort = currentList.maxOfOrNull { it.sortOrder } ?: 0
            val newClass = SchoolClass(name = name.trim(), sortOrder = maxSort + 1)
            repository.insertSchoolClass(newClass)
            toastMessage.value = "صنف «${name.trim()}» با موفقیت افزوده شد."
        }
    }

    fun updateSchoolClass(schoolClass: SchoolClass) {
        if (schoolClass.name.isBlank()) return
        viewModelScope.launch {
            val currentList = repository.getAllSchoolClasses()
            val oldClass = currentList.find { it.id == schoolClass.id }
            if (currentList.any { it.id != schoolClass.id && it.name.trim().equals(schoolClass.name.trim(), ignoreCase = true) }) {
                toastMessage.value = "صنفی با این نام قبلاً وجود دارد."
                return@launch
            }
            if (oldClass != null && oldClass.name != schoolClass.name.trim()) {
                repository.updateClassNameForStudents(oldClass.name, schoolClass.name.trim())
            }
            repository.updateSchoolClass(schoolClass.copy(name = schoolClass.name.trim()))
            toastMessage.value = "صنف با موفقیت ویرایش شد."
        }
    }

    fun deleteSchoolClass(schoolClass: SchoolClass) {
        viewModelScope.launch {
            val studentCount = repository.getActiveStudentCountForClass(schoolClass.name)
            if (studentCount > 0) {
                toastMessage.value = "این صنف دارای $studentCount دانش‌آموز فعال است. امکان حذف وجود ندارد."
                return@launch
            }
            repository.deleteSchoolClass(schoolClass.id)
            toastMessage.value = "صنف با موفقیت حذف شد."
        }
    }

    fun getAvailableSims(): List<SimInfo> {
        return repository.getAvailableSims()
    }

    fun exportBackup(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val jsonStr = repository.exportBackupJson()
            onExportReady(jsonStr)
            toastMessage.value = "فایل پشتیبان آماده شد."
        }
    }

    fun importBackup(jsonStr: String, authManager: SupabaseAuthManager, schoolCode: String) {
        viewModelScope.launch {
            val result = repository.restoreBackupWithInternetCheck(jsonStr, schoolCode, authManager)
            result.onSuccess { msg ->
                toastMessage.value = msg
            }.onFailure { err ->
                toastMessage.value = err.localizedMessage ?: "خطا در بازیابی پشتیبان."
            }
        }
    }

    fun syncCloud(authManager: SupabaseAuthManager, schoolCode: String) {
        viewModelScope.launch {
            val res = repository.syncManagerCloudRestore(schoolCode, authManager)
            toastMessage.value = res.getOrDefault("همگام‌سازی ابری انجام شد.")
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
