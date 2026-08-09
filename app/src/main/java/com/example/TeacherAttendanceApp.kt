package com.example

import android.app.Application
import com.example.database.AppDatabase
import com.example.repository.AppRepository
import com.example.sms.AndroidSmsSender

class TeacherAttendanceApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        val smsSender = AndroidSmsSender(this)
        repository = AppRepository(
            studentDao = database.studentDao(),
            attendanceDao = database.attendanceDao(),
            messageDao = database.messageDao(),
            settingsDao = database.settingsDao(),
            smsSender = smsSender,
            context = this
        )
    }

    companion object {
        lateinit var instance: TeacherAttendanceApp
            private set
    }
}
