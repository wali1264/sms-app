package com.example.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.EventType
import com.example.data.entity.MessageRecord
import com.example.data.entity.MessageStatus
import com.example.repository.AppRepository
import com.example.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MessageStatusFilter {
    ALL,
    SUCCESS,
    PENDING,
    SENDING,
    FAILED
}

enum class EventTypeFilter {
    ALL,
    ABSENCE,
    ARRIVAL,
    DEPARTURE
}

data class MessagesUiState(
    val messages: List<MessageRecord> = emptyList(),
    val filteredMessages: List<MessageRecord> = emptyList(),
    val activeStatusFilter: MessageStatusFilter = MessageStatusFilter.ALL,
    val activeEventTypeFilter: EventTypeFilter = EventTypeFilter.ALL,
    val selectedGrade: String = "همه صنف‌ها",
    val availableGrades: List<String> = listOf("همه صنف‌ها"),
    val selectedJalaliYear: Int = 1403,
    val selectedJalaliMonth: Int = 1,
    val selectedJalaliDay: Int = 1,
    val isToday: Boolean = true,
    val isDatePickerOpen: Boolean = false,
    val pendingCount: Int = 0,
    val sendingCount: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val absenceCount: Int = 0,
    val arrivalCount: Int = 0,
    val departureCount: Int = 0
)

