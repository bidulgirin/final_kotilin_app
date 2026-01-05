package com.final_pj.voice.feature.call.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.final_pj.voice.R

class CallDetectService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var telephonyCallback: TelephonyCallback
    private val executor by lazy { ContextCompat.getMainExecutor(this) }

    // 통화감지하면 알림
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showCallNotification() {
        val channelId = "call_state_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call State",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("감지중")
            .setContentText("보이스피싱감지기능을 이용하시려면 통화녹음을 해주세요")
            .build()

        NotificationManagerCompat.from(this).notify(100, notification)
    }
    // 알림 없애기
    private fun removeCallNotification() {
        NotificationManagerCompat.from(this).cancel(100)
    }
    // 통화상태 감지 
    private fun startCallDetection() {
        telephonyManager =
            getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        telephonyCallback = object : TelephonyCallback(),
            TelephonyCallback.CallStateListener {

            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onCallStateChanged(state: Int) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.d("test", "통화중")
                        // 통화 중
                        showCallNotification()
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        Log.d("test", "CALL_STATE_IDLE")
                        // 통화 종료
                        removeCallNotification()
                    }

                    TelephonyManager.CALL_STATE_RINGING -> {
                        // 전화 울림
                    }
                }
            }
        }

        telephonyManager.registerTelephonyCallback(
            executor,
            telephonyCallback
        )
    }
    // 포그라운드알림
    private fun startForegroundNotification() {
        val channelId = "call_detect"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "통화 감지 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("통화 감지 중")
            .setContentText("통화 상태를 감지하고 있습니다")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        // 포그라운드 호출
        startForeground(1, notification)
    }
    // 서비스 시작
    override fun onCreate() {
        super.onCreate()
        startForegroundNotification() // 포그라운드 서비스
        startCallDetection() // 통화 감지
    }
    // 서비스 끝
    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
    }
    // MainActivity 랑 바인딩해서 씀
    override fun onBind(intent: Intent?): IBinder? = null
}