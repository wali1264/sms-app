package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EventType {
    ABSENCE,    // غیبت
    ARRIVAL,    // ورود / حضور
    DEPARTURE   // خروج
}

enum class SendChannel {
    SMS,
    WHATSAPP
}

enum class MessageStatus {
    PENDING,        // در انتظار ارسال
    SENDING,        // در حال ارسال
    SENT,           // ارسال شد
    FAILED,         // ناموفق
    ACTION_REQUIRED // نیازمند اقدام کاربر (مثلاً باز کردن واتساپ)
}

@Entity(
    tableName = "message_records",
    indices = [Index(value = ["studentId", "date", "eventType", "channel"], unique = true)]
)
data class MessageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: Long,
    val studentName: String,
    val phoneNumber: String,
    val messageText: String,
    val eventType: EventType,
    val channel: SendChannel,
    val date: String, // Format: YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val attempts: Int = 0,
    val status: MessageStatus = MessageStatus.PENDING,
    val errorMessage: String? = null
)