class MessagesViewModel(private val repository: AppRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.autoPurgeOldMessages(30)
        }
    }

    private val activeStatusFilter = MutableStateFlow(MessageStatusFilter.ALL)
    private val activeEventTypeFilter = MutableStateFlow(EventTypeFilter.ALL)
    private val selectedGrade = MutableStateFlow("همه صنف‌ها")

    // Default to Today's Shamsi Date
    private val todayJalali = DateUtils.getTodayJalali()
    private val selectedJalaliYear = MutableStateFlow(todayJalali[0])
    private val selectedJalaliMonth = MutableStateFlow(todayJalali[1])
    private val selectedJalaliDay = MutableStateFlow(todayJalali[2])
    private val isDatePickerOpen = MutableStateFlow(false)

    private val dateSelectionFlow = combine(
        selectedJalaliYear,
        selectedJalaliMonth,
        selectedJalaliDay,
        isDatePickerOpen
    ) { y, m, d, isOpen -> DateSelection(y, m, d, isOpen) }

    private val filterParamsFlow = combine(
        activeStatusFilter,
        activeEventTypeFilter,
        selectedGrade,
        dateSelectionFlow
    ) { statusFilter, eventTypeFilter, grade, dateSel ->
        FilterParams(statusFilter, eventTypeFilter, grade, dateSel)
    }

    val uiState: StateFlow<MessagesUiState> = combine(
        repository.allMessagesFlow,
        repository.allActiveStudents,
        repository.allSchoolClassesFlow,
        filterParamsFlow
    ) { messagesList, studentsList, schoolClasses, filterParams ->

        val statusFilter = filterParams.statusFilter
        val eventTypeFilter = filterParams.eventTypeFilter
        val grade = filterParams.grade
        val dateSel = filterParams.dateSel

        val customGrades = schoolClasses.map { it.name }.filter { it.isNotBlank() }
        val studentGrades = studentsList.map { it.grade }.filter { it.isNotBlank() }
        val allGrades = (listOf("همه صنف‌ها") + customGrades + studentGrades).distinct()

        val studentGradeMap = studentsList.associate { it.id to it.grade }

        val selectedIso = DateUtils.jalaliToGregorianIso(dateSel.year, dateSel.month, dateSel.day)
        val todayIso = DateUtils.getTodayDateIso()
        val isToday = (selectedIso == todayIso)

        val dateFiltered = messagesList.filter { it.date == selectedIso }

        val gradeFiltered = if (grade != "همه صنف‌ها" && grade.isNotBlank()) {
            dateFiltered.filter { msg ->
                studentGradeMap[msg.studentId] == grade
            }
        } else {
            dateFiltered
        }

        val pending = gradeFiltered.count { it.status == MessageStatus.PENDING }
        val sending = gradeFiltered.count { it.status == MessageStatus.SENDING }
        val sent = gradeFiltered.count { it.status == MessageStatus.SENT || it.status == MessageStatus.DELIVERED }
        val failed = gradeFiltered.count { it.status in listOf(MessageStatus.FAILED_RETRYABLE, MessageStatus.FAILED_PERMANENT, MessageStatus.FAILED_UNKNOWN) }

        val absence = gradeFiltered.count { it.eventType == EventType.ABSENCE }
        val arrival = gradeFiltered.count { it.eventType == EventType.ARRIVAL }
        val departure = gradeFiltered.count { it.eventType == EventType.DEPARTURE }

        var resultList = gradeFiltered
        if (eventTypeFilter != EventTypeFilter.ALL) {
            resultList = resultList.filter { msg ->
                when (eventTypeFilter) {
                    EventTypeFilter.ABSENCE -> msg.eventType == EventType.ABSENCE
                    EventTypeFilter.ARRIVAL -> msg.eventType == EventType.ARRIVAL
                    EventTypeFilter.DEPARTURE -> msg.eventType == EventType.DEPARTURE
                    else -> true
                }
            }
        }

        if (statusFilter != MessageStatusFilter.ALL) {
            resultList = resultList.filter { msg ->
                when (statusFilter) {
                    MessageStatusFilter.SUCCESS -> msg.status == MessageStatus.SENT || msg.status == MessageStatus.DELIVERED
                    MessageStatusFilter.PENDING -> msg.status == MessageStatus.PENDING
                    MessageStatusFilter.SENDING -> msg.status == MessageStatus.SENDING
                    MessageStatusFilter.FAILED -> msg.status in listOf(MessageStatus.FAILED_RETRYABLE, MessageStatus.FAILED_PERMANENT, MessageStatus.FAILED_UNKNOWN)
                    else -> true
                }
            }
        }

        MessagesUiState(
            messages = gradeFiltered,
            filteredMessages = resultList,
            activeStatusFilter = statusFilter,
            activeEventTypeFilter = eventTypeFilter,
            selectedGrade = grade,
            availableGrades = allGrades,
            selectedJalaliYear = dateSel.year,
            selectedJalaliMonth = dateSel.month,
            selectedJalaliDay = dateSel.day,
            isToday = isToday,
            isDatePickerOpen = dateSel.isOpen,
            pendingCount = pending,
            sendingCount = sending,
            sentCount = sent,
            failedCount = failed,
            absenceCount = absence,
            arrivalCount = arrival,
            departureCount = departure
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MessagesUiState()
    )

    fun toggleStatusFilter(filter: MessageStatusFilter) {
        if (activeStatusFilter.value == filter) {
            activeStatusFilter.value = MessageStatusFilter.ALL
        } else {
            activeStatusFilter.value = filter
        }
    }

    fun toggleEventTypeFilter(filter: EventTypeFilter) {
        if (activeEventTypeFilter.value == filter) {
            activeEventTypeFilter.value = EventTypeFilter.ALL
        } else {
            activeEventTypeFilter.value = filter
        }
    }

    fun selectGrade(grade: String) {
        selectedGrade.value = grade
    }

    fun openDatePicker() {
        isDatePickerOpen.value = true
    }

    fun closeDatePicker() {
        isDatePickerOpen.value = false
    }

    fun setJalaliDate(month: Int, day: Int) {
        selectedJalaliMonth.value = month.coerceIn(1, 12)
        selectedJalaliDay.value = day.coerceIn(1, 31)
        isDatePickerOpen.value = false
    }

    fun resetToToday() {
        val t = DateUtils.getTodayJalali()
        selectedJalaliYear.value = t[0]
        selectedJalaliMonth.value = t[1]
        selectedJalaliDay.value = t[2]
        isDatePickerOpen.value = false
    }

    fun retryMessage(messageId: Long) {
        viewModelScope.launch {
            repository.retryMessage(messageId)
        }
    }

    fun retryAllFailedMessages() {
        viewModelScope.launch {
            repository.retryAllFailedMessages()
        }
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessagesViewModel(repository) as T
        }
    }
}

private data class DateSelection(
    val year: Int,
    val month: Int,
    val day: Int,
    val isOpen: Boolean
)

private data class FilterParams(
    val statusFilter: MessageStatusFilter,
    val eventTypeFilter: EventTypeFilter,
    val grade: String,
    val dateSel: DateSelection
)

