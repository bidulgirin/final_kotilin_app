package com.final_pj.voice.feature.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.final_pj.voice.R

object NotificationHelper {
    private const val CHANNEL_ID = "voice_danger_v4"
    private const val CHANNEL_NAME = "보이스피싱 긴급 경고"
    private const val CHANNEL_DESC = "보이스피싱 위험 상황 시 알림을 보냅니다"

    const val ID_DEEPVOICE = 1001
    const val ID_KOBERT = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
        } catch (e: Exception) {
            Log.e("VIBRATE", "Vibration failed", e)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showAlert(
        context: Context,
        title: String,
        message: String,
        notificationId: Int,
        status: String = "Normal"
    ) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)
        triggerVibration(context)

        val accentBarColor: Int
        val titleTextColor: Int

        if (status == "WARNING") {
            accentBarColor = Color.parseColor("#FFA500")
            titleTextColor = Color.parseColor("#FF8C00")
        } else {
            accentBarColor = Color.parseColor("#FF5252")
            titleTextColor = Color.parseColor("#D32F2F")
        }

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_danger).apply {
            setTextViewText(R.id.noti_title, title)
            setTextViewText(R.id.noti_message, message)
            //setInt(R.id.noti_accent_bar, "setBackgroundColor", accentBarColor)
            setTextColor(R.id.noti_title, titleTextColor)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_feedback)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews) // 긴 메시지용 확장 뷰 설정
            .setCustomHeadsUpContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
