package com.example.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.dao.AttendanceDao
import com.example.data.dao.MessageDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.StudentDao
import com.example.data.entity.AppSettings
import com.example.data.entity.AttendanceRecord
import com.example.data.entity.EventType
import com.example.data.entity.MessageRecord
import com.example.data.entity.MessageStatus
import com.example.data.entity.NotificationTarget
import com.example.data.entity.SendChannel
import com.example.data.entity.Student
import com.example.sms.SmsResult
import com.example.sms.SmsSender
import com.example.worker.SendMessageWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AttendanceSummary(
    val totalStudents: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0
)

data class PendingSendStats(
    val presentCount: Int,
    val absentCount: Int,
    val smsCount: Int,
    val whatsappCount: Int,
    val targetStudentsCount: Int
)

class AppRepository(
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    private val messageDao: MessageDao,
    private val settingsDao: SettingsDao,
    private val smsSender: SmsSender,
    private val context: Context
) {

    // --- Students ---
    val allActiveStudents: Flow<List<Student>> = studentDao.getAllActiveStudents()

    fun searchStudents(query: String): Flow<List<Student>> {
        return if (query.isBlank()) {
            studentDao.getAllActiveStudents()
        } else {
            studentDao.searchStudents(query.trim())
        }
    }

    suspend fun getStudentById(id: Long): Student? = studentDao.getStudentById(id)

    suspend fun insertStudent(student: Student): Long = studentDao.insertStudent(student)

    suspend fun updateStudent(student: Student) = studentDao.updateStudent(student)

    suspend fun deleteStudent(id: Long) = studentDao.softDeleteStudent(id)

    // --- Settings ---
    val appSettingsFlow: Flow<AppSettings> = settingsDao.getSettingsFlow().map { it ?: AppSettings() }

    suspend fun getSettings(): AppSettings = settingsDao.getSettings() ?: AppSettings()

    suspend fun saveSettings(settings: AppSettings) = settingsDao.saveSettings(settings)

    // --- Attendance ---
    fun getAttendanceForDate(dateStr: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForDate(dateStr)
    }

    suspend fun setAttendanceStatus(studentId: Long, dateStr: String, isPresent: Boolean) {
        val existing = attendanceDao.getStudentAttendanceForDate(studentId, dateStr)
        val record = existing?.copy(isPresent = isPresent, updatedAt = System.currentTimeMillis())
            ?: AttendanceRecord(studentId = studentId, date = dateStr, isPresent = isPresent)
        attendanceDao.insertOrUpdateAttendance(record)
    }

    suspend fun markAllDefaultPresent(dateStr: String, students: List<Student>) {
        val existing = attendanceDao.getAttendanceListForDate(dateStr).associateBy { it.studentId }
        val newRecords = students.map { student ->
            existing[student.id] ?: AttendanceRecord(studentId = student.id, date = dateStr, isPresent = true)
        }
        attendanceDao.insertOrUpdateAll(newRecords)
    }

    // --- Messages ---
    val allMessagesFlow: Flow<List<MessageRecord>> = messageDao.getAllMessages()

    suspend fun getPendingSendStats(dateStr: String, eventType: EventType): PendingSendStats {
        val students = studentDao.getAllActiveStudents().firstOrNull() ?: emptyList()
        val attendanceList = attendanceDao.getAttendanceListForDate(dateStr).associateBy { it.studentId }
        val settings = getSettings()

        var smsCount = 0
        var whatsappCount = 0
        var targetStudents = 0

        var presentCount = 0
        var absentCount = 0

        students.forEach { student ->
            val isPresent = attendanceList[student.id]?.isPresent ?: true
            if (isPresent) presentCount++ else absentCount++

            val isTarget = when (eventType) {
                EventType.DEPARTURE -> isPresent // Only present students get departure msg
                else -> when (settings.notificationTarget) {
                    NotificationTarget.ABSENT_ONLY -> !isPresent
                    NotificationTarget.PRESENT_ONLY -> isPresent
                    NotificationTarget.BOTH -> true
                }
            }

            if (isTarget) {
                targetStudents++
                if (settings.enableSms && student.smsPhone.isNotBlank()) smsCount++
                if (settings.enableWhatsapp && student.whatsappPhone.isNotBlank()) whatsappCount++
            }
        }

        return PendingSendStats(
            presentCount = presentCount,
            absentCount = absentCount,
            smsCount = smsCount,
            whatsappCount = whatsappCount,
            targetStudentsCount = targetStudents
        )
    }

    suspend fun queueAttendanceMessages(dateStr: String, eventType: EventType): Int {
        val students = studentDao.getAllActiveStudents().firstOrNull() ?: emptyList()
        val attendanceMap = attendanceDao.getAttendanceListForDate(dateStr).associateBy { it.studentId }
        val settings = getSettings()

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormatter.format(Date())

        var queuedCount = 0

        students.forEach { student ->
            val isPresent = attendanceMap[student.id]?.isPresent ?: true

            val isTarget = when (eventType) {
                EventType.DEPARTURE -> isPresent
                else -> when (settings.notificationTarget) {
                    NotificationTarget.ABSENT_ONLY -> !isPresent
                    NotificationTarget.PRESENT_ONLY -> isPresent
                    NotificationTarget.BOTH -> true
                }
            }

            if (isTarget) {
                val actualEventType = if (eventType == EventType.DEPARTURE) {
                    EventType.DEPARTURE
                } else {
                    if (isPresent) EventType.ARRIVAL else EventType.ABSENCE
                }

                val template = when (actualEventType) {
                    EventType.ABSENCE -> settings.absenceTemplate
                    EventType.ARRIVAL -> settings.arrivalTemplate
                    EventType.DEPARTURE -> settings.departureTemplate
                }

                val formattedMsg = template
                    .replace("{نام_شاگرد}", student.name)
                    .replace("{نام_پدر}", student.fatherName)
                    .replace("{ساعت}", currentTime)
                    .replace("{تاریخ}", dateStr)

                // SMS Queue
                if (settings.enableSms && student.smsPhone.isNotBlank()) {
                    val existing = messageDao.findExistingRecord(student.id, dateStr, actualEventType, SendChannel.SMS)
                    if (existing == null) {
                        messageDao.insertMessage(
                            MessageRecord(
                                studentId = student.id,
                                studentName = student.name,
                                phoneNumber = student.smsPhone,
                                messageText = formattedMsg,
                                eventType = actualEventType,
                                channel = SendChannel.SMS,
                                date = dateStr,
                                status = MessageStatus.PENDING
                            )
                        )
                        queuedCount++
                    }
                }

                // WhatsApp Queue
                if (settings.enableWhatsapp && student.whatsappPhone.isNotBlank()) {
                    val existing = messageDao.findExistingRecord(student.id, dateStr, actualEventType, SendChannel.WHATSAPP)
                    if (existing == null) {
                        messageDao.insertMessage(
                            MessageRecord(
                                studentId = student.id,
                                studentName = student.name,
                                phoneNumber = student.whatsappPhone,
                                messageText = formattedMsg,
                                eventType = actualEventType,
                                channel = SendChannel.WHATSAPP,
                                date = dateStr,
                                status = MessageStatus.ACTION_REQUIRED // Requires user interaction/action
                            )
                        )
                        queuedCount++
                    }
                }
            }
        }

        if (queuedCount > 0) {
            triggerWorkManager()
        }

        return queuedCount
    }

    suspend fun retryMessage(messageId: Long) {
        val msg = messageDao.getMessageById(messageId) ?: return
        if (msg.channel == SendChannel.SMS) {
            messageDao.updateMessageStatus(messageId, MessageStatus.PENDING, null, null)
            triggerWorkManager()
        }
    }

    fun triggerWorkManager() {
        val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    suspend fun processPendingSmsMessages() {
        val pending = messageDao.getMessagesByStatus(MessageStatus.PENDING)
        pending.forEach { msg ->
            if (msg.channel == SendChannel.SMS) {
                messageDao.updateMessageStatus(msg.id, MessageStatus.SENDING, null, null)
                val result = smsSender.sendSms(msg.phoneNumber, msg.messageText)
                when (result) {
                    is SmsResult.Success -> {
                        messageDao.updateMessageStatus(msg.id, MessageStatus.SENT, System.currentTimeMillis(), null)
                    }
                    is SmsResult.Error -> {
                        messageDao.updateMessageStatus(msg.id, MessageStatus.FAILED, null, result.errorMessage)
                    }
                }
            }
        }
    }
}
