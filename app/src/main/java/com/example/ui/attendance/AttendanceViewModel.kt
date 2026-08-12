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

enum class AttendanceStatusFilter {
    ALL,
    PRESENT,
    ABSENT
}

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
    val selectedGrade: String = "همه صنف‌ها",
    val availableGrades: List<String> = listOf("همه صنف‌ها"),
    val statusFilter: AttendanceStatusFilter = AttendanceStatusFilter.ALL,
    val isConfirmDialogOpen: Boolean = false,
    val confirmEventType: EventType = EventType.ARRIVAL,
    val pendingSendStats: PendingSendStats? = null,
    val isSyncing: Boolean = false,
    val toastMessage: String? = null
)

class AttendanceViewModel(private val repository: AppRepository) : ViewModel() {

    private val dateIso = DateUtils.getTodayDateIso()
    private val dateFormatted = DateUtils.getTodayFormattedFa()

    private val searchQuery = MutableStateFlow("")
    private val selectedGrade = MutableStateFlow("همه صنف‌ها")
    private val statusFilter = MutableStateFlow(AttendanceStatusFilter.ALL)
    private val isConfirmDialogOpen = MutableStateFlow(false)
    private val confirmEventType = MutableStateFlow(EventType.ARRIVAL)
    private val pendingStats = MutableStateFlow<PendingSendStats?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val toastMessage = MutableStateFlow<String?>(null)

    init {
        // Initial setup
        viewModelScope.launch {
            val students = repository.allActiveStudents.firstOrNull() ?: emptyList()
            if (students.isNotEmpty()) {
                repository.markAllDefaultPresent(dateIso, students)
            }
        }
        // Real-time periodic cloud polling loop every 5 seconds
        viewModelScope.launch {
            while (true) {
                try {
                    isSyncing.value = true
                    repository.syncWithCloudIfAvailable()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSyncing.value = false
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            try {
                isSyncing.value = true
                repository.syncWithCloudIfAvailable()
                toastMessage.value = "اطلاعات بروزرسانی شد."
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSyncing.value = false
            }
        }
    }

    val uiState: StateFlow<AttendanceUiState> = combine(
        repository.allActiveStudents,
        repository.getAttendanceForDate(dateIso),
        repository.appSettingsFlow,
        searchQuery,
        selectedGrade,
        statusFilter,
        isConfirmDialogOpen,
        confirmEventType,
        pendingStats,
        isSyncing,
        toastMessage
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val students = flows[0] as List<Student>
        @Suppress("UNCHECKED_CAST")
        val records = flows[1] as List<AttendanceRecord>
        val settings = flows[2] as AppSettings
        val query = flows[3] as String
        val gradeSel = flows[4] as String
        val statusFilt = flows[5] as AttendanceStatusFilter
        val dialogOpen = flows[6] as Boolean
        val eventType = flows[7] as EventType
        val stats = flows[8] as PendingSendStats?
        val syncing = flows[9] as Boolean
        val toast = flows[10] as String?

        val recordMap = records.associateBy { it.studentId }

        val gradesList = listOf("همه صنف‌ها") + students.map { it.grade }.filter { it.isNotBlank() }.distinct().sorted()

        val allItems = students.map { student ->
            val record = recordMap[student.id]
            StudentAttendanceItem(
                student = student,
                isPresent = record?.isPresent ?: true
            )
        }

        val filteredItems = allItems.filter { item ->
            val matchGrade = if (gradeSel == "همه صنف‌ها") true else item.student.grade == gradeSel
            val matchStatus = when (statusFilt) {
                AttendanceStatusFilter.ALL -> true
                AttendanceStatusFilter.PRESENT -> item.isPresent
                AttendanceStatusFilter.ABSENT -> !item.isPresent
            }
            val matchQuery = if (query.isBlank()) true else (
                item.student.name.contains(query.trim(), ignoreCase = true) ||
                item.student.fatherName.contains(query.trim(), ignoreCase = true) ||
                item.student.studentCode.contains(query.trim(), ignoreCase = true)
            )

            matchGrade && matchStatus && matchQuery
        }

        val total = students.size
        val presentCount = students.count { recordMap[it.id]?.isPresent ?: true }
        val absentCount = total - presentCount

        AttendanceUiState(
            dateFormatted = dateFormatted,
            items = filteredItems,
            summary = AttendanceSummary(totalStudents = total, presentCount = presentCount, absentCount = absentCount),
            settings = settings,
            searchQuery = query,
            selectedGrade = gradeSel,
            availableGrades = gradesList,
            statusFilter = statusFilt,
            isConfirmDialogOpen = dialogOpen,
            confirmEventType = eventType,
            pendingSendStats = stats,
            isSyncing = syncing,
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

    fun selectGrade(grade: String) {
        selectedGrade.value = grade
    }

    fun toggleStatusFilter(filter: AttendanceStatusFilter) {
        if (statusFilter.value == filter) {
            statusFilter.value = AttendanceStatusFilter.ALL
        } else {
            statusFilter.value = filter
        }
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
            val gradeFilter = selectedGrade.value
            val stats = repository.getPendingSendStats(dateIso, eventType, gradeFilter)
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
            val gradeFilter = selectedGrade.value
            val queuedCount = repository.queueAttendanceMessages(dateIso, eventType, gradeFilter)
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
