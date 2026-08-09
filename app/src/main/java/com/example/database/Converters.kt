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
    fun toEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromSendChannel(value: SendChannel): String = value.name

    @TypeConverter
    fun toSendChannel(value: String): SendChannel = SendChannel.valueOf(value)

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)

    @TypeConverter
    fun fromNotificationTarget(value: NotificationTarget): String = value.name

    @TypeConverter
    fun toNotificationTarget(value: String): NotificationTarget = NotificationTarget.valueOf(value)
}
