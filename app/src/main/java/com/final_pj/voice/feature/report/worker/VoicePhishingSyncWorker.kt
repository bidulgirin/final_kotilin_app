package com.final_pj.voice.feature.report.worker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.final_pj.voice.core.App

class VoicePhishingSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // App에서 이미 만들어 둔 repo 사용
            val app = applicationContext.applicationContext as App
            val repo = app.phishingNumber

            val count = repo.syncSnapshot().getOrThrow()
            Log.d("PHISH_SYNC", "Daily sync OK: $count items")

            Result.success()
        } catch (e: Exception) {
            Log.e("PHISH_SYNC", "Daily sync FAIL", e)
            Result.retry()
        }
    }
}
