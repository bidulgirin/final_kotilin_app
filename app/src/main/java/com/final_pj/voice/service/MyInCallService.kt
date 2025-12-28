package com.final_pj.voice.service

import android.telecom.Call
import android.telecom.InCallService
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.telecom.TelecomManager
import android.util.Log
import java.io.File

/**
 * 시스템 통화 상태를 관리하는 InCallService
 * - 통화 상태 감지
 * - 통화 녹음 시작 / 종료
 * - Activity에 통화 종료 알림 전달
 */
class MyInCallService : InCallService() {

    companion object {
        /** 현재 실행 중인 InCallService 인스턴스 */
        var instance: MyInCallService? = null
            private set

        /** 현재 통화 Call 객체 */
        var currentCall: Call? = null
            private set

        /** 통화 종료 브로드캐스트 액션 */
        const val ACTION_CALL_ENDED = "com.final_pj.voice.CALL_ENDED"
    }

    /** 통화 녹음용 MediaRecorder */
    private var recorder: MediaRecorder? = null

    // =====================
    // Service 생명주기
    // =====================

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("MyInCallService", "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopRecordingSafely()
        Log.d("MyInCallService", "Service destroyed")
    }

    // =====================
    // 통화 상태 콜백
    // =====================

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        currentCall = call
        call.registerCallback(callCallback)

        Log.d("CALL", "Call added: ${call.details.handle}")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        call.unregisterCallback(callCallback)
        currentCall = null

        stopRecordingSafely()

        // 📢 Activity에 통화 종료 알림
        sendBroadcast(Intent(ACTION_CALL_ENDED))

        Log.d("CALL", "Call removed")
    }

    /**
     * Activity에서 호출하는 통화 종료 요청
     */
    fun endCall() {
        currentCall?.disconnect()
    }

    // =====================
    // Call 상태 변경 감지
    // =====================

    private val callCallback = object : Call.Callback() {

        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_ACTIVE -> {
                    // 📞 실제 통화 시작
                    Log.d("CALL", "Call ACTIVE")
                    startRecording()
                }

                Call.STATE_DISCONNECTED -> {
                    // ☎ 통화 종료
                    Log.d("CALL", "Call DISCONNECTED")
                    stopRecordingSafely()
                }
            }
        }
    }

    // =====================
    // 녹음 처리
    // =====================

    /**
     * 통화 녹음 시작
     * - 이미 녹음 중이면 무시
     */
    private fun startRecording() {
        if (recorder != null) return

        try {
            val outputFile = File(
                getExternalFilesDir(null),
                "call_${System.currentTimeMillis()}.m4a"
            )

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(outputFile.absolutePath)

                prepare()
                start()
            }

            Log.d("RECORD", "Recording started: ${outputFile.name}")

        } catch (e: Exception) {
            Log.e("RECORD", "Recording start failed", e)
            recorder = null
        }
    }

    /**
     * 통화 녹음 안전 종료
     * - 예외 상황에서도 크래시 방지
     */
    private fun stopRecordingSafely() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("RECORD", "Recording stop failed", e)
        } finally {
            recorder = null
        }
    }
}
