package com.example.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.example.util.AppLogger
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

    fun getSavedRole(): String = prefs.getString("role", "MANAGER") ?: "MANAGER"

    fun getSavedSchoolCode(): String {
        val saved = prefs.getString("school_code", "") ?: ""
        if (saved.isNotBlank() && saved != "null") return saved
        val state = _authState.value
        if (state is AuthState.LoggedIn && state.schoolCode.isNotBlank() && state.schoolCode != "null") {
            return state.schoolCode
        }
        return ""
    }

    fun extractSchoolCodeFromProfile(profile: JSONObject?, defaultSchoolCode: String = ""): String {
        if (profile == null) return defaultSchoolCode
        val rawSchoolCode = profile.optString("school_code", "").trim()
        val rawManagerCode = profile.optString("manager_code", "").trim()
        return when {
            rawSchoolCode.isNotEmpty() && rawSchoolCode != "null" -> rawSchoolCode
            rawManagerCode.isNotEmpty() && rawManagerCode != "null" -> rawManagerCode
            else -> defaultSchoolCode
        }
    }

    fun getAccessToken(): String = prefs.getString("access_token", "") ?: ""
    fun isTeacher(): Boolean = getSavedRole() == "TEACHER"
    fun isManager(): Boolean = getSavedRole() == "MANAGER"

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
                val pendingMsg = if (role == "MANAGER") {
                    "حساب کاربری شما در انتظار تأیید سوپر ادمین است. لطفاً منتظر بمانید."
                } else {
                    "حساب کاربری شما در انتظار تأیید مدیر مدرسه است. لطفاً منتظر بمانید."
                }
                _authState.value = AuthState.PendingApproval(
                    userId = userId,
                    email = email,
                    message = pendingMsg
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
            // First attempt: Call RPC function get_next_school_code if defined
            val rpcRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/get_next_school_code")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            val rpcResponse = client.newCall(rpcRequest).execute()
            val rpcString = rpcResponse.body?.string()?.trim() ?: ""
            if (rpcResponse.isSuccessful && rpcString.isNotEmpty()) {
                val cleanCode = rpcString.replace("\"", "")
                if (cleanCode.toIntOrNull() != null) {
                    return cleanCode
                }
            }

            // Query all profiles to find maximum school_code
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/profiles?select=school_code")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""
            if (response.isSuccessful && responseString.isNotEmpty()) {
                val arr = JSONArray(responseString)
                var maxCode = 999
                for (i in 0 until arr.length()) {
                    val rawVal = arr.getJSONObject(i).opt("school_code")
                    val codeInt = when (rawVal) {
                        is Number -> rawVal.toInt()
                        is String -> rawVal.toIntOrNull()
                        else -> null
                    }
                    if (codeInt != null && codeInt > maxCode) {
                        maxCode = codeInt
                    }
                }
                return (maxCode + 1).toString()
            }
            "1000"
        } catch (e: Exception) {
            "1000"
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

            var accessToken = json.optString("access_token", "")
            if (accessToken.isEmpty()) {
                accessToken = json.optJSONObject("session")?.optString("access_token", "") ?: ""
            }

            // If token empty, do login to obtain JWT access token for RLS
            if (accessToken.isEmpty()) {
                try {
                    val loginBody = JSONObject().apply {
                        put("email", email)
                        put("password", password)
                    }.toString()
                    val tokenReq = Request.Builder()
                        .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(loginBody.toRequestBody(jsonMediaType))
                        .build()
                    val tokenResp = client.newCall(tokenReq).execute()
                    val tokenStr = tokenResp.body?.string() ?: ""
                    if (tokenResp.isSuccessful && tokenStr.isNotEmpty()) {
                        val tokenJson = JSONObject(tokenStr)
                        accessToken = tokenJson.optString("access_token", "")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val schoolCode = generateNextSchoolCode()

            // Manager profile requires super admin approval
            createOrUpdateProfile(
                userId = userId,
                email = email,
                role = "MANAGER",
                fullName = fullName,
                schoolName = schoolName,
                phone = phone,
                schoolCode = schoolCode,
                managerId = null,
                isApproved = false,
                deviceId = currentDeviceId,
                accessToken = accessToken
            )

            saveLocalSession(
                userId = userId,
                email = email,
                accessToken = accessToken,
                isApproved = false,
                role = "MANAGER",
                schoolCode = schoolCode,
                fullName = fullName,
                schoolName = schoolName
            )

            val pendingMsg = "حساب کاربری شما در انتظار تأیید توسط سوپر ادمین است. لطفاً منتظر بمانید."
            _authState.value = AuthState.PendingApproval(userId, email, pendingMsg)

            return@withContext Result.success("ثبت‌نام مدیر با موفقیت انجام شد. کد اختصاصی مدرسه شما: $schoolCode\nحساب کاربری شما در انتظار تأیید توسط سوپر ادمین است.")
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

            var accessToken = json.optString("access_token", "")
            if (accessToken.isEmpty()) {
                accessToken = json.optJSONObject("session")?.optString("access_token", "") ?: ""
            }

            if (accessToken.isEmpty()) {
                try {
                    val loginBody = JSONObject().apply {
                        put("email", email)
                        put("password", password)
                    }.toString()
                    val tokenReq = Request.Builder()
                        .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(loginBody.toRequestBody(jsonMediaType))
                        .build()
                    val tokenResp = client.newCall(tokenReq).execute()
                    val tokenStr = tokenResp.body?.string() ?: ""
                    if (tokenResp.isSuccessful && tokenStr.isNotEmpty()) {
                        val tokenJson = JSONObject(tokenStr)
                        accessToken = tokenJson.optString("access_token", "")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                deviceId = currentDeviceId,
                accessToken = accessToken
            )

            saveLocalSession(
                userId = userId,
                email = email,
                accessToken = accessToken,
                isApproved = false,
                role = "TEACHER",
                schoolCode = managerDetails.schoolCode,
                fullName = fullName,
                schoolName = managerDetails.schoolName
            )

            val pendingMsg = "حساب کاربری شما در انتظار تأیید مدیر مدرسه است. لطفاً منتظر بمانید."
            _authState.value = AuthState.PendingApproval(userId, email, pendingMsg)

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
            val rawApproved = profile?.opt("is_approved")
            val isApproved = when {
                rawApproved is Boolean -> rawApproved
                rawApproved is String -> rawApproved.lowercase() == "true" || rawApproved == "1"
                else -> false
            }
            val schoolCode = extractSchoolCodeFromProfile(profile, prefs.getString("school_code", "") ?: "")
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
                val pendingMsg = if (role == "MANAGER") {
                    "حساب کاربری شما در انتظار تأیید توسط سوپر ادمین است. لطفاً منتظر بمانید."
                } else {
                    "حساب کاربری شما در انتظار تأیید مدیر مدرسه است. لطفاً منتظر بمانید."
                }
                AuthState.PendingApproval(userId, email, pendingMsg)
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
            var profile = fetchProfile(userId, accessToken)
            if (profile == null) {
                profile = fetchProfile(userId, "")
            }
            if (profile == null) {
                // Profile row is missing in Supabase! Repair it in Supabase using local session info
                val savedRole = prefs.getString("role", "MANAGER") ?: "MANAGER"
                val savedFullName = prefs.getString("full_name", "") ?: ""
                val savedSchoolName = prefs.getString("school_name", "") ?: ""
                val savedSchoolCode = prefs.getString("school_code", "") ?: ""
                val finalCode = if (savedSchoolCode.isNotEmpty()) savedSchoolCode else generateNextSchoolCode()

                createOrUpdateProfile(
                    userId = userId,
                    email = email,
                    role = savedRole,
                    fullName = savedFullName,
                    schoolName = savedSchoolName,
                    phone = "",
                    schoolCode = finalCode,
                    managerId = null,
                    isApproved = false,
                    deviceId = currentDeviceId,
                    accessToken = accessToken
                )
                profile = fetchProfile(userId, accessToken) ?: fetchProfile(userId, "")
            }

            if (profile != null) {
                // If profile in Supabase was created as default TEACHER without school_code by database trigger,
                // but local session was registered as MANAGER, repair profile in Supabase
                val dbRole = profile.optString("role", "")
                val dbSchoolCode = profile.optString("school_code", "")
                val savedRole = prefs.getString("role", null)

                if (savedRole == "MANAGER" && (dbRole != "MANAGER" || dbSchoolCode.isEmpty() || dbSchoolCode == "null")) {
                    val savedSchoolCode = prefs.getString("school_code", "") ?: ""
                    val finalCode = if (savedSchoolCode.isNotEmpty()) savedSchoolCode else generateNextSchoolCode()
                    val savedFullName = prefs.getString("full_name", "") ?: profile.optString("full_name", "")
                    val savedSchoolName = prefs.getString("school_name", "") ?: profile.optString("school_name", "")
                    val currentIsApproved = when {
                        profile.opt("is_approved") is Boolean -> profile.getBoolean("is_approved")
                        profile.opt("is_approved") is String -> profile.optString("is_approved").lowercase() == "true" || profile.optString("is_approved") == "1"
                        else -> false
                    }

                    createOrUpdateProfile(
                        userId = userId,
                        email = email,
                        role = "MANAGER",
                        fullName = savedFullName,
                        schoolName = savedSchoolName,
                        phone = profile.optString("phone", ""),
                        schoolCode = finalCode,
                        managerId = null,
                        isApproved = currentIsApproved,
                        deviceId = currentDeviceId,
                        accessToken = accessToken
                    )
                    profile = fetchProfile(userId, accessToken) ?: fetchProfile(userId, "") ?: profile
                } else if (savedRole == "TEACHER" || dbRole == "TEACHER") {
                    val dbManagerCode = profile.optString("manager_code", "")
                    val effectiveTeacherCode = if (dbSchoolCode.isNotEmpty() && dbSchoolCode != "null") dbSchoolCode
                        else if (dbManagerCode.isNotEmpty() && dbManagerCode != "null") dbManagerCode
                        else prefs.getString("school_code", "") ?: ""

                    if (effectiveTeacherCode.isNotEmpty() && (dbSchoolCode.isEmpty() || dbSchoolCode == "null")) {
                        val savedFullName = prefs.getString("full_name", "") ?: profile.optString("full_name", "")
                        val savedSchoolName = prefs.getString("school_name", "") ?: profile.optString("school_name", "")
                        val currentIsApproved = when {
                            profile.opt("is_approved") is Boolean -> profile.getBoolean("is_approved")
                            profile.opt("is_approved") is String -> profile.optString("is_approved").lowercase() == "true" || profile.optString("is_approved") == "1"
                            else -> false
                        }

                        createOrUpdateProfile(
                            userId = userId,
                            email = email,
                            role = "TEACHER",
                            fullName = savedFullName,
                            schoolName = savedSchoolName,
                            phone = profile.optString("phone", ""),
                            schoolCode = effectiveTeacherCode,
                            managerId = if (profile.has("manager_id") && !profile.isNull("manager_id")) profile.optString("manager_id", null)?.takeIf { it != "null" } else null,
                            isApproved = currentIsApproved,
                            deviceId = currentDeviceId,
                            accessToken = accessToken
                        )
                        profile = fetchProfile(userId, accessToken) ?: fetchProfile(userId, "") ?: profile
                    }
                }

                val role = profile.optString("role", prefs.getString("role", "MANAGER") ?: "MANAGER")
                val rawApproved = profile.opt("is_approved")
                val isApproved = when {
                    rawApproved is Boolean -> rawApproved
                    rawApproved is String -> rawApproved.lowercase() == "true" || rawApproved == "1"
                    else -> false
                }
                val schoolCode = extractSchoolCodeFromProfile(profile, prefs.getString("school_code", "") ?: "")
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
                    val pendingMsg = if (role == "MANAGER") {
                        "حساب کاربری شما در انتظار تأیید توسط سوپر ادمین است. لطفاً منتظر بمانید."
                    } else {
                        "حساب کاربری شما در انتظار تأیید مدیر مدرسه است. لطفاً منتظر بمانید."
                    }
                    AuthState.PendingApproval(userId, email, pendingMsg)
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
                .url("$SUPABASE_URL/rest/v1/profiles?role=eq.TEACHER&or=(school_code.eq.$schoolCode,manager_code.eq.$schoolCode)&select=id,email,full_name,phone,is_approved")
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

            val accessToken = prefs.getString("access_token", "") ?: ""
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $SUPABASE_ANON_KEY"

            // 1. Try RPC function approve_teacher
            val rpcBody = JSONObject().apply {
                put("p_teacher_id", teacherUserId)
                put("p_approved", isApproved)
            }.toString()

            val rpcRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/approve_teacher")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(rpcBody.toRequestBody(jsonMediaType))
                .build()

            try {
                val rpcResponse = client.newCall(rpcRequest).execute()
                val rpcStr = rpcResponse.body?.string() ?: ""
                if (rpcResponse.isSuccessful && rpcStr.trim() == "true") {
                    return@withContext Result.success(Unit)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Direct PATCH fallback
            val bodyJson = JSONObject().apply {
                put("is_approved", isApproved)
                put("updated_at", "now()")
            }.toString()

            val headersToTry = mutableListOf<String>()
            if (accessToken.isNotEmpty()) {
                headersToTry.add("Bearer $accessToken")
            }
            headersToTry.add("Bearer $SUPABASE_ANON_KEY")

            for (hHeader in headersToTry) {
                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$teacherUserId")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", hHeader)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    if (response.code == 204 || (responseString.startsWith("[") && JSONArray(responseString).length() > 0)) {
                        return@withContext Result.success(Unit)
                    }
                }
            }

            return@withContext Result.failure(Exception("تغییرات انجام نشد. لطفاً کوئری SQL جدید را در Supabase اجرا نمایید."))
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("خطا: ${e.localizedMessage}"))
        }
    }

    suspend fun deleteTeacher(teacherUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                return@withContext Result.failure(Exception("اتصال اینترنت برقرار نیست."))
            }

            val accessToken = prefs.getString("access_token", "") ?: ""
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $SUPABASE_ANON_KEY"

            // 1. Try RPC function delete_teacher_by_manager
            val rpcBody = JSONObject().apply {
                put("p_teacher_id", teacherUserId)
            }.toString()

            val rpcRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rpc/delete_teacher_by_manager")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .post(rpcBody.toRequestBody(jsonMediaType))
                .build()

            try {
                val rpcResponse = client.newCall(rpcRequest).execute()
                val rpcStr = rpcResponse.body?.string() ?: ""
                if (rpcResponse.isSuccessful && rpcStr.trim() == "true") {
                    return@withContext Result.success(Unit)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Direct DELETE fallback
            val headersToTry = mutableListOf<String>()
            if (accessToken.isNotEmpty()) {
                headersToTry.add("Bearer $accessToken")
            }
            headersToTry.add("Bearer $SUPABASE_ANON_KEY")

            for (hHeader in headersToTry) {
                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$teacherUserId")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", hHeader)
                    .addHeader("Prefer", "return=representation")
                    .delete()
                    .build()

                val response = client.newCall(request).execute()
                val responseString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    if (response.code == 204 || (responseString.startsWith("[") && JSONArray(responseString).length() > 0)) {
                        return@withContext Result.success(Unit)
                    }
                }
            }

            return@withContext Result.failure(Exception("حذف انجام نشد. لطفاً کوئری SQL جدید را در Supabase اجرا نمایید."))
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
                // 1. Request clearance of device_id on Supabase
                updateDeviceId(userId, accessToken, null)

                // 2. Query Supabase to verify that device_id is actually cleared (NULL/empty)
                val verifyProfile = fetchProfile(userId, accessToken)
                val updatedDeviceId = verifyProfile?.optString("device_id", null)

                if (!updatedDeviceId.isNullOrEmpty() && updatedDeviceId != "null") {
                    return@withContext Result.failure(
                        Exception("پاکسازی شناسه آنلاین با خطا مواجه شد. جهت جلوگیری از قفل شدن حساب، خروج انجام نشد. لطفاً مجدداً تلاش کنید.")
                    )
                }
            } catch (e: Exception) {
                return@withContext Result.failure(
                    Exception("خطا در تأیید پاکسازی شناسه دستگاه: ${e.localizedMessage}. خروج لغو شد.")
                )
            }
        }

        clearLocalSession()
        return@withContext Result.success(Unit)
    }

    private fun fetchProfile(userId: String, accessToken: String): JSONObject? {
        val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $SUPABASE_ANON_KEY"
        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId&select=*")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", authHeader)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotEmpty()) {
                val jsonArray = JSONArray(responseString)
                if (jsonArray.length() > 0) {
                    return jsonArray.getJSONObject(0)
                }
            } else if (response.code == 500 && responseString.contains("infinite recursion")) {
                android.util.Log.w("SupabaseAuthManager", "Supabase RLS Policy Infinite Recursion detected on profiles table.")
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Retry with ANON KEY header if access token query returned empty or failed
        if (accessToken.isNotEmpty()) {
            try {
                val fallbackRequest = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId&select=*")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .get()
                    .build()

                val fallbackResponse = client.newCall(fallbackRequest).execute()
                val fallbackString = fallbackResponse.body?.string() ?: ""
                if (fallbackResponse.isSuccessful && fallbackString.isNotEmpty()) {
                    val jsonArray = JSONArray(fallbackString)
                    if (jsonArray.length() > 0) {
                        return jsonArray.getJSONObject(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        deviceId: String?,
        accessToken: String = ""
    ) {
        val bodyJson = JSONObject().apply {
            put("id", userId)
            put("email", email)
            put("role", role)
            put("full_name", fullName)
            put("school_name", schoolName)
            put("phone", phone)
            val codeInt = schoolCode.toIntOrNull()
            if (role == "MANAGER") {
                if (codeInt != null) put("school_code", codeInt) else put("school_code", JSONObject.NULL)
                put("manager_code", JSONObject.NULL)
            } else if (role == "TEACHER") {
                put("school_code", JSONObject.NULL)
                if (codeInt != null) put("manager_code", codeInt) else put("manager_code", JSONObject.NULL)
            } else {
                if (codeInt != null) put("school_code", codeInt) else put("school_code", JSONObject.NULL)
                if (codeInt != null) put("manager_code", codeInt) else put("manager_code", JSONObject.NULL)
            }
            val validManagerId = if (!managerId.isNullOrBlank() && managerId != "null") managerId else null
            if (validManagerId != null) put("manager_id", validManagerId) else put("manager_id", JSONObject.NULL)

            val validDeviceId = if (!deviceId.isNullOrBlank() && deviceId != "null") deviceId else null
            if (validDeviceId != null) put("device_id", validDeviceId) else put("device_id", JSONObject.NULL)
            put("updated_at", "now()")
        }.toString()

        val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $SUPABASE_ANON_KEY"

        // First attempt: PATCH request to update existing profile (e.g. inserted by handle_new_user trigger)
        val patchRequest = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .patch(bodyJson.toRequestBody(jsonMediaType))
            .build()

        var isRlsRecursionError = false

        try {
            val response = client.newCall(patchRequest).execute()
            val respStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonArr = try { JSONArray(respStr) } catch (e: Exception) { null }
                if (jsonArr != null && jsonArr.length() > 0) {
                    return
                }
            } else {
                if (response.code == 500 && respStr.contains("infinite recursion")) {
                    isRlsRecursionError = true
                    android.util.Log.e("SupabaseAuthManager", "PATCH profile failed due to Supabase RLS Policy Infinite Recursion (42P17).")
                } else {
                    android.util.Log.e("SupabaseAuthManager", "PATCH profile failed: code=${response.code}, body=$respStr")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isRlsRecursionError) {
            // Do not retry POST if the table's RLS policy itself is crashing PostgreSQL
            return
        }

        // Second attempt: POST request with on_conflict=id and merge-duplicates
        val postRequest = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/profiles?on_conflict=id")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = client.newCall(postRequest).execute()
            val respStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code == 500 && respStr.contains("infinite recursion")) {
                    android.util.Log.e("SupabaseAuthManager", "POST profile failed due to Supabase RLS Policy Infinite Recursion (42P17).")
                } else {
                    android.util.Log.e("SupabaseAuthManager", "POST profile failed: code=${response.code}, body=$respStr")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            val effectiveCode = if (schoolCode.isNotBlank() && schoolCode != "null") schoolCode else getSavedSchoolCode()
            if (!isNetworkAvailable() || effectiveCode.isBlank()) {
                AppLogger.d("SyncCloud", "همگام‌سازی انجام نشد: اینترنت متصل نیست یا کد مدرسه خالی است ($effectiveCode)")
                return@withContext Result.success(Unit)
            }
            val authToken = getAccessToken().ifEmpty { SUPABASE_ANON_KEY }
            val codeInt = effectiveCode.toLongOrNull()
            AppLogger.i("SyncCloud", "شروع همگام‌سازی ابری برای کد مدرسه $effectiveCode...")

            // 1. Sync full JSON backup to school_backups table
            val bodyJson = JSONObject().apply {
                if (codeInt != null) put("school_code", codeInt) else put("school_code", effectiveCode)
                put("data_json", studentsJson)
                put("updated_at", "now()")
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/school_backups?on_conflict=school_code")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                AppLogger.e("SyncCloud", "خطا در جدول school_backups: کد ${response.code} | متن: $errBody")
            } else {
                AppLogger.i("SyncCloud", "جدول school_backups با موفقیت بروزرسانی شد.")
            }

            // 2. Also sync individual rows to public.students table
            try {
                val root = JSONObject(studentsJson)
                if (root.has("students")) {
                    val studentsArr = root.getJSONArray("students")
                    val studentsPayload = JSONArray()
                    for (i in 0 until studentsArr.length()) {
                        val s = studentsArr.getJSONObject(i)
                        val studentObj = JSONObject().apply {
                            put("local_id", s.optLong("id", 0L))
                            if (codeInt != null) {
                                put("school_code", codeInt)
                            } else {
                                put("school_code", effectiveCode)
                            }
                            put("first_name", s.optString("name", ""))
                            put("last_name", "")
                            put("father_name", s.optString("fatherName", ""))
                            put("phone_number", s.optString("smsPhone", ""))
                            put("grade", s.optString("studentCode", ""))
                            put("status", if (s.optBoolean("isActive", true)) "ACTIVE" else "INACTIVE")
                        }
                        studentsPayload.put(studentObj)
                    }

                    if (studentsPayload.length() > 0) {
                        AppLogger.i("SyncCloud", "ارسال ${studentsPayload.length()} دانش‌آموز به جدول students...")
                        // Attempt 1: Upsert with on_conflict=local_id,school_code
                        val studentsReq = Request.Builder()
                            .url("$SUPABASE_URL/rest/v1/students?on_conflict=local_id,school_code")
                            .addHeader("apikey", SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer $authToken")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(studentsPayload.toString().toRequestBody(jsonMediaType))
                            .build()

                        val studResp = client.newCall(studentsReq).execute()
                        val studErrBody = studResp.body?.string() ?: ""
                        if (!studResp.isSuccessful) {
                            AppLogger.e("SyncCloud", "خطا در جدول students (روش ۱): کد ${studResp.code} | $studErrBody")
                            
                            // Attempt 2: Fallback to plain POST (without on_conflict query param)
                            val fallbackReq = Request.Builder()
                                .url("$SUPABASE_URL/rest/v1/students")
                                .addHeader("apikey", SUPABASE_ANON_KEY)
                                .addHeader("Authorization", "Bearer $authToken")
                                .addHeader("Content-Type", "application/json")
                                .post(studentsPayload.toString().toRequestBody(jsonMediaType))
                                .build()
                            val fallbackResp = client.newCall(fallbackReq).execute()
                            val fbErr = fallbackResp.body?.string() ?: ""
                            if (!fallbackResp.isSuccessful) {
                                AppLogger.e("SyncCloud", "خطا در جدول students (روش ۲): کد ${fallbackResp.code} | $fbErr")
                            } else {
                                AppLogger.i("SyncCloud", "جدول students با روش ۲ بروزرسانی شد.")
                            }
                        } else {
                            AppLogger.i("SyncCloud", "جدول students با موفقیت بروزرسانی شد.")
                        }
                    }
                }

                // 3. Also sync school_classes table
                if (root.has("school_classes")) {
                    val classesArr = root.getJSONArray("school_classes")
                    val classesPayload = JSONArray()
                    for (i in 0 until classesArr.length()) {
                        val c = classesArr.getJSONObject(i)
                        val classObj = JSONObject().apply {
                            put("local_id", c.optLong("id", 0L))
                            if (codeInt != null) {
                                put("school_code", codeInt)
                            } else {
                                put("school_code", effectiveCode)
                            }
                            put("name", c.optString("name", ""))
                            put("sort_order", c.optInt("sortOrder", 0))
                        }
                        classesPayload.put(classObj)
                    }

                    // First, delete existing classes for this school_code to guarantee clean mirror state
                    try {
                        val deleteQuery = if (codeInt != null) "$codeInt" else effectiveCode
                        val delReq = Request.Builder()
                            .url("$SUPABASE_URL/rest/v1/school_classes?school_code=eq.$deleteQuery")
                            .addHeader("apikey", SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer $authToken")
                            .delete()
                            .build()
                        client.newCall(delReq).execute()
                        AppLogger.i("SyncCloud", "صنف‌های قبلی مکتب $deleteQuery پاک‌سازی شدند.")
                    } catch (e: Exception) {
                        AppLogger.e("SyncCloud", "خطا در پاک‌سازی صنف‌های قبلی از Supabase", e)
                    }

                    if (classesPayload.length() > 0) {
                        AppLogger.i("SyncCloud", "ارسال ${classesPayload.length()} صنف به جدول school_classes...")
                        val classesReq = Request.Builder()
                            .url("$SUPABASE_URL/rest/v1/school_classes")
                            .addHeader("apikey", SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer $authToken")
                            .addHeader("Content-Type", "application/json")
                            .post(classesPayload.toString().toRequestBody(jsonMediaType))
                            .build()

                        val classResp = client.newCall(classesReq).execute()
                        val classErr = classResp.body?.string() ?: ""
                        if (!classResp.isSuccessful) {
                            AppLogger.e("SyncCloud", "خطا در ثبت صنف‌ها در Supabase: کد ${classResp.code} | $classErr")
                        } else {
                            AppLogger.i("SyncCloud", "جدول school_classes با موفقیت همگام شد.")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("SyncCloud", "خطا در پارس لیست دانش‌آموزان/صنف‌ها", e)
            }

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("SyncCloud", "استثنا در همگام‌سازی ابری", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchStudentsFromCloud(schoolCode: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val effectiveCode = if (schoolCode.isNotBlank() && schoolCode != "null") schoolCode else getSavedSchoolCode()
            if (!isNetworkAvailable() || effectiveCode.isBlank()) {
                return@withContext Result.success(null)
            }
            val authToken = getAccessToken().ifEmpty { SUPABASE_ANON_KEY }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/school_backups?school_code=eq.$effectiveCode&select=data_json")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $authToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotEmpty()) {
                val arr = JSONArray(responseString)
                if (arr.length() > 0) {
                    val dataJson = arr.getJSONObject(0).optString("data_json", null)
                    AppLogger.i("FetchCloud", "اطلاعات پشتیبان از ابر بازیابی شد.")
                    return@withContext Result.success(dataJson)
                }
            } else {
                AppLogger.e("FetchCloud", "خطا در دریافت پشتیبان از ابر: کد ${response.code} | $responseString")
            }
            return@withContext Result.success(null)
        } catch (e: Exception) {
            AppLogger.e("FetchCloud", "استثنا در دریافت پشتیبان ابری", e)
            return@withContext Result.failure(e)
        }
    }
}
