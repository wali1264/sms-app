package com.example.ui.attendance

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.auth.AuthState
import com.example.auth.SupabaseAuthManager
import com.example.data.entity.EventType
import com.example.ui.theme.*

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    onNavigateToStudents: () -> Unit,
    authManager: SupabaseAuthManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val authState = authManager?.authState?.collectAsStateWithLifecycle()?.value
    val isTeacher = (authState as? AuthState.LoggedIn)?.role == "TEACHER"
    val isOnline = authManager?.isNetworkAvailable() ?: true

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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "حضور و غیاب",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828))
                            .testTag("network_status_dot")
                    )
                }
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

                    if (!isTeacher) {
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
                        onToggle = {
                            if (isTeacher && !isOnline) {
                                Toast.makeText(
                                    context,
                                    "برای ثبت حضور و غیاب توسط معلم، اتصال به اینترنت الزامی است.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                viewModel.toggleAttendance(item.student.id, item.isPresent)
                            }
                        }
                    )
                }
            }
        }

        // Bottom Actions Area (Only visible for Manager)
        if (!isTeacher && uiState.summary.totalStudents > 0) {
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
            }
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
            .clickable { onToggle() }
            .testTag("student_row_${item.student.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.student.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "نام پدر: ${item.student.fatherName.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (item.isPresent) PrimaryContainerBlue else Color(0xFFFFEBEE),
                modifier = Modifier.clickable { onToggle() }
            ) {
                Text(
                    text = if (item.isPresent) "حاضر" else "غایب",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isPresent) PrimaryBlue else ErrorRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
