package com.example.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.dao.AttendanceDao
import com.example.data.dao.MessageDao
import com.example.data.dao.SchoolClassDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.StudentDao
import com.example.data.entity.AppSettings
import com.example.data.entity.AttendanceRecord
import com.example.data.entity.EventType
import com.example.data.entity.MessageRecord
import com.example.data.entity.MessageStatus
import com.example.data.entity.NotificationTarget
import com.example.data.entity.SchoolClass
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
    val targetStudentsCount: Int
)

class AppRepository(
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    private val messageDao: MessageDao,
    private val settingsDao: SettingsDao,
    private val schoolClassDao: SchoolClassDao? = null,
    private val smsSender: SmsSender,
    private val context: Context,
    private val authManager: com.example.auth.SupabaseAuthManager? = null
) {

    private fun checkTeacherInternetPermission() {
        if (authManager != null && authManager.isTeacher()) {
            if (!authManager.isNetworkAvailable()) {
                throw IllegalStateException("جهت انجام عملیات توسط معلم، اتصال به اینترنت ضروری است.")
            }
        }
    }

    private suspend fun triggerCloudSync() {
        authManager?.let { auth ->
            val schoolCode = auth.getSavedSchoolCode()
            if (auth.isNetworkAvailable() && schoolCode.isNotBlank()) {
                try {
                    val backupJson = exportBackupJson()
                    auth.syncStudentsToCloud(schoolCode, backupJson)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun syncWithCloudIfAvailable(): Result<String> = withContext(Dispatchers.IO) {
        cleanupAndSyncSchoolClasses(emptyList())
        val auth = authManager ?: return@withContext Result.success("آفلاین")
        if (!auth.isNetworkAvailable()) {
            return@withContext Result.success("آفلاین - استفاده از اطلاعات محلی")
        }
        val schoolCode = auth.getSavedSchoolCode()
        if (schoolCode.isBlank()) {
            return@withContext Result.success("کد مدرسه مشخص نیست.")
        }
        try {
            val cloudRes = auth.fetchStudentsFromCloud(schoolCode)
            val jsonStr = cloudRes.getOrNull()
            if (!jsonStr.isNullOrBlank()) {
                importBackupJson(jsonStr)
            }
            val backupJson = exportBackupJson()
            auth.syncStudentsToCloud(schoolCode, backupJson)
            return@withContext Result.success("همگام‌سازی ابری با موفقیت انجام شد.")
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

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

    suspend fun insertStudent(student: Student): Long {
        checkTeacherInternetPermission()
        val id = studentDao.insertStudent(student)
        triggerCloudSync()
        return id
    }

    suspend fun updateStudent(student: Student) {
        checkTeacherInternetPermission()
        studentDao.updateStudent(student)
        triggerCloudSync()
    }

    suspend fun deleteStudent(id: Long) {
        checkTeacherInternetPermission()
        studentDao.softDeleteStudent(id)
        triggerCloudSync()
    }

    suspend fun isStudentCodeDuplicate(code: String, currentStudentId: Long = 0L): Boolean {
        if (code.isBlank()) return false
        val existing = studentDao.getStudentByCode(code.trim())
        return existing != null && existing.id != currentStudentId
    }

    suspend fun generateNextStudentCode(): String {
        val allStudents = studentDao.getAllStudentsList().filter { it.isActive }
        val maxNumericCode = allStudents.mapNotNull { s ->
            s.studentCode.trim().toLongOrNull()
        }.maxOrNull() ?: 0L

        return (maxNumericCode + 1).toString()
    }

    // --- School Classes ---
    val allSchoolClassesFlow: Flow<List<SchoolClass>> = (schoolClassDao?.getAllClassesFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .map { list -> list.distinctBy { it.name.trim() } }

    suspend fun getAllSchoolClasses(): List<SchoolClass> =
        (schoolClassDao?.getAllClassesList() ?: emptyList()).distinctBy { it.name.trim() }

    private suspend fun cleanupAndSyncSchoolClasses(importedClasses: List<SchoolClass>) {
        val dao = schoolClassDao ?: return
        val currentLocal = dao.getAllClassesList()

        // 1. Remove duplicate records from local SQLite
        val uniqueLocalByName = mutableMapOf<String, SchoolClass>()
        val duplicateIdsToDelete = mutableListOf<Long>()

        for (cls in currentLocal) {
            val trimmed = cls.name.trim()
            if (trimmed.isEmpty()) {
                duplicateIdsToDelete.add(cls.id)
                continue
            }
            if (uniqueLocalByName.containsKey(trimmed)) {
                duplicateIdsToDelete.add(cls.id)
            } else {
                uniqueLocalByName[trimmed] = cls
            }
        }

        for (dupId in duplicateIdsToDelete) {
            dao.deleteClassById(dupId)
        }

        // 2. Insert or update imported classes
        val distinctImported = importedClasses.distinctBy { it.name.trim() }
        for (imp in distinctImported) {
            val trimmed = imp.name.trim()
            if (trimmed.isEmpty()) continue
            val existing = uniqueLocalByName[trimmed]
            if (existing == null) {
                val newId = dao.insertClass(SchoolClass(name = trimmed, sortOrder = imp.sortOrder))
                uniqueLocalByName[trimmed] = SchoolClass(id = newId, name = trimmed, sortOrder = imp.sortOrder)
            } else if (existing.sortOrder != imp.sortOrder) {
                dao.updateClass(existing.copy(sortOrder = imp.sortOrder))
            }
        }
    }

    suspend fun insertSchoolClass(schoolClass: SchoolClass): Long {
        checkTeacherInternetPermission()
        val dao = schoolClassDao ?: return 0L
        val trimmedName = schoolClass.name.trim()
        val existing = dao.getAllClassesList().find { it.name.trim() == trimmedName }
        if (existing != null) {
            return existing.id
        }
        val id = dao.insertClass(schoolClass.copy(name = trimmedName))
        triggerCloudSync()
        return id
    }

    suspend fun updateSchoolClass(schoolClass: SchoolClass) {
        checkTeacherInternetPermission()
        schoolClassDao?.updateClass(schoolClass)
        triggerCloudSync()
    }

    suspend fun deleteSchoolClass(id: Long) {
        checkTeacherInternetPermission()
        schoolClassDao?.deleteClassById(id)
        triggerCloudSync()
    }

    // --- Settings ---
    val appSettingsFlow: Flow<AppSettings> = settingsDao.getSettingsFlow().map { it ?: AppSettings() }

    suspend fun getSettings(): AppSettings = settingsDao.getSettings() ?: AppSettings()

    suspend fun saveSettings(settings: AppSettings) = settingsDao.saveSettings(settings)

    // --- Attendance ---
    fun getAttendanceForDate(dateStr: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForDate(dateStr)
    }

    suspend fun setAttendanceStatus(studentId: Long, dateStr: String, isPresent: Boolean) {
        checkTeacherInternetPermission()
        val existing = attendanceDao.getStudentAttendanceForDate(studentId, dateStr)
        val record = existing?.copy(isPresent = isPresent, updatedAt = System.currentTimeMillis())
            ?: AttendanceRecord(studentId = studentId, date = dateStr, isPresent = isPresent)
        attendanceDao.insertOrUpdateAttendance(record)
        triggerCloudSync()
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

    suspend fun autoPurgeOldMessages(daysThreshold: Int = 30): Int = withContext(Dispatchers.IO) {
        val thresholdMillis = System.currentTimeMillis() - (daysThreshold.toLong() * 24L * 60L * 60L * 1000L)
        return@withContext messageDao.deleteMessagesOlderThan(thresholdMillis)
    }

    private fun isEligibleForMessage(
        isPresent: Boolean,
        eventType: EventType,
        existingTypes: Set<EventType>,
        settings: AppSettings
    ): Boolean {
        if (eventType == EventType.DEPARTURE) {
            if (!isPresent) return false
            if (existingTypes.contains(EventType.DEPARTURE)) return false
            if (existingTypes.contains(EventType.ABSENCE)) return false
            return true
        } else {
            if (isPresent) {
                if (settings.notificationTarget == NotificationTarget.ABSENT_ONLY) return false
                if (existingTypes.contains(EventType.ARRIVAL)) return false
                if (existingTypes.contains(EventType.ABSENCE)) return false
                return true
            } else {
                if (settings.notificationTarget == NotificationTarget.PRESENT_ONLY) return false
                if (existingTypes.contains(EventType.ABSENCE)) return false
                if (existingTypes.contains(EventType.ARRIVAL)) return false
                return true
            }
        }
    }

    suspend fun getPendingSendStats(
        dateStr: String,
        eventType: EventType,
        selectedGrade: String? = null
    ): PendingSendStats {
        val allActiveStudents = studentDao.getAllActiveStudents().firstOrNull() ?: emptyList()
        val students = if (!selectedGrade.isNullOrBlank() && selectedGrade != "همه صنف‌ها") {
            allActiveStudents.filter { it.grade == selectedGrade }
        } else {
            allActiveStudents
        }

        val attendanceMap = attendanceDao.getAttendanceListForDate(dateStr).associateBy { it.studentId }
        val existingMessagesByStudent = messageDao.getMessageRecordsForDate(dateStr).groupBy { it.studentId }
        val settings = getSettings()

        var smsCount = 0
        var targetStudents = 0
        var presentCount = 0
        var absentCount = 0

        students.forEach { student ->
            val isPresent = attendanceMap[student.id]?.isPresent ?: true
            if (isPresent) presentCount++ else absentCount++

            val studentMsgs = existingMessagesByStudent[student.id] ?: emptyList()
            val sentTypes = studentMsgs.map { it.eventType }.toSet()

            val isEligible = isEligibleForMessage(
                isPresent = isPresent,
                eventType = eventType,
                existingTypes = sentTypes,
                settings = settings
            )

            if (isEligible) {
                targetStudents++
                if (settings.enableSms && student.smsPhone.isNotBlank()) smsCount++
            }
        }

        return PendingSendStats(
            presentCount = presentCount,
            absentCount = absentCount,
            smsCount = smsCount,
            targetStudentsCount = targetStudents
        )
    }

    suspend fun queueAttendanceMessages(
        dateStr: String,
        eventType: EventType,
        selectedGrade: String? = null
    ): Int {
        val allActiveStudents = studentDao.getAllActiveStudents().firstOrNull() ?: emptyList()
        val students = if (!selectedGrade.isNullOrBlank() && selectedGrade != "همه صنف‌ها") {
            allActiveStudents.filter { it.grade == selectedGrade }
        } else {
            allActiveStudents
        }

        val attendanceMap = attendanceDao.getAttendanceListForDate(dateStr).associateBy { it.studentId }
        val existingMessagesByStudent = messageDao.getMessageRecordsForDate(dateStr).groupBy { it.studentId }
        val settings = getSettings()

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormatter.format(Date())

        var queuedCount = 0

        students.forEach { student ->
            val isPresent = attendanceMap[student.id]?.isPresent ?: true
            val studentMsgs = existingMessagesByStudent[student.id] ?: emptyList()
            val sentTypes = studentMsgs.map { it.eventType }.toSet()

            val isEligible = isEligibleForMessage(
                isPresent = isPresent,
                eventType = eventType,
                existingTypes = sentTypes,
                settings = settings
            )

            if (isEligible) {
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

    suspend fun retryAllFailedMessages() {
        messageDao.resetFailedMessagesToPending()
        triggerWorkManager()
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
        // Step 0: Auto purge messages older than 30 days to keep database lean
        autoPurgeOldMessages(30)

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

    // --- Class Helpers ---
    suspend fun getActiveStudentCountForClass(className: String): Int = withContext(Dispatchers.IO) {
        studentDao.getActiveStudentCountForClass(className)
    }

    suspend fun updateClassNameForStudents(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        studentDao.updateClassNameForStudents(oldName, newName)
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
            obj.put("grade", s.grade)
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
            put("notificationTarget", settings.notificationTarget.name)
            put("enableDeparture", settings.enableDeparture)
            put("absenceTemplate", settings.absenceTemplate)
            put("arrivalTemplate", settings.arrivalTemplate)
            put("departureTemplate", settings.departureTemplate)
            put("selectedSubId", settings.selectedSubId)
            put("pacingDelayMs", settings.pacingDelayMs)
            put("autoGenerateStudentCode", settings.autoGenerateStudentCode)
        }
        root.put("settings", settingsObj)

        // School Classes
        val classesList = getAllSchoolClasses()
        val classesArray = JSONArray()
        classesList.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("sortOrder", c.sortOrder)
            classesArray.put(obj)
        }
        root.put("school_classes", classesArray)

        return@withContext root.toString(2)
    }

    suspend fun importBackupJson(jsonStr: String, clearLocalFirst: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            var restoredStudentsCount = 0
            var restoredAttendanceCount = 0

            // If clearLocalFirst is requested, perform atomic wipe of local database tables
            if (clearLocalFirst) {
                studentDao.deleteAllStudents()
                schoolClassDao?.deleteAllClasses()
                attendanceDao.deleteAllAttendanceRecords()
            }

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
                            grade = obj.optString("grade", obj.optString("whatsappPhone", "")),
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

            if (root.has("school_classes")) {
                val classesArr = root.getJSONArray("school_classes")
                val classesList = mutableListOf<SchoolClass>()
                for (i in 0 until classesArr.length()) {
                    val obj = classesArr.getJSONObject(i)
                    classesList.add(
                        SchoolClass(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name", ""),
                            sortOrder = obj.optInt("sortOrder", 0)
                        )
                    )
                }
                cleanupAndSyncSchoolClasses(classesList)
            } else {
                cleanupAndSyncSchoolClasses(emptyList())
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
                val existingLocal = settingsDao.getSettings()
                val obj = root.getJSONObject("settings")
                val targetName = obj.optString("notificationTarget", NotificationTarget.ABSENT_ONLY.name)
                val targetEnum = try { NotificationTarget.valueOf(targetName) } catch (e: Exception) { NotificationTarget.ABSENT_ONLY }

                // Preserve local hardware settings if existing on device
                val appSettings = AppSettings(
                    id = 1,
                    enableSms = obj.optBoolean("enableSms", true),
                    notificationTarget = existingLocal?.notificationTarget ?: targetEnum,
                    enableDeparture = obj.optBoolean("enableDeparture", false),
                    absenceTemplate = obj.optString("absenceTemplate", "والد گرامی، فرزند شما {نام_شاگرد} امروز در مکتب حاضر نشده است. لطفاً پیگیری نمایید."),
                    arrivalTemplate = obj.optString("arrivalTemplate", "والد گرامی، فرزند شما {نام_شاگرد} امروز ساعت {ساعت} وارد مکتب شد."),
                    departureTemplate = obj.optString("departureTemplate", "والد گرامی، فرزند شما {نام_شاگرد} امروز ساعت {ساعت} از مکتب خارج شد."),
                    selectedSubId = existingLocal?.selectedSubId ?: obj.optInt("selectedSubId", -1),
                    pacingDelayMs = existingLocal?.pacingDelayMs ?: obj.optLong("pacingDelayMs", 2500L),
                    autoGenerateStudentCode = obj.optBoolean("autoGenerateStudentCode", true)
                )
                settingsDao.saveSettings(appSettings)
            }

            Result.success("بازیابی با موفقیت انجام شد: $restoredStudentsCount شاگرد و $restoredAttendanceCount رکورد حضور و غیاب.")
        } catch (e: Exception) {
            Result.failure(Exception("فایل پشتیبان معتبر نمی‌باشد: ${e.localizedMessage}"))
        }
    }

    suspend fun restoreBackupWithInternetCheck(
        jsonStr: String,
        schoolCode: String,
        authManager: com.example.auth.SupabaseAuthManager
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!authManager.isNetworkAvailable()) {
            return@withContext Result.failure(Exception("برای بازیابی فایل پشتیبان، اتصال به اینترنت الزامی است."))
        }

        // 1. Local atomic wipe and restore
        val importRes = importBackupJson(jsonStr, clearLocalFirst = true)
        if (importRes.isFailure) {
            return@withContext importRes
        }

        // 2. Clear cloud DB & push new restored backup simultaneously
        try {
            val backupJson = exportBackupJson()
            authManager.syncStudentsToCloud(schoolCode, backupJson, clearServerDataFirst = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext importRes
    }

    suspend fun syncManagerCloudRestore(schoolCode: String, authManager: com.example.auth.SupabaseAuthManager): Result<String> = withContext(Dispatchers.IO) {
        if (!authManager.isNetworkAvailable()) {
            return@withContext Result.success("آفلاین - اطلاعات محلی استفاده می‌شود.")
        }
        val localStudents = studentDao.getAllStudentsList()
        val localClasses = schoolClassDao?.getAllClassesList() ?: emptyList()

        if (localStudents.isEmpty() && localClasses.isEmpty()) {
            // Local memory is completely empty! Bootstrap / fetch from server first.
            val cloudRes = authManager.fetchStudentsFromCloud(schoolCode)
            val jsonStr = cloudRes.getOrNull()
            if (!jsonStr.isNullOrBlank()) {
                val importRes = importBackupJson(jsonStr, clearLocalFirst = true)
                if (importRes.isSuccess) {
                    return@withContext Result.success("اطلاعات شما با موفقیت از ابر دریافت گردید.")
                }
            }
        } else {
            // Local data exists. Push latest backup to cloud for safety.
            try {
                val backupJson = exportBackupJson()
                authManager.syncStudentsToCloud(schoolCode, backupJson, clearServerDataFirst = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext Result.success("همگام‌سازی ابری انجام شد.")
    }
}
