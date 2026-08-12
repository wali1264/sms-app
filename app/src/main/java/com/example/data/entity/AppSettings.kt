package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NotificationTarget {
    ABSENT_ONLY,  // فقط غایبین
    PRESENT_ONLY, // فقط حاضرین
    BOTH          // حاضرین و غایبین
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val enableSms: Boolean = true,
    val notificationTarget: NotificationTarget = NotificationTarget.ABSENT_ONLY,
    val enableDeparture: Boolean = false,
    val absenceTemplate: String = "والد گرامی، فرزند شما {نام_شاگرد} امروز در مکتب حاضر نشده است. لطفاً پیگیری نمایید.",
    val arrivalTemplate: String = "والد گرامی، فرزند شما {نام_شاگرد} امروز ساعت {ساعت} وارد مکتب شد.",
    val departureTemplate: String = "والد گرامی، فرزند شما {نام_شاگرد} امروز ساعت {ساعت} از مکتب خارج شد.",
    val selectedSubId: Int = -1,
    val pacingDelayMs: Long = 2500L,
    val autoGenerateStudentCode: Boolean = true
)
