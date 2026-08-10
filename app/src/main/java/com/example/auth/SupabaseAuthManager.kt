package com.example.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(
        val userId: String,
        val email: String,
        val role: String, // "MANAGER" or "TEACHER"
        val schoolCode: String,
        val fullName: String,
        val schoolName: String = ""
    ) : AuthState()
    data class PendingApproval(val userId: String, val email: String, val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

data class ManagerDetails(
    val managerId: String,
    val fullName: String,
    val schoolName: String,
    val schoolCode: String
)

data class TeacherProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String,
    val isApproved: Boolean
)

class SupabaseAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("supabase_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val SUPABASE_URL = "https://qdbcjjmldkinduqsjbkq.supabase.co"
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFkYmNqam1sZGtpbmR1cXNqYmtxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYzNTc3MjMsImV4cCI6MjEwMTkzMzcyM30.Vr5GFYT5CZtIEPnb0HzPO7JRWn0ve8TkSeaYfhYFG4s"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val currentDeviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreLocalSession()
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun restoreLocalSession() {
        val userId = prefs.getString("user_id", null)
        val email = prefs.getString("email", null)
        val isApproved = prefs.getBoolean("is_approved", false)
        val role = prefs.getString("role", "MANAGER") ?: "MANAGER"
        val schoolCode = prefs.getString("school_code", "") ?: ""
        val fullName = prefs.getString("full_name", "") ?: ""
        val schoolName = prefs.getString("school_name", "") ?: ""

        if (userId != null && email != null) {
            if (isApproved) {
                _authState.value = AuthState.LoggedIn(
                    userId = userId,
                    email = email,
                    role = role,
                    schoolCode = schoolCode,
                    fullName = fullName,
                    schoolName = schoolName
                )
            } else {
                _authState.value = AuthState.PendingApproval(
                    userId = userId,
                    email = email,
                    message = "حساب کاربری شما در انتظار تأیید مدیر مدرسه است."
                )
            }
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    private fun saveLocalSession(
        userId: String,
        email: String,
        accessToken: String,
        isApproved: Boolean,
        role: String,
        schoolCode: String,
        fullName: String,
        schoolName: String
    ) {
        prefs.edit()
            .putString("user_id", userId)
            .putString("email", email)
            .putString("access_token", accessToken)
            .putBoolean("is_approved", isApproved)
            .putString("role", role)
            .putString("school_code", schoolCode)
            .putString("full_name", fullName)
            .putString("school_name", schoolName)
            .putLong("last_check_time", System.currentTimeMillis())
            .apply()
    }

    fun clearLocalSession() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.LoggedOut
    }

    suspend fun findManagerByCode(code: String): Result<ManagerDetails> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("جهت بررسی کد مدیر، اتصال به اینترنت الزامی است."))
            }
            val cleanCode = code.trim()
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?role=eq.MANAGER&school_code=eq.$cleanCode&select=id,full_name,school_name,school_code")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotEmpty()) {
                val arr = JSONArray(responseString)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val manager = ManagerDetails(
                        managerId = obj.optString("id"),
                        fullName = obj.optString("full_name", "مدیر مدرسه"),
                        schoolName = obj.optString("school_name", ""),
                        schoolCode = obj.optString("school_code", cleanCode)
                    )
                    return@withContext Result.success(manager)
                }
            }
            return@withContext Result.failure(Exception("مدیری با این کد پیدا نشد."))
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطا در بررسی کد مدیر: ${e.localizedMessage}"))
        }
    }

    private fun generateNextSchoolCode(): String {
        return try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?role=eq.MANAGER&select=school_code&order=school_code.desc&limit=1")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            if (response.isSuccessful && responseString.isNotEmpty()) {
                val arr = JSONArray(responseString)
                if (arr.length() > 0) {
                    val lastCodeStr = arr.getJSONObject(0).optString("school_code", "999")
                    val lastCodeInt = lastCodeStr.toIntOrNull() ?: 999
                    return (lastCodeInt + 1).toString()
                }
            }
            "1000"
        } catch (e: Exception) {
            val randomOffset = (100..999).random()
            "1$randomOffset"
        }
    }

    suspend fun signUpManager(
        email: String,
        password: String,
        fullName: String,
        schoolName: String,
        phone: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("ارتباط با اینترنت برقرار نیست."))
            }

            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/signup")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseString).optString("msg", JSONObject(responseString).optString("error_description", "خطا در ثبت‌نام مدیر"))
                } catch (e: Exception) {
                    "خطا در ثبت‌نام: کد ${response.code}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseString)
            val userId = json.optJSONObject("user")?.optString("id") ?: json.optString("id")
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("شناسه کاربری دریافت نشد."))
            }

            val schoolCode = generateNextSchoolCode()

            // Manager is approved by default
            createOrUpdateProfile(
                userId = userId,
                email = email,
                role = "MANAGER",
                fullName = fullName,
                schoolName = schoolName,
                phone = phone,
                schoolCode = schoolCode,
                managerId = null,
                isApproved = true,
                deviceId = currentDeviceId
            )

            return@withContext Result.success("ثبت‌نام مدیر با موفقیت انجام شد. کد اختصاصی مدرسه شما: $schoolCode")
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطایی رخ داد: ${e.localizedMessage}"))
        }
    }

    suspend fun signUpTeacher(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        managerCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("ارتباط با اینترنت برقرار نیست."))
            }

            val managerRes = findManagerByCode(managerCode)
            if (managerRes.isFailure) {
                return@withContext Result.failure(Exception(managerRes.exceptionOrNull()?.message ?: "کد مدیر وارد شده معتبر نمی‌باشد."))
            }
            val managerDetails = managerRes.getOrThrow()

            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/signup")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseString).optString("msg", JSONObject(responseString).optString("error_description", "خطا در ثبت‌نام معلم"))
                } catch (e: Exception) {
                    "خطا در ثبت‌نام: کد ${response.code}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseString)
            val userId = json.optJSONObject("user")?.optString("id") ?: json.optString("id")
            if (userId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("شناسه کاربری دریافت نشد."))
            }

            // Teacher profile requires manager approval
            createOrUpdateProfile(
                userId = userId,
                email = email,
                role = "TEACHER",
                fullName = fullName,
                schoolName = managerDetails.schoolName,
                phone = phone,
                schoolCode = managerDetails.schoolCode,
                managerId = managerDetails.managerId,
                isApproved = false,
                deviceId = currentDeviceId
            )

            return@withContext Result.success("ثبت‌نام شما با موفقیت انجام شد و در انتظار تأیید مدیر مدرسه بمانید.")
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطایی رخ داد: ${e.localizedMessage}"))
        }
    }

    suspend fun login(email: String, password: String): Result<AuthState> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                val savedUserId = prefs.getString("user_id", null)
                val savedEmail = prefs.getString("email", null)
                val savedApproved = prefs.getBoolean("is_approved", false)
                val savedRole = prefs.getString("role", "MANAGER") ?: "MANAGER"
                val savedSchoolCode = prefs.getString("school_code", "") ?: ""
                val savedFullName = prefs.getString("full_name", "") ?: ""
                val savedSchoolName = prefs.getString("school_name", "") ?: ""

                if (savedUserId != null && savedEmail != null && savedEmail.equals(email, ignoreCase = true)) {
                    val state = if (savedApproved) {
                        AuthState.LoggedIn(
                            userId = savedUserId,
                            email = savedEmail,
                            role = savedRole,
                            schoolCode = savedSchoolCode,
                            fullName = savedFullName,
                            schoolName = savedSchoolName
                        )
                    } else {
                        AuthState.PendingApproval(savedUserId, savedEmail, "حساب کاربری شما در انتظار تأیید مدیر مدرسه است.")
                    }
                    _authState.value = state
                    return@withContext Result.success(state)
                } else {
                    return@withContext Result.failure(Exception("در حالت آفلاین، امکان ورود با حساب جدید وجود ندارد."))
                }
            }

            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseString).optString("error_description", "ایمیل یا رمز عبور اشتباه است.")
                } catch (e: Exception) {
                    "اطلاعات ورود اشتباه است."
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseString)
            val accessToken = json.optString("access_token")
            val userObj = json.optJSONObject("user")
            val userId = userObj?.optString("id") ?: ""

            if (userId.isEmpty()) {
                return@withContext Result.failure(Exception("پاسخ نامعتبر از سرور."))
            }

            // Fetch profile
            val profile = fetchProfile(userId, accessToken)
            val role = profile?.optString("role", "MANAGER") ?: "MANAGER"
            val isApproved = profile?.optBoolean("is_approved", role == "MANAGER") ?: (role == "MANAGER")
            val schoolCode = profile?.optString("school_code", "") ?: ""
            val fullName = profile?.optString("full_name", "") ?: ""
            val schoolName = profile?.optString("school_name", "") ?: ""
            val deviceIdInDb = profile?.optString("device_id", null)

            if (!deviceIdInDb.isNullOrEmpty() && deviceIdInDb != currentDeviceId && deviceIdInDb != "null") {
                return@withContext Result.failure(
                    Exception("شما قبلاً در دستگاه دیگری وارد شده‌اید. ابتدا از دستگاه قبلی خارج شوید.")
                )
            }

            updateDeviceId(userId, accessToken, currentDeviceId)

            saveLocalSession(
                userId = userId,
                email = email,
                accessToken = accessToken,
                isApproved = isApproved,
                role = role,
                schoolCode = schoolCode,
                fullName = fullName,
                schoolName = schoolName
            )

            val newState = if (isApproved) {
                AuthState.LoggedIn(
                    userId = userId,
                    email = email,
                    role = role,
                    schoolCode = schoolCode,
                    fullName = fullName,
                    schoolName = schoolName
                )
            } else {
                AuthState.PendingApproval(userId, email, "حساب کاربری شما در انتظار تأیید مدیر مدرسه است.")
            }

            _authState.value = newState
            return@withContext Result.success(newState)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطا در برقراری ارتباط: ${e.localizedMessage}"))
        }
    }

    suspend fun checkStatusAndSync(): AuthState = withContext(Dispatchers.IO) {
        val userId = prefs.getString("user_id", null) ?: return@withContext AuthState.LoggedOut
        val email = prefs.getString("email", null) ?: return@withContext AuthState.LoggedOut
        val accessToken = prefs.getString("access_token", "") ?: ""

        if (!isNetworkAvailable()) {
            return@withContext _authState.value
        }

        try {
            val profile = fetchProfile(userId, accessToken)
            if (profile != null) {
                val role = profile.optString("role", "MANAGER")
                val isApproved = profile.optBoolean("is_approved", role == "MANAGER")
                val schoolCode = profile.optString("school_code", "")
                val fullName = profile.optString("full_name", "")
                val schoolName = profile.optString("school_name", "")
                val deviceIdInDb = profile.optString("device_id", null)

                if (!deviceIdInDb.isNullOrEmpty() && deviceIdInDb != currentDeviceId && deviceIdInDb != "null") {
                    clearLocalSession()
                    val errState = AuthState.Error("حساب شما در دستگاه دیگری فعال شده است.")
                    _authState.value = errState
                    return@withContext errState
                }

                saveLocalSession(
                    userId = userId,
                    email = email,
                    accessToken = accessToken,
                    isApproved = isApproved,
                    role = role,
                    schoolCode = schoolCode,
                    fullName = fullName,
                    schoolName = schoolName
                )

                val newState = if (isApproved) {
                    AuthState.LoggedIn(
                        userId = userId,
                        email = email,
                        role = role,
                        schoolCode = schoolCode,
                        fullName = fullName,
                        schoolName = schoolName
                    )
                } else {
                    AuthState.PendingApproval(userId, email, "حساب کاربری شما در انتظار تأیید مدیر مدرسه است.")
                }
                _authState.value = newState
                return@withContext newState
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext _authState.value
    }

    suspend fun fetchTeachersForManager(schoolCode: String): Result<List<TeacherProfile>> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("اتصال اینترنت برقرار نیست."))
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?role=eq.TEACHER&school_code=eq.$schoolCode&select=id,email,full_name,phone,is_approved")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotEmpty()) {
                val arr = JSONArray(responseString)
                val teachers = mutableListOf<TeacherProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    teachers.add(
                        TeacherProfile(
                            id = obj.optString("id"),
                            email = obj.optString("email"),
                            fullName = obj.optString("full_name", "معلم"),
                            phone = obj.optString("phone", ""),
                            isApproved = obj.optBoolean("is_approved", false)
                        )
                    )
                }
                return@withContext Result.success(teachers)
            }
            return@withContext Result.success(emptyList())
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطا در دریافت لیست معلمان: ${e.localizedMessage}"))
        }
    }

    suspend fun updateTeacherApproval(teacherUserId: String, isApproved: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("اتصال اینترنت برقرار نیست."))
            }
            val bodyJson = JSONObject().apply {
                put("is_approved", isApproved)
                put("updated_at", "now()")
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$teacherUserId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .patch(bodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                return@withContext Result.success(Unit)
            } else {
                return@withContext Result.failure(Exception("خطا در تغییر وضعیت معلم."))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطا: ${e.localizedMessage}"))
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("خروج از حساب کاربری در حالت آفلاین امکان‌پذیر نیست."))
        }

        val userId = prefs.getString("user_id", null)
        val accessToken = prefs.getString("access_token", "") ?: ""

        if (userId != null) {
            try {
                updateDeviceId(userId, accessToken, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        clearLocalSession()
        return@withContext Result.success(Unit)
    }

    private fun fetchProfile(userId: String, accessToken: String): JSONObject? {
        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId&select=*")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${accessToken.ifEmpty { SUPABASE_ANON_KEY }}")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val responseString = response.body?.string() ?: ""

        if (response.isSuccessful && responseString.isNotEmpty()) {
            val jsonArray = JSONArray(responseString)
            if (jsonArray.length() > 0) {
                return jsonArray.getJSONObject(0)
            }
        }
        return null
    }

    private fun createOrUpdateProfile(
        userId: String,
        email: String,
        role: String,
        fullName: String,
        schoolName: String,
        phone: String,
        schoolCode: String,
        managerId: String?,
        isApproved: Boolean,
        deviceId: String?
    ) {
        val bodyJson = JSONObject().apply {
            put("id", userId)
            put("email", email)
            put("role", role)
            put("full_name", fullName)
            put("school_name", schoolName)
            put("phone", phone)
            put("school_code", schoolCode)
            if (managerId != null) put("manager_id", managerId) else put("manager_id", JSONObject.NULL)
            put("is_approved", isApproved)
            put("device_id", deviceId ?: JSONObject.NULL)
            put("updated_at", "now()")
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute()
    }

    private fun updateDeviceId(userId: String, accessToken: String, deviceId: String?) {
        val bodyJson = JSONObject().apply {
            if (deviceId == null) {
                put("device_id", JSONObject.NULL)
            } else {
                put("device_id", deviceId)
            }
            put("updated_at", "now()")
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${accessToken.ifEmpty { SUPABASE_ANON_KEY }}")
            .addHeader("Content-Type", "application/json")
            .patch(bodyJson.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute()
    }

    suspend fun syncStudentsToCloud(schoolCode: String, studentsJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable() || schoolCode.isBlank()) {
                return@withContext Result.success(Unit)
            }
            val bodyJson = JSONObject().apply {
                put("school_code", schoolCode)
                put("data_json", studentsJson)
                put("updated_at", "now()")
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/school_backups")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                return@withContext Result.success(Unit)
            } else {
                return@withContext Result.failure(Exception("خطا در همگام‌سازی با ابر: کد ${response.code}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchStudentsFromCloud(schoolCode: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable() || schoolCode.isBlank()) {
                return@withContext Result.success(null)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/school_backups?school_code=eq.$schoolCode&select=data_json")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotEmpty()) {
                val arr = JSONArray(responseString)
                if (arr.length() > 0) {
                    val dataJson = arr.getJSONObject(0).optString("data_json", null)
                    return@withContext Result.success(dataJson)
                }
            }
            return@withContext Result.success(null)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
