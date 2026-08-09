package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {

    fun getTodayDateIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getTodayFormattedFa(): String {
        val today = Date()
        val dayOfWeekFormatter = SimpleDateFormat("EEEE", Locale("fa"))
        val dayOfWeek = dayOfWeekFormatter.format(today)

        // Simple Afghan / Persian date display string
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        val monthsFa = arrayOf(
            "حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله",
            "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت"
        )

        // Basic shamsi conversion approximation for UI display or local Gregorian fallback
        // standard Afghan Shamsi month mapping
        val monthName = if (month in 1..12) monthsFa[(month - 1) % 12] else ""

        return "$dayOfWeek، $day $monthName $year"
    }
}
