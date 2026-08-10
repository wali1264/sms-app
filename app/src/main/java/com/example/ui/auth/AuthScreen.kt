package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthState
import com.example.auth.ManagerDetails
import com.example.auth.SupabaseAuthManager
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class UserRoleChoice {
    MANAGER, TEACHER
}

@Composable
fun AuthScreen(
    authManager: SupabaseAuthManager,
    onAuthSuccess: () -> Unit
) {
    val authState by authManager.authState.collectAsState()
    val scope = rememberCoroutineScope()

    var isLoginMode by remember { mutableStateOf(true) }
    var selectedRole by remember { mutableStateOf(UserRoleChoice.MANAGER) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var fullName by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    // Teacher specific
    var managerCodeInput by remember { mutableStateOf("") }
    var verifiedManager by remember { mutableStateOf<ManagerDetails?>(null) }
    var isCheckingManagerCode by remember { mutableStateOf(false) }
    var managerCodeError by remember { mutableStateOf<String?>(null) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val isOnline = authManager.isNetworkAvailable()

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            onAuthSuccess()
        }
    }

    Scaffold(
        containerColor = Color(0xFFA6E3E9)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLoginMode) "ورود به سیستم مدیریت مکتب" else "ثبت‌نام کاربر جدید",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isLoginMode) "لطفاً ایمیل و رمز عبور خود را وارد کنید"
                        else "نقش خود (مدیر یا معلم) و اطلاعات را وارد نمایید",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (authState is AuthState.PendingApproval) {
                        val pendingState = authState as AuthState.PendingApproval
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    tint = Color(0xFF856404),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "حساب کاربری: ${pendingState.email}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF856404)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pendingState.message,
                                    fontSize = 13.sp,
                                    color = Color(0xFF856404),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    authManager.checkStatusAndSync()
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_check_approval"),
                            enabled = !isLoading && isOnline
                        ) {
                            Text("بررسی مجدد وضعیت تأیید")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val res = authManager.logout()
                                    res.onFailure {
                                        errorMessage = it.localizedMessage ?: "خطا در خروج از حساب"
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_pending_logout"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("خروج از حساب", fontWeight = FontWeight.Bold)
                        }

                    } else {
                        // Regular Login / Signup Form
                        if (!isLoginMode) {
                            // Role selector
                            Text(
                                text = "انتخاب نقش کاربری:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedRole == UserRoleChoice.MANAGER,
                                    onClick = {
                                        selectedRole = UserRoleChoice.MANAGER
                                        errorMessage = null
                                    },
                                    label = { Text("مدیر مدرسه", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("chip_role_manager")
                                )

                                FilterChip(
                                    selected = selectedRole == UserRoleChoice.TEACHER,
                                    onClick = {
                                        selectedRole = UserRoleChoice.TEACHER
                                        errorMessage = null
                                    },
                                    label = { Text("معلم", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("chip_role_teacher")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // If Teacher selected: Manager Code input with instant check
                            if (selectedRole == UserRoleChoice.TEACHER) {
                                OutlinedTextField(
                                    value = managerCodeInput,
                                    onValueChange = { newValue ->
                                        managerCodeInput = newValue
                                        verifiedManager = null
                                        managerCodeError = null
                                        errorMessage = null

                                        if (newValue.trim().length >= 4) {
                                            scope.launch {
                                                isCheckingManagerCode = true
                                                val res = authManager.findManagerByCode(newValue.trim())
                                                res.onSuccess { manager ->
                                                    verifiedManager = manager
                                                    managerCodeError = null
                                                }.onFailure { err ->
                                                    verifiedManager = null
                                                    managerCodeError = err.localizedMessage ?: "کد مدیر نامعتبر است"
                                                }
                                                isCheckingManagerCode = false
                                            }
                                        }
                                    },
                                    label = { Text("کد اختصاصی مدیر مدرسه") },
                                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                                    trailingIcon = {
                                        if (isCheckingManagerCode) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("input_manager_code"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (verifiedManager != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "مدیر: ${verifiedManager!!.fullName}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF1B5E20)
                                                )
                                                if (verifiedManager!!.schoolName.isNotBlank()) {
                                                    Text(
                                                        text = "مدرسه/مکتب: ${verifiedManager!!.schoolName}",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (managerCodeError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = managerCodeError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Full Name
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it; errorMessage = null },
                                label = { Text("نام و نام خانوادگی") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().testTag("input_full_name"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Phone
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it; errorMessage = null },
                                label = { Text("شماره تلفن همراه") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().testTag("input_phone"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (selectedRole == UserRoleChoice.MANAGER) {
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = schoolName,
                                    onValueChange = { schoolName = it; errorMessage = null },
                                    label = { Text("نام مکتب / مدرسه") },
                                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth().testTag("input_school_name"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            label = { Text("ایمیل (Gmail)") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("input_email"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = { Text("رمز عبور") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("input_password"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isLoginMode) ImeAction.Done else ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (!isLoginMode) {
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorMessage = null },
                                label = { Text("تکرار رمز عبور") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("input_confirm_password"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (successMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "✔ ثبت‌نام با موفقیت انجام شد!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = successMessage!!,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1B5E20),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                successMessage = null

                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "لطفاً ایمیل و رمز عبور را وارد نمایید."
                                    return@Button
                                }

                                if (!isLoginMode) {
                                    if (fullName.isBlank()) {
                                        errorMessage = "لطفاً نام و نام خانوادگی را وارد کنید."
                                        return@Button
                                    }
                                    if (password != confirmPassword) {
                                        errorMessage = "رمز عبور و تکرار آن یکسان نیستند."
                                        return@Button
                                    }
                                    if (selectedRole == UserRoleChoice.TEACHER && verifiedManager == null) {
                                        errorMessage = "لطفاً یک کد مدیر معتبر وارد نمایید."
                                        return@Button
                                    }
                                }

                                scope.launch {
                                    isLoading = true
                                    if (isLoginMode) {
                                        val res = authManager.login(email.trim(), password.trim())
                                        res.onFailure {
                                            errorMessage = it.localizedMessage ?: "خطا در ورود"
                                        }
                                    } else {
                                        if (selectedRole == UserRoleChoice.MANAGER) {
                                            val res = authManager.signUpManager(
                                                email = email.trim(),
                                                password = password.trim(),
                                                fullName = fullName.trim(),
                                                schoolName = schoolName.trim(),
                                                phone = phone.trim()
                                            )
                                            res.onSuccess { msg ->
                                                successMessage = msg
                                                isLoginMode = true
                                                password = ""
                                                confirmPassword = ""
                                            }.onFailure {
                                                errorMessage = it.localizedMessage ?: "خطا در ثبت‌نام مدیر"
                                            }
                                        } else {
                                            val res = authManager.signUpTeacher(
                                                email = email.trim(),
                                                password = password.trim(),
                                                fullName = fullName.trim(),
                                                phone = phone.trim(),
                                                managerCode = managerCodeInput.trim()
                                            )
                                            res.onSuccess { msg ->
                                                successMessage = msg
                                                isLoginMode = true
                                                password = ""
                                                confirmPassword = ""
                                            }.onFailure {
                                                errorMessage = it.localizedMessage ?: "خطا در ثبت‌نام معلم"
                                            }
                                        }
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_auth_submit"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isLoginMode) "در حال ورود..." else "در حال ثبت‌نام...",
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Text(
                                    text = if (isLoginMode) "ورود به حساب" else "تکمیل ثبت‌نام",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = {
                                isLoginMode = !isLoginMode
                                errorMessage = null
                                successMessage = null
                            },
                            modifier = Modifier.testTag("btn_switch_auth_mode")
                        ) {
                            Text(
                                text = if (isLoginMode) "حساب کاربری ندارید؟ ثبت‌نام کنید"
                                else "قبلاً ثبت‌نام کرده‌اید؟ ورود به حساب",
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
