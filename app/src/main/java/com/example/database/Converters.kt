package com.example.database

import androidx.room.TypeConverter
import com.example.data.entity.EventType
import com.example.data.entity.MessageStatus
import com.example.data.entity.NotificationTarget
import com.example.data.entity.SendChannel

class Converters {
    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String?): EventType =
        runCatching { EventType.valueOf(value ?: "") }.getOrDefault(EventType.ABSENCE)

    @TypeConverter
    fun fromSendChannel(value: SendChannel): String = value.name

    @TypeConverter
    fun toSendChannel(value: String?): SendChannel =
        runCatching { SendChannel.valueOf(value ?: "") }.getOrDefault(SendChannel.SMS)

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String?): MessageStatus =
        when (value) {
            "FAILED" -> MessageStatus.FAILED_PERMANENT
            else -> runCatching { MessageStatus.valueOf(value ?: "") }.getOrDefault(MessageStatus.PENDING)
        }

    @TypeConverter
    fun fromNotificationTarget(value: NotificationTarget): String = value.name

    @TypeConverter
    fun toNotificationTarget(value: String?): NotificationTarget =
        runCatching { NotificationTarget.valueOf(value ?: "") }.getOrDefault(NotificationTarget.ABSENT_ONLY)
}
