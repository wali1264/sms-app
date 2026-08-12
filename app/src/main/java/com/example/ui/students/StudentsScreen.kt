package com.example.ui.students

import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.Student
import com.example.ui.theme.CardBackground
import com.example.ui.theme.DividerColor
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.rotate
import com.example.ui.theme.SecondaryContainer

@Composable
fun StudentsScreen(
    viewModel: StudentsViewModel,
    authManager: com.example.auth.SupabaseAuthManager? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "students_refresh_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddStudentDialog() },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_student_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن شاگرد")
            }
        }
    ) { paddingValues ->
        val isOnline = authManager?.isNetworkAvailable() ?: true

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فهرست شاگردان",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828))
                            .testTag("students_network_status_dot")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.manualRefresh() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SecondaryContainer)
                            .testTag("students_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "بروزرسانی",
                            tint = TextPrimary,
                            modifier = if (uiState.isSyncing) Modifier.rotate(rotationAngle) else Modifier
                        )
                    }

                    Text(
                        text = "${uiState.students.size} نفر",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("جستجوی نام، نام پدر یا کد شاگرد...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    var classFilterExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { classFilterExpanded = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("students_class_filter_button")
                        ) {
                            Text(
                                text = uiState.selectedClassFilter,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "فیلتر صنف",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = classFilterExpanded,
                            onDismissRequest = { classFilterExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("همه صنف‌ها", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    viewModel.onClassFilterChanged("همه صنف‌ها")
                                    classFilterExpanded = false
                                }
                            )
                            uiState.schoolClasses.distinctBy { it.name.trim() }.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.name, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        viewModel.onClassFilterChanged(cls.name)
                                        classFilterExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("students_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = DividerColor
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Student List
            if (uiState.students.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.searchQuery.isBlank()) "هیچ شاگردی ثبت نشده است.\nاز دکمه + برای افزودن استفاده کنید." else "شاگردی پیدا نشد.",
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
                    items(uiState.students, key = { it.id }) { student ->
                        StudentItemCard(
                            student = student,
                            onEdit = { viewModel.openEditStudentDialog(student) },
                            onDelete = { viewModel.confirmDelete(student) }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (uiState.isFormDialogOpen) {
        val isEdit = uiState.editingStudent != null
        var gradeExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissFormDialog() },
            title = {
                Text(
                    text = if (isEdit) "ویرایش شاگرد" else "افزودن شاگرد جدید",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.errorMessage?.let { err ->
                        Text(text = err, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                    }

                    OutlinedTextField(
                        value = uiState.nameInput,
                        onValueChange = { viewModel.onNameChanged(it) },
                        label = { Text("نام شاگرد *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_name_input")
                    )

                    OutlinedTextField(
                        value = uiState.fatherNameInput,
                        onValueChange = { viewModel.onFatherNameChanged(it) },
                        label = { Text("نام پدر *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("father_name_input")
                    )

                    OutlinedTextField(
                        value = uiState.smsPhoneInput,
                        onValueChange = { viewModel.onSmsPhoneChanged(it) },
                        label = { Text("شماره SMS *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sms_phone_input")
                    )

                    // Grade Dropdown Menu
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = gradeExpanded,
                        onExpandedChange = { gradeExpanded = !gradeExpanded }
                    ) {
                        OutlinedTextField(
                            value = uiState.gradeInput,
                            onValueChange = { viewModel.onGradeChanged(it) },
                            label = { Text("صنف / پایه") },
                            readOnly = uiState.schoolClasses.isNotEmpty(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("grade_input")
                        )
                        if (uiState.schoolClasses.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = gradeExpanded,
                                onDismissRequest = { gradeExpanded = false }
                            ) {
                                uiState.schoolClasses.forEach { cls ->
                                    DropdownMenuItem(
                                        text = { Text(cls.name) },
                                        onClick = {
                                            viewModel.onGradeChanged(cls.name)
                                            gradeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.codeInput,
                        onValueChange = { viewModel.onCodeChanged(it) },
                        label = { Text("کد شاگرد (کد سیستم)") },
                        isError = uiState.isCodeDuplicate,
                        supportingText = {
                            if (uiState.isCodeDuplicate) {
                                Text("کد شاگرد تکراری است! لطفاً کد دیگری وارد کنید.", color = ErrorRed)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("student_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveStudent() },
                    enabled = !uiState.isCodeDuplicate,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_student_button")
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFormDialog() }) {
                    Text("لغو", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Delete Confirmation Dialog
    uiState.studentToDelete?.let { student ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("تأیید حذف", style = MaterialTheme.typography.titleLarge) },
            text = { Text("آیا از حذف شاگرد «${student.name}» مطمئن هستید؟ سوابق قبلی حفظ خواهند شد.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteStudentConfirmed() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("انصراف", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun StudentItemCard(
    student: Student,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_card_${student.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    if (student.studentCode.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${student.studentCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryBlue
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ولد ${student.fatherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = student.smsPhone, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    if (student.grade.isNotBlank()) {
                        Text(text = "• صنف: ${student.grade}", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue)
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = PrimaryBlue)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ErrorRed)
                }
            }
        }
    }
}
