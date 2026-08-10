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
import com.example.sms.SimInfo
import com.example.sms.SmsResult
import com.example.sms.SmsSender
import com.example.worker.SendMessageWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    fun getAvailableSims(): List<SimInfo> {
        return smsSender.getAvailableSims()
    }

    suspend fun retryMessage(messageId: Long) {
        val msg = messageDao.getMessageById(messageId) ?: return
        if (msg.channel == SendChannel.SMS) {
            messageDao.updateMessageResult(
                id = messageId,
                status = MessageStatus.PENDING,
                sentAt = null,
                attempts = 0,
                errorMessage = null,
                subId = null
            )
            triggerWorkManager()
        }
    }

    fun triggerWorkManager() {
        try {
            val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun processPendingSmsMessages() {
        // Step 1: Recover any stuck SENDING messages from crash/reboot
        messageDao.markStuckSendingMessagesAsUnknown()

        // Step 2: Get settings
        val settings = settingsDao.getSettings() ?: AppSettings()

        // Step 3: Fetch pending or retryable messages
        val queue = messageDao.getPendingOrRetryableMessages().filter { it.channel == SendChannel.SMS }
        if (queue.isEmpty()) return

        var consecutiveFailures = 0

        for (i in queue.indices) {
            val msg = queue[i]

            // Mark as SENDING before attempt
            messageDao.updateMessageStatus(msg.id, MessageStatus.SENDING, null, null)

            val result = smsSender.sendSms(
                phoneNumber = msg.phoneNumber,
                messageText = msg.messageText,
                targetSubId = settings.selectedSubId
            )

            when (result) {
                is SmsResult.Success -> {
                    consecutiveFailures = 0
                    messageDao.updateMessageResult(
                        id = msg.id,
                        status = MessageStatus.SENT,
                        sentAt = System.currentTimeMillis(),
                        attempts = msg.attempts + 1,
                        errorMessage = null,
                        subId = result.subIdUsed
                    )
                }
                is SmsResult.Error -> {
                    consecutiveFailures++
                    val newAttempts = msg.attempts + 1
                    val newStatus = if (!result.isRetryable || newAttempts >= 3) {
                        MessageStatus.FAILED_PERMANENT
                    } else {
                        MessageStatus.FAILED_RETRYABLE
                    }

                    messageDao.updateMessageResult(
                        id = msg.id,
                        status = newStatus,
                        sentAt = null,
                        attempts = newAttempts,
                        errorMessage = result.errorMessage,
                        subId = null
                    )

                    // Circuit Breaker: Stop queue if trigger condition met or 3 consecutive failures
                    if (result.isCircuitBreakerTrigger || consecutiveFailures >= 3) {
                        break
                    }
                }
            }

            // Pacing delay between SMS messages if more messages remain
            if (i < queue.size - 1 && consecutiveFailures == 0) {
                delay(settings.pacingDelayMs)
            }
        }
    }

    // --- Backup & Restore (پشتیبان‌گیری و بازیابی) ---
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "TeacherAttendanceApp")
        root.put("version", 1)
        root.put("exportTimestamp", System.currentTimeMillis())

        // Students
        val studentsList = studentDao.getAllStudentsList()
        val studentsArray = JSONArray()
        studentsList.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("fatherName", s.fatherName)
            obj.put("smsPhone", s.smsPhone)
            obj.put("whatsappPhone", s.whatsappPhone)
            obj.put("studentCode", s.studentCode)
            obj.put("createdAt", s.createdAt)
            obj.put("isActive", s.isActive)
            studentsArray.put(obj)
        }
        root.put("students", studentsArray)

        // Attendance
        val attendanceList = attendanceDao.getAllAttendanceRecords()
        val attendanceArray = JSONArray()
        attendanceList.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("studentId", a.studentId)
            obj.put("date", a.date)
            obj.put("isPresent", a.isPresent)
            obj.put("updatedAt", a.updatedAt)
            attendanceArray.put(obj)
        }
        root.put("attendance", attendanceArray)

        // Settings
        val settings = getSettings()
        val settingsObj = JSONObject().apply {
            put("enableSms", settings.enableSms)
            put("enableWhatsapp", settings.enableWhatsapp)
            put("notificationTarget", settings.notificationTarget.name)
            put("enableDeparture", settings.enableDeparture)
            put("absenceTemplate", settings.absenceTemplate)
            put("arrivalTemplate", settings.arrivalTemplate)
            put("departureTemplate", settings.departureTemplate)
            put("selectedSubId", settings.selectedSubId)
            put("pacingDelayMs", settings.pacingDelayMs)
        }
        root.put("settings", settingsObj)

        return@withContext root.toString(2)
    }

    suspend fun importBackupJson(jsonStr: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            var restoredStudentsCount = 0
            var restoredAttendanceCount = 0

            if (root.has("students")) {
                val studentsArr = root.getJSONArray("students")
                val studentsList = mutableListOf<Student>()
                for (i in 0 until studentsArr.length()) {
                    val obj = studentsArr.getJSONObject(i)
                    studentsList.add(
                        Student(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name", ""),
                            fatherName = obj.optString("fatherName", ""),
                            smsPhone = obj.optString("smsPhone", ""),
                            whatsappPhone = obj.optString("whatsappPhone", ""),
                            studentCode = obj.optString("studentCode", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            isActive = obj.optBoolean("isActive", true)
                        )
                    )
                }
                if (studentsList.isNotEmpty()) {
                    studentDao.insertStudents(studentsList)
                    restoredStudentsCount = studentsList.size
                }
            }

            if (root.has("attendance")) {
                val attArr = root.getJSONArray("attendance")
                val attList = mutableListOf<AttendanceRecord>()
                for (i in 0 until attArr.length()) {
                    val obj = attArr.getJSONObject(i)
                    attList.add(
                        AttendanceRecord(
                            id = obj.optLong("id", 0L),
                            studentId = obj.optLong("studentId", 0L),
                            date = obj.optString("date", ""),
                            isPresent = obj.optBoolean("isPresent", true),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (attList.isNotEmpty()) {
                    attendanceDao.insertOrUpdateAll(attList)
                    restoredAttendanceCount = attList.size
                }
            }

            if (root.has("settings")) {
                val obj = root.getJSONObject("settings")
                val targetName = obj.optString("notificationTarget", NotificationTarget.ABSENT_ONLY.name)
                val targetEnum = try { NotificationTarget.valueOf(targetName) } catch (e: Exception) { NotificationTarget.ABSENT_ONLY }

                val appSettings = AppSettings(
                    id = 1,
                    enableSms = obj.optBoolean("enableSms", true),
                    enableWhatsapp = obj.optBoolean("enableWhatsapp", false),
                    notificationTarget = targetEnum,
                    enableDeparture = obj.optBoolean("enableDeparture", false),
                    absenceTemplate = obj.optString("absenceTemplate", "والد گرامی، فرزند شما {نام_شاگرد} امروز در مکتب حاضر نشده است. لطفاً پیگیری نمایید."),
                    arrivalTemplate = obj.optString("arrivalTemplate", "والد گرامی، فرزند شما {نام_شاگرد} امروز ساعت {ساعت} وارد مکتب شد."),
                    departureTemplate = obj.optString("departureTemplate", "والد گرامی، فرزند شما {نام_شاگرد} امروز ساعت {ساعت} از مکتب خارج شد."),
                    selectedSubId = obj.optInt("selectedSubId", -1),
                    pacingDelayMs = obj.optLong("pacingDelayMs", 2500L)
                )
                settingsDao.saveSettings(appSettings)
            }

            Result.success("بازیابی با موفقیت انجام شد: $restoredStudentsCount شاگرد و $restoredAttendanceCount رکورد حضور و غیاب.")
        } catch (e: Exception) {
            Result.failure(Exception("فایل پشتیبان معتبر نمی‌باشد: ${e.localizedMessage}"))
        }
    }

    suspend fun syncManagerCloudRestore(schoolCode: String, authManager: com.example.auth.SupabaseAuthManager): Result<String> = withContext(Dispatchers.IO) {
        if (!authManager.isNetworkAvailable()) {
            return@withContext Result.success("آفلاین - اطلاعات محلی استفاده می‌شود.")
        }
        val localStudents = studentDao.getAllStudentsList()
        if (localStudents.isEmpty()) {
            // Local memory is empty. Check if cloud has a backup for this manager!
            val cloudRes = authManager.fetchStudentsFromCloud(schoolCode)
            val jsonStr = cloudRes.getOrNull()
            if (!jsonStr.isNullOrBlank()) {
                val importRes = importBackupJson(jsonStr)
                if (importRes.isSuccess) {
                    return@withContext Result.success("اطلاعات شما با موفقیت از ابر بازیابی شد.")
                }
            }
        } else {
            // Local data exists. Push latest backup to cloud for safety.
            try {
                val backupJson = exportBackupJson()
                authManager.syncStudentsToCloud(schoolCode, backupJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext Result.success("همگام‌سازی ابری انجام شد.")
    }
}
