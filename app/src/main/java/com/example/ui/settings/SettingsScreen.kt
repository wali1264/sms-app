package com.example.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthState
import com.example.auth.SupabaseAuthManager
import com.example.data.entity.NotificationTarget
import com.example.ui.theme.CardBackground
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authManager: SupabaseAuthManager? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessageFlow.collectAsStateWithLifecycle()
    val teachersList by viewModel.teachersList.collectAsStateWithLifecycle()
    val isLoadingTeachers by viewModel.isLoadingTeachers.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val authState = authManager?.authState?.collectAsStateWithLifecycle()?.value
    val isOnline = authManager?.isNetworkAvailable() ?: true

    val isManager = (authState as? AuthState.LoggedIn)?.role == "MANAGER"

    // Load teachers list when screen opens for Manager
    LaunchedEffect(authState) {
        if (authManager != null && authState is AuthState.LoggedIn) {
            val user = authState as AuthState.LoggedIn
            if (user.role == "MANAGER" && user.schoolCode.isNotBlank()) {
                viewModel.loadTeachers(authManager, user.schoolCode)
            }
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "تنظیمات",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )

        // 0. User Account Card
        if (authManager != null && authState is AuthState.LoggedIn) {
            val user = authState as AuthState.LoggedIn
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_account_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "اطلاعات حساب کاربری",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "نام کاربر: ${user.fullName.ifBlank { "نامشخص" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "نقش: ${if (user.role == "MANAGER") "مدیر مدرسه" else "معلم"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ایمیل: ${user.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    if (user.schoolName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "نام مدرسه: ${user.schoolName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val res = authManager.logout()
                                res.onFailure {
                                    Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = isOnline,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("btn_logout")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("خروج از حساب کاربری")
                    }

                    if (!isOnline) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "خروج از حساب کاربری در حالت آفلاین امکان‌پذیر نیست.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Manager Code & Teacher Requests Card (For Manager Only)
            if (isManager && user.schoolCode.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("manager_code_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "کد اختصاصی مدیر (کد مدرسه)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "این کد را در اختیار معلمان خود قرار دهید تا هنگام ثبت‌نام به این مدرسه متصل شوند:",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White
                            ) {
                                Text(
                                    text = user.schoolCode,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(user.schoolCode))
                                    Toast.makeText(context, "کد مدیر کپی شد: ${user.schoolCode}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("کپی کد")
                            }
                        }
                    }
                }

                // Teacher Management Card
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("teachers_management_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "مدیریت درخواست‌های معلمان",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.loadTeachers(authManager, user.schoolCode)
                                },
                                enabled = !isLoadingTeachers && isOnline
                            ) {
                                if (isLoadingTeachers) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی لیست معلمان")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (teachersList.isEmpty()) {
                            Text(
                                text = if (isLoadingTeachers) "در حال بارگذاری لیست معلمان..." else "هیچ معلمی با کد مدرسه شما ثبت‌نام نکرده است.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            teachersList.forEach { teacher ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (teacher.isApproved) Color(0xFFF1F8E9) else Color(0xFFFFF8E1)
                                    )
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
                                                text = teacher.fullName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "ایمیل: ${teacher.email}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                            if (teacher.phone.isNotBlank()) {
                                                Text(
                                                    text = "تلفن: ${teacher.phone}",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                            Text(
                                                text = if (teacher.isApproved) "وضعیت: فعال (تأیید شده)" else "وضعیت: در انتظار تأیید",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (teacher.isApproved) Color(0xFF2E7D32) else Color(0xFFE65100)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                viewModel.toggleTeacherApproval(
                                                    authManager,
                                                    teacher.id,
                                                    teacher.isApproved,
                                                    user.schoolCode
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (teacher.isApproved) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("btn_toggle_teacher_${teacher.id}")
                                        ) {
                                            Text(
                                                text = if (teacher.isApproved) "غیرفعال" else "تأیید",
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SMS & Notification Settings (Only visible to Manager or standalone mode)
        if (isManager) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_sending_methods_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "روش‌های ارسال پیام",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateEnableSms(!settings.enableSms) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = settings.enableSms,
                            onCheckedChange = { viewModel.updateEnableSms(it) },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
                            modifier = Modifier.testTag("enable_sms_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("پیامک (SMS)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateEnableWhatsapp(!settings.enableWhatsapp) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = settings.enableWhatsapp,
                            onCheckedChange = { viewModel.updateEnableWhatsapp(it) },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
                            modifier = Modifier.testTag("enable_whatsapp_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("واتساپ (WhatsApp)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }

            // Dual SIM & Pacing Settings Card
            if (settings.enableSms) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("settings_dual_sim_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تنظیمات سیم‌کارت و سرعت ارسال (Pacing)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "انتخاب سیم‌کارت ارسال:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val availableSims = viewModel.getAvailableSims()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSelectedSubId(-1) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.selectedSubId == -1,
                                onClick = { viewModel.updateSelectedSubId(-1) },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("سیم‌کارت پیش‌فرض سیستم", style = MaterialTheme.typography.bodyMedium)
                        }

                        availableSims.forEach { sim ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateSelectedSubId(sim.subscriptionId) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.selectedSubId == sim.subscriptionId,
                                    onClick = { viewModel.updateSelectedSubId(sim.subscriptionId) },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "سیم ${sim.slotIndex + 1}: ${sim.displayName} (${sim.carrierName})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "فاصله زمانی بین پیامک‌ها (Pacing):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val pacingOptions = listOf(
                            1000L to "۱ ثانیه",
                            2500L to "۲.۵ ثانیه (پیش‌فرض)",
                            5000L to "۵ ثانیه",
                            10000L to "۱۰ ثانیه"
                        )

                        pacingOptions.forEach { (delayMs, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updatePacingDelayMs(delayMs) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.pacingDelayMs == delayMs,
                                    onClick = { viewModel.updatePacingDelayMs(delayMs) },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Notification Targets
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_targets_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "گیرندگان اطلاع‌رسانی",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val targets = listOf(
                        NotificationTarget.ABSENT_ONLY to "فقط شاگردان غایب",
                        NotificationTarget.PRESENT_ONLY to "فقط شاگردان حاضر",
                        NotificationTarget.BOTH to "هم حاضر و هم غایب"
                    )

                    targets.forEach { (target, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateNotificationTarget(target) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.notificationTarget == target,
                                onClick = { viewModel.updateNotificationTarget(target) },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
            }

            // Message Templates
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_templates_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "قالب‌های پیام",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settings.absenceTemplate,
                        onValueChange = { viewModel.updateAbsenceTemplate(it) },
                        label = { Text("متن عدم حضور (غایب)") },
                        modifier = Modifier.fillMaxWidth().testTag("absence_template_field"),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = settings.arrivalTemplate,
                        onValueChange = { viewModel.updateArrivalTemplate(it) },
                        label = { Text("متن ورود (حاضر)") },
                        modifier = Modifier.fillMaxWidth().testTag("arrival_template_field"),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateEnableDeparture(!settings.enableDeparture) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = settings.enableDeparture,
                            onCheckedChange = { viewModel.updateEnableDeparture(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ارسال پیام خروج هم فعال باشد", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (settings.enableDeparture) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.departureTemplate,
                            onValueChange = { viewModel.updateDepartureTemplate(it) },
                            label = { Text("متن خروج") },
                            modifier = Modifier.fillMaxWidth().testTag("departure_template_field"),
                            maxLines = 3
                        )
                    }
                }
            }

            // Backup & Restore Card (پشتیبان‌گیری و بازیابی)
            var showExportDialog by remember { mutableStateOf(false) }
            var exportJsonText by remember { mutableStateOf("") }
            var showImportDialog by remember { mutableStateOf(false) }
            var importJsonText by remember { mutableStateOf("") }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_backup_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "پشتیبان‌گیری و بازیابی اطلاعات",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "اطلاعات شما همیشه اولویت اول را روی حافظه دستگاه دارد. جهت انتقال به دستگاه دیگر یا اطمینان بیشتر، می‌توانید فایل پشتیبان ایجاد یا بازیابی کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportBackup { json ->
                                    exportJsonText = json
                                    showExportDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_backup_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پشتیبان‌گیری", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f).testTag("import_backup_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("بازیابی اطلاعات", fontSize = 13.sp)
                        }
                    }

                    if (authManager != null && authState is AuthState.LoggedIn) {
                        val user = authState as AuthState.LoggedIn
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.syncCloud(authManager, user.schoolCode) },
                            modifier = Modifier.fillMaxWidth().testTag("sync_cloud_button")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("همگام‌سازی ابری با حساب مدیر", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Export Dialog
            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text("فایل پشتیبان اطلاعات") },
                    text = {
                        Column {
                            Text("متن فایل پشتیبان شما تولید شد. می‌توانید آن را کپی کرده و در جای امن نگهداری نمایید:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = exportJsonText,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(exportJsonText))
                                Toast.makeText(context, "فایل پشتیبان در حافظه موقت (Clipboard) کپی شد.", Toast.LENGTH_SHORT).show()
                                showExportDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("کپی متن پشتیبان")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExportDialog = false }) {
                            Text("بستن")
                        }
                    }
                )
            }

            // Import Dialog
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("بازیابی اطلاعات از پشتیبان") },
                    text = {
                        Column {
                            Text("متن فایل پشتیبان خود را در کادر زیر قرار داده و دکمه بازیابی را بزنید:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importJsonText,
                                onValueChange = { importJsonText = it },
                                placeholder = { Text("متن پشتیبان JSON را اینجا پیست کنید...") },
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (importJsonText.isNotBlank()) {
                                    viewModel.importBackup(importJsonText)
                                    showImportDialog = false
                                    importJsonText = ""
                                } else {
                                    Toast.makeText(context, "لطفاً متن پشتیبان را وارد کنید.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("بازیابی")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("انصراف")
                        }
                    }
                )
            }
        }
    }
}
