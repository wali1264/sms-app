package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.auth.SupabaseAuthManager

class AuthCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authManager = SupabaseAuthManager(applicationContext)
        authManager.checkStatusAndSync()
        return Result.success()
    }
}
