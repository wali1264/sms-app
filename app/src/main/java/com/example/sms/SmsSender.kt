package com.example.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

interface SmsSender {
    fun sendSms(phoneNumber: String, messageText: String): SmsResult
}

sealed class SmsResult {
    object Success : SmsResult()
    data class Error(val errorMessage: String) : SmsResult()
}

class AndroidSmsSender(private val context: Context) : SmsSender {

    override fun sendSms(phoneNumber: String, messageText: String): SmsResult {
        if (phoneNumber.isBlank()) {
            return SmsResult.Error("شماره تلفن وارد نشده است.")
        }
        
        val cleanPhone = phoneNumber.trim()
        
        return try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()

            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, messageText, null, null)
            }
            SmsResult.Success
        } catch (e: SecurityException) {
            Log.e("AndroidSmsSender", "Permission denied", e)
            SmsResult.Error("مجوز ارسال پیامک داده نشده است.")
        } catch (e: Exception) {
            Log.e("AndroidSmsSender", "Failed to send SMS", e)
            SmsResult.Error("ارسال پیامک ناموفق بود: ${e.localizedMessage ?: "خطای ناشناخته"}")
        }
    }
}
