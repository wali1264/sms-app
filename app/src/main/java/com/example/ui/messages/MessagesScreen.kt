package com.example.ui.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.EventType
import com.example.data.entity.MessageRecord
import com.example.data.entity.MessageStatus
import com.example.ui.theme.CardBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessagesScreen(viewModel: MessagesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isGradeMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "سوابق پیام‌ها",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Shamsi Date Filter Button
                    Surface(
                        onClick = { viewModel.openDatePicker() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.isToday) PrimaryBlue.copy(alpha = 0.1f) else Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, if (uiState.isToday) PrimaryBlue.copy(alpha = 0.4f) else Color(0xFFFF9800)),
                        modifier = Modifier.testTag("date_filter_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "تقویم",
                                tint = if (uiState.isToday) PrimaryBlue else Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (uiState.isToday) "امروز (${uiState.selectedJalaliDay}/${uiState.selectedJalaliMonth})" else "تاریخ: ${uiState.selectedJalaliDay}/${uiState.selectedJalaliMonth}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isToday) PrimaryBlue else Color(0xFFE65100)
                            )
                        }
                    }
                }

                Text(
                    text = "${uiState.filteredMessages.size} پیام ثبت‌شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Retry All Failed Messages Button
                OutlinedButton(
                    onClick = { viewModel.retryAllFailedMessages() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border = BorderStroke(1.dp, PrimaryBlue),
                    modifier = Modifier.testTag("retry_all_failed_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تلاش مجدد",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "ارسال مجدد همه", fontSize = 11.sp)
                }
            }
        }

        // Class / Grade Filter Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(uiState.availableGrades) { gradeOption ->
                    val isSelected = (gradeOption == uiState.selectedGrade)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectGrade(gradeOption) },
                        label = { Text(gradeOption, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 1: Event Type Summary Cards (غایبین، حاضرین/ورود، خروج)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .testTag("event_type_summary_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "تفکیک نوع رویداد پیام‌ها:",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Absence Card (غایبین)
                    val isAbsenceActive = (uiState.activeEventTypeFilter == EventTypeFilter.ABSENCE)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAbsenceActive) ErrorRed.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.06f))
                            .clickable { viewModel.toggleEventTypeFilter(EventTypeFilter.ABSENCE) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔴 غایبین", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                            Text("${uiState.absenceCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }
                    }

                    // Arrival Card (ورود / حاضرین)
                    val isArrivalActive = (uiState.activeEventTypeFilter == EventTypeFilter.ARRIVAL)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isArrivalActive) SuccessGreen.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.06f))
                            .clickable { viewModel.toggleEventTypeFilter(EventTypeFilter.ARRIVAL) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🟢 ورود/حاضرین", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Text("${uiState.arrivalCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }

                    // Departure Card (خروج)
                    val isDepartureActive = (uiState.activeEventTypeFilter == EventTypeFilter.DEPARTURE)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDepartureActive) PrimaryBlue.copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.06f))
                            .clickable { viewModel.toggleEventTypeFilter(EventTypeFilter.DEPARTURE) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔵 خروج", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            Text("${uiState.departureCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Status Summary Cards (موفق، در انتظار، در حال ارسال، ناموفق)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .testTag("sms_queue_summary_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "وضعیت ارسال:",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Success Filter
                    val isSuccessActive = (uiState.activeStatusFilter == MessageStatusFilter.SUCCESS)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSuccessActive) SuccessGreen.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { viewModel.toggleStatusFilter(MessageStatusFilter.SUCCESS) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("موفق", fontSize = 10.sp, color = SuccessGreen)
                            Text("${uiState.sentCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }

                    // Pending Filter
                    val isPendingActive = (uiState.activeStatusFilter == MessageStatusFilter.PENDING)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPendingActive) PrimaryBlue.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { viewModel.toggleStatusFilter(MessageStatusFilter.PENDING) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("در انتظار", fontSize = 10.sp, color = PrimaryBlue)
                            Text("${uiState.pendingCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }

                    // Sending Filter
                    val isSendingActive = (uiState.activeStatusFilter == MessageStatusFilter.SENDING)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSendingActive) PrimaryBlue.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { viewModel.toggleStatusFilter(MessageStatusFilter.SENDING) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("در حال ارسال", fontSize = 10.sp, color = PrimaryBlue)
                            Text("${uiState.sendingCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }

                    // Failed Filter
                    val isFailedActive = (uiState.activeStatusFilter == MessageStatusFilter.FAILED)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFailedActive) ErrorRed.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { viewModel.toggleStatusFilter(MessageStatusFilter.FAILED) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ناموفق", fontSize = 10.sp, color = ErrorRed)
                            Text("${uiState.failedCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message List
        if (uiState.filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.activeStatusFilter != MessageStatusFilter.ALL || uiState.activeEventTypeFilter != EventTypeFilter.ALL)
                        "پیامی با فیلتر انتخابی یافت نشد."
                    else
                        "هیچ سابقه پیامی برای این تاریخ وجود ندارد.",
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
                items(uiState.filteredMessages, key = { it.id }) { message ->
                    MessageRowCard(
                        message = message,
                        onRetry = { viewModel.retryMessage(message.id) }
                    )
                }
            }
        }
    }

    // Shamsi Date Picker Dialog (Only Numbers 1-12 for Month, 1-31 for Day)
    if (uiState.isDatePickerOpen) {
        JalaliDatePickerDialog(
            currentMonth = uiState.selectedJalaliMonth,
            currentDay = uiState.selectedJalaliDay,
            onDismiss = { viewModel.closeDatePicker() },
            onDateSelected = { month, day ->
                viewModel.setJalaliDate(month, day)
            },
            onResetToToday = {
                viewModel.resetToToday()
            }
        )
    }
}

@Composable
fun JalaliDatePickerDialog(
    currentMonth: Int,
    currentDay: Int,
    onDismiss: () -> Unit,
    onDateSelected: (month: Int, day: Int) -> Unit,
    onResetToToday: () -> Unit
) {
    var tempMonth by remember { mutableStateOf(currentMonth) }
    var tempDay by remember { mutableStateOf(currentDay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "انتخاب تاریخ شمسی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "لطفاً ماه (۱ تا ۱۲) و روز (۱ تا ۳۱) را بر اساس عدد انتخاب کنید:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Month Picker Row (Numbers 1 to 12)
                Column {
                    Text(text = "ماه سال (عدد ۱ الی ۱۲):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(12) { idx ->
                            val mNum = idx + 1
                            val isSel = (tempMonth == mNum)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PrimaryBlue else Color(0xFFF0F0F0))
                                    .clickable { tempMonth = mNum }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mNum",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }

                // Day Picker Grid/Row (Numbers 1 to 31)
                Column {
                    Text(text = "روز ماه (عدد ۱ الی ۳۱):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(31) { idx ->
                            val dNum = idx + 1
                            val isSel = (tempDay == dNum)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PrimaryBlue else Color(0xFFF0F0F0))
                                    .clickable { tempDay = dNum }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$dNum",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }

                // Selected Summary
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "تاریخ انتخابی: روز $tempDay / ماه $tempMonth",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDateSelected(tempMonth, tempDay) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("تأیید و اعمال")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onResetToToday) {
                    Text("بازگشت به امروز", color = PrimaryBlue)
                }
                TextButton(onClick = onDismiss) {
                    Text("انصراف", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun MessageRowCard(
    message: MessageRecord,
    onRetry: () -> Unit
) {
    val eventLabel = when (message.eventType) {
        EventType.ABSENCE -> "غیبت"
        EventType.ARRIVAL -> "ورود"
        EventType.DEPARTURE -> "خروج"
    }

    val (statusText, statusBg, statusFg) = when (message.status) {
        MessageStatus.SENT -> Triple("ارسال شد", SuccessGreen.copy(alpha = 0.15f), SuccessGreen)
        MessageStatus.DELIVERED -> Triple("تحویل داده شد", SuccessGreen.copy(alpha = 0.15f), SuccessGreen)
        MessageStatus.FAILED_RETRYABLE -> Triple("ناموفق (قابل تلاش)", ErrorRed.copy(alpha = 0.15f), ErrorRed)
        MessageStatus.FAILED_PERMANENT -> Triple("ناموفق (دائمی)", ErrorRed.copy(alpha = 0.15f), ErrorRed)
        MessageStatus.FAILED_UNKNOWN -> Triple("معلق (قطع برنامه)", Color(0xFFD84315).copy(alpha = 0.15f), Color(0xFFD84315))
        MessageStatus.PENDING -> Triple("در انتظار ارسال", PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue)
        MessageStatus.SENDING -> Triple("در حال ارسال...", PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue)
        MessageStatus.ACTION_REQUIRED -> Triple("ناموفق", ErrorRed.copy(alpha = 0.15f), ErrorRed)
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
                text = "SMS: ${message.phoneNumber}${if (message.subId != null && message.subId != -1) " (SIM ${message.subId})" else ""}",
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
                    MessageStatus.FAILED_UNKNOWN,
                    MessageStatus.ACTION_REQUIRED
                )

                if (isFailed) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("retry_message_button_${message.id}")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("ارسال مجدد", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
