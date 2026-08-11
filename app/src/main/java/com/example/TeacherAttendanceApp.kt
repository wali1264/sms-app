package com.example

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.auth.SupabaseAuthManager
import com.example.database.AppDatabase
import com.example.repository.AppRepository
import com.example.sms.AndroidSmsSender
import com.example.worker.AuthCheckWorker
import java.util.concurrent.TimeUnit

class TeacherAttendanceApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val authManager: SupabaseAuthManager by lazy {
        SupabaseAuthManager(this)
    }

    val repository: AppRepository by lazy {
        AppRepository(
            studentDao = database.studentDao(),
            attendanceDao = database.attendanceDao(),
            messageDao = database.messageDao(),
            settingsDao = database.settingsDao(),
            smsSender = AndroidSmsSender(this),
            context = this,
            authManager = authManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        scheduleHourlyAuthCheck()
    }

    private fun scheduleHourlyAuthCheck() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<AuthCheckWorker>(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "HourlyAuthCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        lateinit var instance: TeacherAttendanceApp
            private set
    }
}

