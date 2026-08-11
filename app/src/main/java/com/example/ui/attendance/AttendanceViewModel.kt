package com.example.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.AttendanceRecord
import com.example.data.entity.AppSettings
import com.example.data.entity.EventType
import com.example.data.entity.Student
import com.example.repository.AppRepository
import com.example.repository.AttendanceSummary
import com.example.repository.PendingSendStats
import com.example.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentAttendanceItem(
    val student: Student,
    val isPresent: Boolean
)

data class AttendanceUiState(
    val dateFormatted: String = "",
    val items: List<StudentAttendanceItem> = emptyList(),
    val summary: AttendanceSummary = AttendanceSummary(),
    val settings: AppSettings = AppSettings(),
    val searchQuery: String = "",
    val isConfirmDialogOpen: Boolean = false,
    val confirmEventType: EventType = EventType.ARRIVAL,
    val pendingSendStats: PendingSendStats? = null,
    val toastMessage: String? = null
)

class AttendanceViewModel(private val repository: AppRepository) : ViewModel() {

    private val dateIso = DateUtils.getTodayDateIso()
    private val dateFormatted = DateUtils.getTodayFormattedFa()

    private val searchQuery = MutableStateFlow("")
    private val isConfirmDialogOpen = MutableStateFlow(false)
    private val confirmEventType = MutableStateFlow(EventType.ARRIVAL)
    private val pendingStats = MutableStateFlow<PendingSendStats?>(null)
    private val toastMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            try {
                repository.syncWithCloudIfAvailable()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val students = repository.allActiveStudents.firstOrNull() ?: emptyList()
            if (students.isNotEmpty()) {
                repository.markAllDefaultPresent(dateIso, students)
            }
        }
    }

    val uiState: StateFlow<AttendanceUiState> = combine(
        repository.allActiveStudents,
        repository.getAttendanceForDate(dateIso),
        repository.appSettingsFlow,
        searchQuery,
        isConfirmDialogOpen,
        confirmEventType,
        pendingStats,
        toastMessage
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val students = flows[0] as List<Student>
        @Suppress("UNCHECKED_CAST")
        val records = flows[1] as List<AttendanceRecord>
        val settings = flows[2] as AppSettings
        val query = flows[3] as String
        val dialogOpen = flows[4] as Boolean
        val eventType = flows[5] as EventType
        val stats = flows[6] as PendingSendStats?
        val toast = flows[7] as String?

        val recordMap = records.associateBy { it.studentId }

        val items = students.map { student ->
            val record = recordMap[student.id]
            StudentAttendanceItem(
                student = student,
                isPresent = record?.isPresent ?: true
            )
        }.filter {
            if (query.isBlank()) true
            else it.student.name.contains(query.trim(), ignoreCase = true) ||
                    it.student.fatherName.contains(query.trim(), ignoreCase = true) ||
                    it.student.studentCode.contains(query.trim(), ignoreCase = true)
        }

        val total = students.size
        val presentCount = students.count { recordMap[it.id]?.isPresent ?: true }
        val absentCount = total - presentCount

        AttendanceUiState(
            dateFormatted = dateFormatted,
            items = items,
            summary = AttendanceSummary(totalStudents = total, presentCount = presentCount, absentCount = absentCount),
            settings = settings,
            searchQuery = query,
            isConfirmDialogOpen = dialogOpen,
            confirmEventType = eventType,
            pendingSendStats = stats,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AttendanceUiState(dateFormatted = dateFormatted)
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleAttendance(studentId: Long, currentIsPresent: Boolean) {
        viewModelScope.launch {
            try {
                repository.setAttendanceStatus(studentId, dateIso, !currentIsPresent)
            } catch (e: Exception) {
                toastMessage.value = e.localizedMessage ?: "خطایی رخ داد."
            }
        }
    }

    fun openSendConfirmation(eventType: EventType) {
        viewModelScope.launch {
            val stats = repository.getPendingSendStats(dateIso, eventType)
            pendingStats.value = stats
            confirmEventType.value = eventType
            isConfirmDialogOpen.value = true
        }
    }

    fun dismissConfirmationDialog() {
        isConfirmDialogOpen.value = false
        pendingStats.value = null
    }

    fun confirmAndSend() {
        val eventType = confirmEventType.value
        viewModelScope.launch {
            val queuedCount = repository.queueAttendanceMessages(dateIso, eventType)
            isConfirmDialogOpen.value = false
            pendingStats.value = null
            toastMessage.value = if (queuedCount > 0) {
                "تعداد $queuedCount پیام وارد صف ارسال شد."
            } else {
                "هیچ پیام جدیدی برای ارسال وجود نداشت یا قبلاً ارسال شده است."
            }
        }
    }

    fun clearToast() {
        toastMessage.value = null
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AttendanceViewModel(repository) as T
        }
    }
}
