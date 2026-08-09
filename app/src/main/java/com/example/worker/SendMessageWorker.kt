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
            val app = applicationContext as? TeacherAttendanceApp ?: return Result.failure()
            app.repository.processPendingSmsMessages()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
