package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

object WhatsappHelper {

    fun sendWhatsappMessage(context: Context, phoneNumber: String, messageText: String): Boolean {
        if (phoneNumber.isBlank()) return false

        return try {
            val cleanPhone = phoneNumber.replace("+", "").replace(" ", "").trim()
            val encodedMessage = URLEncoder.encode(messageText, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("WhatsappHelper", "Failed to open WhatsApp", e)
            false
        }
    }
}
