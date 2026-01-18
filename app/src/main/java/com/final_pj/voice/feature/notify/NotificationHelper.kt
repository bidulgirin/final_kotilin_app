package com.final_pj.voice.feature.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.final_pj.voice.R

object NotificationHelper {
    // 딥보이스 탐지 알람
    //private const val CHANNEL_ID = "vp_alert_channel" // 기존에 쓰던 알림
    private const val CHANNEL_ID = "deepvoice_alert_channel" // 딥보이스 알림
    private const val CHANNEL_NAME = "딥보이스 감지"
    private const val CHANNEL_DESC = "딥보이스를 감지 했습니다"

    // stt + kobert 탐지 알람

    private const val CHANNEL_ID_kobert = "text_danger_alert_channel"
    private const val CHANNEL_NAME_kobert = "의심 대화 탐지"
    private const val CHANNEL_DESC_kobert = "대화 내용에 위험한 내용이 포함되어잇습니다"


    private const val DEFAULT_NOTIFICATION_ID = 1001
    private const val DEFAULT_NOTIFICATION_ID2 = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        // Android 13(API 33)부터만 런타임 권한 필요
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // 딥보이스 감지 알림
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showAlert(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = DEFAULT_NOTIFICATION_ID
    ) {
        // 권한 없으면 조용히 리턴 (서비스에서 호출해도 안전)
        if (!hasNotificationPermission(context)) {
            // 필요하면 로그만 남기기
            Log.w("NOTI", "POST_NOTIFICATIONS permission not granted")
            return
        }

        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert) // 없으면 android.R.drawable.stat_sys_warning
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
    // 문장 탐지 알림
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showTextAlert(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = DEFAULT_NOTIFICATION_ID2
    ) {
        // 권한 없으면 조용히 리턴 (서비스에서 호출해도 안전)
        if (!hasNotificationPermission(context)) {
            // 필요하면 로그만 남기기
            Log.w("NOTI", "POST_NOTIFICATIONS permission not granted")
            return
        }

        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert) // 없으면 android.R.drawable.stat_sys_warning
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

}