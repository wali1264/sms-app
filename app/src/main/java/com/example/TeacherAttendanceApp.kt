package com.example

import android.app.Application
import com.example.database.AppDatabase
import com.example.repository.AppRepository
import com.example.sms.AndroidSmsSender

class TeacherAttendanceApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val repository: AppRepository by lazy {
        AppRepository(
            studentDao = database.studentDao(),
            attendanceDao = database.attendanceDao(),
            messageDao = database.messageDao(),
            settingsDao = database.settingsDao(),
            smsSender = AndroidSmsSender(this),
            context = this
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TeacherAttendanceApp
            private set
    }
}
