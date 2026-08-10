package com.example.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.EventType
import com.example.data.entity.MessageRecord
import com.example.data.entity.MessageStatus
import com.example.data.entity.SendChannel
import com.example.ui.theme.CardBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(viewModel: MessagesViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pendingCount = messages.count { it.status == MessageStatus.PENDING }
    val sendingCount = messages.count { it.status == MessageStatus.SENDING }
    val sentCount = messages.count { it.status == MessageStatus.SENT || it.status == MessageStatus.DELIVERED }
    val failedCount = messages.count { it.status in listOf(MessageStatus.FAILED_RETRYABLE, MessageStatus.FAILED_PERMANENT, MessageStatus.FAILED_UNKNOWN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "سوابق پیام‌ها",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Text(
                text = "${messages.size} پیام",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }

        // Queue Progress Card
        if (messages.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("sms_queue_summary_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("موفق", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                        Text("$sentCount", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("در انتظار", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                        Text("$pendingCount", style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("در حال ارسال", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                        Text("$sendingCount", style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ناموفق", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                        Text("$failedCount", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "هیچ سابقه پیامی وجود ندارد.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRowCard(
                        message = message,
                        onRetry = { viewModel.retryMessage(message.id) },
                        onOpenWhatsapp = { viewModel.openWhatsappForMessage(context, message) }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageRowCard(
    message: MessageRecord,
    onRetry: () -> Unit,
    onOpenWhatsapp: () -> Unit
) {
    val eventLabel = when (message.eventType) {
        EventType.ABSENCE -> "غیبت"
        EventType.ARRIVAL -> "ورود"
        EventType.DEPARTURE -> "خروج"
    }

    val channelLabel = when (message.channel) {
        SendChannel.SMS -> "SMS"
        SendChannel.WHATSAPP -> "WhatsApp"
    }

    val (statusText, statusBg, statusFg) = when (message.status) {
        MessageStatus.SENT -> Triple("ارسال شد", SuccessGreen.copy(alpha = 0.15f), SuccessGreen)
        MessageStatus.DELIVERED -> Triple("تحویل داده شد", SuccessGreen.copy(alpha = 0.15f), SuccessGreen)
        MessageStatus.FAILED_RETRYABLE -> Triple("ناموفق (قابل تلاش)", ErrorRed.copy(alpha = 0.15f), ErrorRed)
        MessageStatus.FAILED_PERMANENT -> Triple("ناموفق (دائمی)", ErrorRed.copy(alpha = 0.15f), ErrorRed)
        MessageStatus.FAILED_UNKNOWN -> Triple("معلق (قطع برنامه)", Color(0xFFD84315).copy(alpha = 0.15f), Color(0xFFD84315))
        MessageStatus.PENDING -> Triple("در انتظار ارسال", PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue)
        MessageStatus.SENDING -> Triple("در حال ارسال...", PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue)
        MessageStatus.ACTION_REQUIRED -> Triple("نیازمند اقدام", Color(0xFFE65100).copy(alpha = 0.15f), Color(0xFFE65100))
    }

    val timeFormatter = SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault())
    val timeFormatted = timeFormatter.format(Date(message.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card_${message.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Event Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryBlue.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = eventLabel, style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                    }

                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = statusText, style = MaterialTheme.typography.labelMedium, color = statusFg)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$channelLabel: ${message.phoneNumber}${if (message.subId != null && message.subId != -1) " (SIM ${message.subId})" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            message.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "علت خطا: $err",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                val isFailed = message.status in listOf(
                    MessageStatus.FAILED_RETRYABLE,
                    MessageStatus.FAILED_PERMANENT,
                    MessageStatus.FAILED_UNKNOWN
                )

                if (isFailed && message.channel == SendChannel.SMS) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("retry_message_button_${message.id}")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("ارسال مجدد", style = MaterialTheme.typography.labelMedium)
                    }
                } else if (message.channel == SendChannel.WHATSAPP && message.status == MessageStatus.ACTION_REQUIRED) {
                    Button(
                        onClick = onOpenWhatsapp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("open_whatsapp_button_${message.id}")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("باز کردن واتساپ", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
        }
    }
}
