package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.TeacherAttendanceApp

class SendMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = (applicationContext as TeacherAttendanceApp).repository
            repository.processPendingSmsMessages()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
