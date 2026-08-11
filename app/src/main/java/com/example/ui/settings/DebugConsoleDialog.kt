package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.SupabaseAuthManager
import com.example.util.AppLogger
import com.example.util.LogEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel,
    authManager: SupabaseAuthManager?
) {
    val logs by AppLogger.logs.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filterErrorsOnly by remember { mutableStateOf(false) }

    val displayedLogs = if (filterErrorsOnly) logs.filter { it.isError } else logs

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("debug_console_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E), // Dark terminal style background
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "کنسول عیب‌یابی (Debug Console)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "ثبت لحظه‌ای تمام وقایع، خطاهای شبکه و همگام‌سازی",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("بستن", color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                // Action controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterErrorsOnly,
                        onClick = { filterErrorsOnly = !filterErrorsOnly },
                        label = { Text(if (filterErrorsOnly) "فقط خطاها (❌)" else "همه لاگ‌ها (${logs.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFB71C1C),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF333333),
                            labelColor = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Run Test Sync button
                    if (authManager != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    AppLogger.i("TestSync", "شروع دستی تست همگام‌سازی...")
                                    val user = (authManager.authState.value as? com.example.auth.AuthState.LoggedIn)
                                    val code = user?.schoolCode ?: ""
                                    if (code.isNotBlank()) {
                                        viewModel.syncCloud(authManager, code)
                                    } else {
                                        AppLogger.e("TestSync", "کد مدرسه موجود نیست.")
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تست همگام‌سازی", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Copy button
                    IconButton(
                        onClick = {
                            val text = AppLogger.getAllLogsText()
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "تمام لاگ‌های کنسول کپی شدند.", Toast.LENGTH_SHORT).show()
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "کپی لاگ‌ها", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Clear button
                    IconButton(
                        onClick = { AppLogger.clear() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF424242))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "پاک‌سازی", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Console output area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF121212)
                ) {
                    if (displayedLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "هیچ لاگی ثبت نشده است.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(displayedLogs) { log ->
                                LogItemRow(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (log.isError) Color(0xFF3E1212) else Color(0xFF1E261E),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "[${log.tag}]",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (log.isError) Color(0xFFFF8A80) else Color(0xFF81C784),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = log.timestamp,
                fontSize = 10.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = log.message,
            fontSize = 12.sp,
            color = if (log.isError) Color(0xFFFFCDD2) else Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}
