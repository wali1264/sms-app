package com.example.ui.attendance

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.EventType
import com.example.ui.theme.CardBackground
import com.example.ui.theme.DividerColor
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.OnPrimaryContainerBlue
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryContainerBlue
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    onNavigateToStudents: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "حضور و غیاب",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Text(
                    text = uiState.dateFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(
                onClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) viewModel.onSearchQueryChanged("")
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SecondaryContainer)
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "جستجو",
                    tint = TextPrimary
                )
            }
        }

        // Search Bar (if expanded)
        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("جستجوی نام، نام پدر یا کد شاگرد...", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_input"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = DividerColor
                )
            )
        }

        // Summary Stats Card
        if (uiState.summary.totalStudents > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainerBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("شاگردان", style = MaterialTheme.typography.labelMedium, color = OnPrimaryContainerBlue.copy(alpha = 0.7f))
                            Text("${uiState.summary.totalStudents}", style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainerBlue)
                        }

                        Box(modifier = Modifier.size(1.dp, 32.dp).background(OnPrimaryContainerBlue.copy(alpha = 0.15f)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("حاضر", style = MaterialTheme.typography.labelMedium, color = OnPrimaryContainerBlue.copy(alpha = 0.7f))
                            Text("${uiState.summary.presentCount}", style = MaterialTheme.typography.titleLarge, color = OnPrimaryContainerBlue)
                        }

                        Box(modifier = Modifier.size(1.dp, 32.dp).background(OnPrimaryContainerBlue.copy(alpha = 0.15f)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("غایب", style = MaterialTheme.typography.labelMedium, color = OnPrimaryContainerBlue.copy(alpha = 0.7f))
                            Text("${uiState.summary.absentCount}", style = MaterialTheme.typography.titleLarge, color = ErrorRed)
                        }
                    }

                    Button(
                        onClick = { viewModel.openSendConfirmation(EventType.ARRIVAL) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("fast_send_button")
                    ) {
                        Text("ثبت نهایی", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    }
                }
            }
        }

        // Main Student List or Empty State
        if (uiState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.summary.totalStudents == 0) "هیچ شاگردی ثبت نشده است." else "شاگردی با این مشخصات یافت نشد.",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.summary.totalStudents == 0) {
                        Button(
                            onClick = onNavigateToStudents,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("add_first_student_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("افزودن شاگرد جدید")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.items, key = { it.student.id }) { item ->
                    StudentAttendanceRow(
                        item = item,
                        onToggle = { viewModel.toggleAttendance(item.student.id, item.isPresent) }
                    )
                }
            }
        }

        // Bottom Actions Area
        if (uiState.summary.totalStudents > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.openSendConfirmation(EventType.ARRIVAL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("send_attendance_sms_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ارسال پیام به والدین",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    if (uiState.settings.enableDeparture) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.openSendConfirmation(EventType.DEPARTURE) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("send_departure_sms_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ثبت خروج و ارسال پیام",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (uiState.isConfirmDialogOpen) {
        val stats = uiState.pendingSendStats
        val eventTitle = if (uiState.confirmEventType == EventType.DEPARTURE) "ارسال پیام خروج" else "ارسال پیام حضور و غیاب"

        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmationDialog() },
            title = {
                Text(
                    text = eventTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stats != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("غایب: ${stats.absentCount} نفر", style = MaterialTheme.typography.bodyLarge, color = ErrorRed)
                            Text("حاضر: ${stats.presentCount} نفر", style = MaterialTheme.typography.bodyLarge, color = PrimaryBlue)
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))

                        Text("SMS: ${stats.smsCount} پیام", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("WhatsApp: ${stats.whatsappCount} پیام", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    } else {
                        Text("در حال محاسبه خلاصه ارسال...")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndSend() },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_dialog_send_button")
                ) {
                    Text("ارسال")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissConfirmationDialog() }
                ) {
                    Text("لغو", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun StudentAttendanceRow(
    item: StudentAttendanceItem,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_row_${item.student.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.student.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "ولد ${item.student.fatherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Segmented Toggle Control (Single Touch Toggle)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SecondaryContainer)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (item.isPresent) PrimaryBlue else Color.Transparent)
                        .clickable { if (!item.isPresent) onToggle() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "حاضر",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (item.isPresent) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.isPresent) Color.White else TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!item.isPresent) ErrorRed else Color.Transparent)
                        .clickable { if (item.isPresent) onToggle() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "غایب",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (!item.isPresent) FontWeight.Bold else FontWeight.Normal,
                        color = if (!item.isPresent) Color.White else TextSecondary
                    )
                }
            }
        }
    }
}
