package com.example.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
)

sealed class SmsResult {
    data class Success(val subIdUsed: Int) : SmsResult()
    data class Error(
        val errorMessage: String,
        val isRetryable: Boolean,
        val isCircuitBreakerTrigger: Boolean
    ) : SmsResult()
}

interface SmsSender {
    suspend fun sendSms(
        phoneNumber: String,
        messageText: String,
        targetSubId: Int = -1
    ): SmsResult

    fun getAvailableSims(): List<SimInfo>
}

class AndroidSmsSender(private val context: Context) : SmsSender {

    override fun getAvailableSims(): List<SimInfo> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return emptyList()
        }

        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val list = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            list.map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString() ?: "سیم‌کارت ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("AndroidSmsSender", "Failed to list SIM cards", e)
            emptyList()
        }
    }

    override suspend fun sendSms(
        phoneNumber: String,
        messageText: String,
        targetSubId: Int
    ): SmsResult {
        if (phoneNumber.isBlank()) {
            return SmsResult.Error(
                errorMessage = "شماره تلفن وارد نشده است.",
                isRetryable = false,
                isCircuitBreakerTrigger = false
            )
        }

        val cleanPhone = phoneNumber.trim()

        val smsManager: SmsManager? = try {
            if (targetSubId != -1) {
                val availableSims = getAvailableSims()
                val isSubActive = availableSims.any { it.subscriptionId == targetSubId }
                if (!isSubActive) {
                    return SmsResult.Error(
                        errorMessage = "سیم‌کارت انتخاب‌شده (کد $targetSubId) فعال یا در دسترس نیست. ارسال متوقف شد.",
                        isRetryable = false,
                        isCircuitBreakerTrigger = true
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val baseManager = context.getSystemService(SmsManager::class.java)
                    baseManager?.createForSubscriptionId(targetSubId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(targetSubId)
                }
            } else {
                context.getSystemService(SmsManager::class.java)
                    ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.e("AndroidSmsSender", "Failed to get SmsManager for subId $targetSubId", e)
            null
        }

        if (smsManager == null) {
            return SmsResult.Error(
                errorMessage = "سیم‌کارت یا سرویس ارسال پیامک در دسترس نیست.",
                isRetryable = false,
                isCircuitBreakerTrigger = true
            )
        }

        val parts = try {
            smsManager.divideMessage(messageText)
        } catch (e: Exception) {
            ArrayList<String>().apply { add(messageText) }
        }

        val totalParts = parts.size
        val actionName = "com.example.SMS_SENT_${System.currentTimeMillis()}_${Random.nextInt(10000)}"

        val deferredResults = Array(totalParts) { CompletableDeferred<Int>() }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == actionName) {
                    val partIndex = intent.getIntExtra("part_index", 0)
                    if (partIndex in 0 until totalParts) {
                        deferredResults[partIndex].complete(resultCode)
                    }
                }
            }
        }

        val filter = IntentFilter(actionName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.e("AndroidSmsSender", "Error registering receiver", e)
        }

        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntents = ArrayList<PendingIntent>()
            for (i in 0 until totalParts) {
                val intent = Intent(actionName).apply {
                    putExtra("part_index", i)
                    setPackage(context.packageName)
                }
                val pi = PendingIntent.getBroadcast(context, i, intent, flags)
                sentIntents.add(pi)
            }

            if (totalParts > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, parts[0], sentIntents[0], null)
            }

            val allPartResults = withTimeoutOrNull(30_000L) {
                deferredResults.map { it.await() }
            }

            if (allPartResults == null) {
                return SmsResult.Error(
                    errorMessage = "پاسخی از شبکه یا اندروید در زمان مقرر دریافت نشد (Timeout).",
                    isRetryable = true,
                    isCircuitBreakerTrigger = false
                )
            }

            val failedResultCode = allPartResults.firstOrNull { it != Activity.RESULT_OK }
            if (failedResultCode == null) {
                val usedSubId = if (targetSubId != -1) targetSubId else {
                    @Suppress("DEPRECATION")
                    try { SmsManager.getDefaultSmsSubscriptionId() } catch (e: Exception) { -1 }
                }
                return SmsResult.Success(subIdUsed = usedSubId)
            } else {
                return parseSmsResultCode(failedResultCode)
            }
        } catch (e: SecurityException) {
            Log.e("AndroidSmsSender", "Permission denied", e)
            return SmsResult.Error(
                errorMessage = "مجوز SEND_SMS داده نشده است.",
                isRetryable = false,
                isCircuitBreakerTrigger = true
            )
        } catch (e: Exception) {
            Log.e("AndroidSmsSender", "Failed to send SMS", e)
            return SmsResult.Error(
                errorMessage = "ارسال پیامک ناموفق بود: ${e.localizedMessage ?: "خطای ناپایدار"}",
                isRetryable = true,
                isCircuitBreakerTrigger = false
            )
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore unregister exception
            }
        }
    }

    private fun parseSmsResultCode(resultCode: Int): SmsResult.Error {
        return when (resultCode) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> SmsResult.Error(
                errorMessage = "شبکه تلفن همراه در دسترس نیست (No Service).",
                isRetryable = true,
                isCircuitBreakerTrigger = true
            )
            SmsManager.RESULT_ERROR_RADIO_OFF -> SmsResult.Error(
                errorMessage = "حالت پرواز یا رادیوی تلفن خاموش است.",
                isRetryable = true,
                isCircuitBreakerTrigger = true
            )
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> SmsResult.Error(
                errorMessage = "محدودیت تعداد ارسال پیامک اندروید پر شده است.",
                isRetryable = true,
                isCircuitBreakerTrigger = true
            )
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> SmsResult.Error(
                errorMessage = "خطای عمومی شبکه یا سیم‌کارت (احتمال اتمام اعتبار شارژ یا خطای اپراتور).",
                isRetryable = true,
                isCircuitBreakerTrigger = false
            )
            SmsManager.RESULT_ERROR_NULL_PDU -> SmsResult.Error(
                errorMessage = "خطای PDU در پردازش پیامک.",
                isRetryable = false,
                isCircuitBreakerTrigger = false
            )
            else -> SmsResult.Error(
                errorMessage = "ارسال توسط اپراتور/شبکه رد شد (کد خطا: $resultCode).",
                isRetryable = true,
                isCircuitBreakerTrigger = false
            )
        }
    }
}
