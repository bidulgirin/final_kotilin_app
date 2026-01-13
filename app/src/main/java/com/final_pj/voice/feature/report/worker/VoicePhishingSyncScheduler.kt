package com.final_pj.voice.feature.report.worker

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object VoicePhishingSyncScheduler {

    private const val UNIQUE_NAME = "voice_phishing_daily_sync"

    fun scheduleDaily(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val initialDelay = computeInitialDelayMillis(3, 10, "Asia/Seoul")

        val request = PeriodicWorkRequestBuilder<VoicePhishingSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    private fun computeInitialDelayMillis(hour: Int, minute: Int, tzId: String): Long {
        val tz = TimeZone.getTimeZone(tzId)
        val now = Calendar.getInstance(tz)
        val next = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis - now.timeInMillis
    }
}
